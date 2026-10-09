package com.maksimowiczm.foodyou.app.ui.food.pending

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import androidx.camera.core.ImageProxy
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Owned JPEG data: the camera buffer can be closed before decoding or writing to disk. */
internal data class CapturedPhoto(val jpeg: ByteArray, val rotationDegrees: Int)

internal data class CapturedPhotoPreview(val frame: Bitmap, val thumbnail: Bitmap)

internal fun ImageProxy.copyCapturedPhoto(): CapturedPhoto =
    try {
        require(format == ImageFormat.JPEG) { "Expected a JPEG capture" }
        val buffer = planes.single().buffer.duplicate()
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        require(bytes.isNotEmpty()) { "Empty JPEG capture" }
        CapturedPhoto(bytes, imageInfo.rotationDegrees)
    } finally {
        close()
    }

private val CapturedPhoto.exifOrientation: Int
    get() =
        when (rotationDegrees) {
            0 -> ExifInterface.ORIENTATION_NORMAL
            90 -> ExifInterface.ORIENTATION_ROTATE_90
            180 -> ExifInterface.ORIENTATION_ROTATE_180
            270 -> ExifInterface.ORIENTATION_ROTATE_270
            else -> error("Unsupported capture rotation: $rotationDegrees")
        }

internal fun CapturedPhoto.decodePreview(): CapturedPhotoPreview? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val maxSize = CapturePhotoPreviewState.EXPANDED_SIZE_PX
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSize) sample *= 2
    val decoded =
        BitmapFactory.decodeByteArray(
            jpeg, 0, jpeg.size, BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null
    val bounded = decoded.scaledTo(maxSize)
    if (bounded !== decoded) decoded.recycle()
    val frame = applyExifOrientation(bounded, exifOrientation)
    if (frame !== bounded) bounded.recycle()
    return CapturedPhotoPreview(frame, frame.scaledTo(CapturePhotoPreviewState.THUMBNAIL_SIZE_PX))
}

private fun Bitmap.scaledTo(maxSize: Int): Bitmap {
    val largestSide = maxOf(width, height)
    if (largestSide <= maxSize) return this
    val scale = maxSize.toFloat() / largestSide
    return Bitmap.createScaledBitmap(
        this,
        (width * scale).roundToInt().coerceAtLeast(1),
        (height * scale).roundToInt().coerceAtLeast(1),
        true,
    )
}

/** Preserve the encoded pixels; only normalize orientation metadata before publishing the file. */
internal fun CapturedPhoto.saveTo(file: File) {
    val temporary = File(file.parentFile, "${file.name}.partial")
    try {
        val directory = requireNotNull(file.parentFile)
        directory.mkdirs()
        check(directory.isDirectory) { "Could not create capture directory" }
        temporary.outputStream().use { it.write(jpeg) }
        ExifInterface(temporary).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, exifOrientation.toString())
            saveAttributes()
        }
        check(temporary.renameTo(file)) { "Could not publish captured photo" }
    } finally {
        temporary.delete()
    }
}

/** Processing survives the camera UI; preview decoding and saving do not wait for each other. */
internal class CapturedPhotoProcessor(
    private val scope: CoroutineScope,
    private val decode: suspend (CapturedPhoto) -> CapturedPhotoPreview? = {
        withContext(Dispatchers.Default) { it.decodePreview() }
    },
    private val save: suspend (CapturedPhoto, File) -> Unit = { photo, file ->
        withContext(Dispatchers.IO) { photo.saveTo(file) }
    },
) {
    fun process(
        photo: CapturedPhoto,
        file: File,
        onPreview: (CapturedPhotoPreview) -> Unit,
        onSaved: (String) -> Unit,
        onError: () -> Unit,
    ): Job =
        scope.launch(Dispatchers.Main.immediate) {
            launch {
                val preview = try {
                    decode(photo)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
                if (preview != null) onPreview(preview)
            }
            launch {
                val succeeded = try {
                    save(photo, file)
                    true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    false
                }
                if (succeeded) onSaved(file.name) else onError()
            }
        }
}

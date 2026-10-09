package com.maksimowiczm.foodyou.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Crops the already oriented input; the stored camera file and its EXIF metadata are untouched. */
internal fun cropScaleDisplay(photo: ScaleModelImage, box: ScaleDisplayBox): ScaleModelImage {
    val source = requireNotNull(BitmapFactory.decodeByteArray(photo.bytes, 0, photo.bytes.size))
    var cropped: Bitmap? = null
    var scaled: Bitmap? = null
    try {
        val left = box.left / 1000.0 * source.width
        val top = box.top / 1000.0 * source.height
        val right = box.right / 1000.0 * source.width
        val bottom = box.bottom / 1000.0 * source.height
        val padX = (right - left) * 0.35
        val padY = (bottom - top) * 0.35
        val x = (left - padX).toInt().coerceIn(0, source.width - 1)
        val y = (top - padY).toInt().coerceIn(0, source.height - 1)
        val endX = (right + padX + 0.5).toInt().coerceIn(x + 1, source.width)
        val endY = (bottom + padY + 0.5).toInt().coerceIn(y + 1, source.height)
        cropped = Bitmap.createBitmap(source, x, y, endX - x, endY - y)
        val scale = minOf(sqrt(600_000.0 / (cropped.width.toDouble() * cropped.height)),
            2048.0 / maxOf(cropped.width, cropped.height))
        scaled = Bitmap.createScaledBitmap(cropped,
            (cropped.width * scale).roundToInt().coerceIn(1, 2048),
            (cropped.height * scale).roundToInt().coerceIn(1, 2048), true)
        val bytes = ByteArrayOutputStream().use { output ->
            check(scaled.compress(Bitmap.CompressFormat.JPEG, 95, output))
            output.toByteArray()
        }
        return ScaleModelImage(bytes, "${scaled.width}x${scaled.height}")
    } finally {
        if (scaled != null && scaled !== cropped && scaled !== source) scaled.recycle()
        if (cropped != null && cropped !== source) cropped.recycle()
        source.recycle()
    }
}

internal fun decodeScalePhoto(directory: File, path: String): ByteArray {
    val file = File(directory, path).canonicalFile
    require(file.parentFile == directory.canonicalFile && file.isFile)
    return FileInputStream(file).use { decodeScalePhoto(it) }
}

internal fun decodeScalePhoto(stream: FileInputStream, onDecoded: (Int, Int) -> Unit = { _, _ -> }): ByteArray {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    stream.channel.position(0)
    BitmapFactory.decodeStream(stream, null, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
    stream.channel.position(0)
    val source = requireNotNull(BitmapFactory.decodeStream(stream, null,
        BitmapFactory.Options().apply { inSampleSize = sample }))
    var oriented: Bitmap? = null
    try {
        stream.channel.position(0)
        val exif = ExifInterface(stream)
        val transform = Matrix().apply {
            if (exif.isFlipped) postScale(-1f, 1f)
            postRotate(exif.rotationDegrees.toFloat())
        }
        oriented = Bitmap.createBitmap(source, 0, 0, source.width, source.height, transform, true)
        onDecoded(oriented.width, oriented.height)
        return ByteArrayOutputStream().use { output ->
            check(oriented.compress(Bitmap.CompressFormat.JPEG, 95, output))
            output.toByteArray()
        }
    } finally {
        if (oriented != null && oriented !== source) oriented.recycle()
        source.recycle()
    }
}

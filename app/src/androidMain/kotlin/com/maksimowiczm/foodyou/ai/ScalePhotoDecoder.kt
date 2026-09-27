package com.maksimowiczm.foodyou.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream

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

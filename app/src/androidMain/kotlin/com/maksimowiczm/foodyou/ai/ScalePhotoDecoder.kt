package com.maksimowiczm.foodyou.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File

internal fun decodeScalePhoto(directory: File, path: String): ByteArray {
    val file = File(directory, path).canonicalFile
    require(file.parentFile == directory.canonicalFile && file.isFile)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
    val source = requireNotNull(BitmapFactory.decodeFile(file.path,
        BitmapFactory.Options().apply { inSampleSize = sample }))
    var oriented: Bitmap? = null
    try {
        val exif = ExifInterface(file)
        val transform = Matrix().apply {
            if (exif.isFlipped) postScale(-1f, 1f)
            postRotate(exif.rotationDegrees.toFloat())
        }
        oriented = Bitmap.createBitmap(source, 0, 0, source.width, source.height, transform, true)
        return ByteArrayOutputStream().use { output ->
            check(oriented.compress(Bitmap.CompressFormat.JPEG, 95, output))
            output.toByteArray()
        }
    } finally {
        if (oriented != null && oriented !== source) oriented.recycle()
        source.recycle()
    }
}

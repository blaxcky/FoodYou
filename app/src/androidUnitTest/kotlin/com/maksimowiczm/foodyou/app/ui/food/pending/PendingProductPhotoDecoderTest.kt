package com.maksimowiczm.foodyou.app.ui.food.pending

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlin.test.assertEquals
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class PendingProductPhotoDecoderTest {
    private val files = mutableListOf<File>()

    @After
    fun tearDown() {
        files.forEach(File::delete)
    }

    @Test
    fun `decoder applies clockwise EXIF rotation`() {
        val bitmap = decodeJpegWithOrientation(ExifInterface.ORIENTATION_ROTATE_90)

        assertEquals(20, bitmap.width)
        assertEquals(40, bitmap.height)
        bitmap.recycle()
    }

    @Test
    fun `decoder applies counterclockwise EXIF rotation`() {
        val bitmap = decodeJpegWithOrientation(ExifInterface.ORIENTATION_ROTATE_270)

        assertEquals(20, bitmap.width)
        assertEquals(40, bitmap.height)
        bitmap.recycle()
    }

    @Test
    fun `horizontal EXIF mirror swaps image edges`() {
        val source = Bitmap.createBitmap(20, 10, Bitmap.Config.ARGB_8888)
        for (x in 0 until source.width) {
            for (y in 0 until source.height) {
                source.setPixel(x, y, if (x < source.width / 2) Color.RED else Color.BLUE)
            }
        }

        val mirrored = applyExifOrientation(source, ExifInterface.ORIENTATION_FLIP_HORIZONTAL)

        assertEquals(Color.BLUE, mirrored.getPixel(2, 5))
        assertEquals(Color.RED, mirrored.getPixel(17, 5))
        mirrored.recycle()
    }

    @Test
    fun `transposed EXIF mirror swaps dimensions`() {
        val source = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888)

        val transposed = applyExifOrientation(source, ExifInterface.ORIENTATION_TRANSPOSE)

        assertEquals(2, transposed.width)
        assertEquals(3, transposed.height)
        transposed.recycle()
    }

    private fun decodeJpegWithOrientation(orientation: Int): Bitmap {
        val file =
            File.createTempFile(
                    "quick-capture-orientation-",
                    ".jpg",
                    RuntimeEnvironment.getApplication().cacheDir,
                )
                .also(files::add)
        val source = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888)
        try {
            file.outputStream().use { stream ->
                source.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            }
        } finally {
            source.recycle()
        }
        ExifInterface(file).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            saveAttributes()
        }
        return requireNotNull(decodeSampledBitmap(file, maxSizePx = 100))
    }
}

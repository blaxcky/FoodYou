package com.maksimowiczm.foodyou.ai

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.ByteArrayOutputStream
import kotlin.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class ScalePhotoDecoderTest {
    @Test fun paddedDisplayAtImageEdgeIsClampedAndEnlargedWithoutChangingInput() {
        val source = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        val photo = source.asScaleImage()
        source.recycle()
        val before = photo.bytes.copyOf()
        val cropped = cropScaleDisplay(photo, ScaleDisplayBox(800,800,1000,1000))
        val output = BitmapFactory.decodeByteArray(cropped.bytes, 0, cropped.bytes.size)
        try {
            assertEquals(output.width, output.height)
            assertTrue(output.width > 256)
            assertTrue(output.width * output.height in 598_000..602_000)
            assertEquals("${output.width}x${output.height}", cropped.imageSize)
            assertTrue(Color.green(output.getPixel(output.width / 2, output.height / 2)) > 240)
            assertContentEquals(before, photo.bytes)
        } finally { output.recycle() }
    }

    @Test fun veryWideDisplayRespectsLongestSideLimitAndAspectRatio() {
        val source = Bitmap.createBitmap(1000, 100, Bitmap.Config.ARGB_8888)
        val photo = source.asScaleImage()
        source.recycle()
        val cropped = cropScaleDisplay(photo, ScaleDisplayBox(400,0,500,1000))
        val output = BitmapFactory.decodeByteArray(cropped.bytes, 0, cropped.bytes.size)
        try {
            assertEquals(2048, output.width)
            // The padded bounds round outwards to 1000 × 18 px before resizing.
            assertEquals(37, output.height)
        } finally { output.recycle() }
    }

    @Test fun displayCoordinatesUseExifOrientedPixelsExactlyOnce() {
        val dir = kotlin.io.path.createTempDirectory("scale-crop-exif").toFile()
        try {
            val file = File(dir, "rotated.jpg")
            val source = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
            for (y in 0 until source.height) for (x in 0 until source.width) {
                source.setPixel(x, y, if (x < 100) Color.RED else Color.BLUE)
            }
            file.outputStream().use { source.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            source.recycle()
            ExifInterface(file).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                saveAttributes()
            }
            val original = file.readBytes()
            val oriented = ScaleModelImage(decodeScalePhoto(dir, file.name), "100x200")
            val cropped = cropScaleDisplay(oriented, ScaleDisplayBox(700,200,1000,800))
            val output = BitmapFactory.decodeByteArray(cropped.bytes, 0, cropped.bytes.size)
            try {
                val center = output.getPixel(output.width / 2, output.height / 2)
                assertTrue(Color.blue(center) > 230)
                assertTrue(Color.red(center) < 25)
                assertContentEquals(original, file.readBytes())
            } finally { output.recycle() }
        } finally { dir.deleteRecursively() }
    }

    @Test fun mirroredExifPhotoIsCroppedInItsDisplayedOrientation() {
        val dir = kotlin.io.path.createTempDirectory("scale-crop-flip").toFile()
        try {
            val file = File(dir, "mirrored.jpg")
            val source = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
            for (y in 0 until source.height) for (x in 0 until source.width) {
                source.setPixel(x, y, if (x < 100) Color.RED else Color.BLUE)
            }
            file.outputStream().use { source.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            source.recycle()
            ExifInterface(file).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_FLIP_HORIZONTAL.toString())
                saveAttributes()
            }
            val original = file.readBytes()
            val oriented = ScaleModelImage(decodeScalePhoto(dir, file.name), "200x100")
            val cropped = cropScaleDisplay(oriented, ScaleDisplayBox(0,0,1000,300))
            val output = BitmapFactory.decodeByteArray(cropped.bytes, 0, cropped.bytes.size)
            try {
                val center = output.getPixel(output.width / 2, output.height / 2)
                assertTrue(Color.blue(center) > 230)
                assertTrue(Color.red(center) < 25)
                assertContentEquals(original, file.readBytes())
            } finally { output.recycle() }
        } finally { dir.deleteRecursively() }
    }

    private fun Bitmap.asScaleImage(): ScaleModelImage = ByteArrayOutputStream().use { output ->
        check(compress(Bitmap.CompressFormat.PNG, 100, output))
        ScaleModelImage(output.toByteArray(), "${width}x$height")
    }

    @Test fun appliesExifRotationAndLimitsResolutionWithoutChangingOriginal() {
        val dir = kotlin.io.path.createTempDirectory("scale-photo").toFile()
        try {
            val file = File(dir, "photo.jpg")
            val bitmap = Bitmap.createBitmap(3000, 1500, Bitmap.Config.ARGB_8888)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bitmap.recycle()
            ExifInterface(file).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                saveAttributes()
            }
            val before = file.readBytes()
            val bytes = decodeScalePhoto(dir, file.name)
            val output = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            assertTrue(output.height > output.width)
            assertTrue(maxOf(output.width, output.height) <= 2048)
            assertContentEquals(before, file.readBytes())
            output.recycle()
            assertFailsWith<IllegalArgumentException> { decodeScalePhoto(dir, "../outside.jpg") }
        } finally { dir.deleteRecursively() }
    }

    @Test fun partialDownloadsRemainInactiveAcrossStoreInstances() {
        val dir = kotlin.io.path.createTempDirectory("scale-model").toFile()
        try {
            File(dir, "${GemmaModel.E4B.fileName}.part").writeText("unfinished")
            assertFalse(GemmaModelStore(dir).state.value.ready)
            assertEquals(10L, GemmaModelStore(dir).state.value.bytes)
            GemmaModelStore(dir).delete()
            assertEquals(0L, GemmaModelStore(dir).state.value.bytes)
        } finally { dir.deleteRecursively() }
    }
}

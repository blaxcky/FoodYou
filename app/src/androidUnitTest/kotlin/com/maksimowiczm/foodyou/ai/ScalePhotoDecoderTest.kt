package com.maksimowiczm.foodyou.ai

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlin.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class ScalePhotoDecoderTest {
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
            File(dir, "$GEMMA_FILE.part").writeText("unfinished")
            assertFalse(GemmaModelStore(dir).state.value.ready)
            assertEquals(10L, GemmaModelStore(dir).state.value.bytes)
            GemmaModelStore(dir).delete()
            assertEquals(0L, GemmaModelStore(dir).state.value.bytes)
        } finally { dir.deleteRecursively() }
    }
}

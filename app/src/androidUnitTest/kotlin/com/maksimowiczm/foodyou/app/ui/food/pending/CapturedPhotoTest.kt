package com.maksimowiczm.foodyou.app.ui.food.pending

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageFormat
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class CapturedPhotoTest {
    private val directories = mutableListOf<File>()
    private val bitmaps = mutableSetOf<Bitmap>()

    @After
    fun tearDown() {
        directories.forEach(File::deleteRecursively)
        bitmaps.filterNot(Bitmap::isRecycled).forEach(Bitmap::recycle)
    }

    @Test
    fun copiesRemainingJpegBytesAndClosesCameraBuffer() {
        val buffer = ByteBuffer.wrap(byteArrayOf(9, 1, 2, 3, 8)).apply {
            position(1)
            limit(4)
        }
        var closes = 0
        val image = image(buffer, 90, onClose = { closes++ })
        val photo = image.copyCapturedPhoto()
        assertContentEquals(byteArrayOf(1, 2, 3), photo.jpeg)
        assertEquals(90, photo.rotationDegrees)
        assertEquals(1, closes)
        assertEquals(1, buffer.position())
        buffer.put(1, 7)
        assertContentEquals(byteArrayOf(1, 2, 3), photo.jpeg)
    }

    @Test
    fun cameraBufferIsClosedWhenCopyFails() {
        var closes = 0
        val image = image(ByteBuffer.allocate(0), 0, onClose = { closes++ })
        assertFailsWith<IllegalArgumentException> { image.copyCapturedPhoto() }
        assertEquals(1, closes)
        val unsupported = image(ByteBuffer.allocate(3), 0,
            format = ImageFormat.YUV_420_888, onClose = { closes++ })
        assertFailsWith<IllegalArgumentException> { unsupported.copyCapturedPhoto() }
        assertEquals(2, closes)
    }

    @Test
    fun savedJpegAndAnimatedPhotoMatchAtEveryRotationWithoutRecompressingPixels() {
        val jpeg = fixture()
        val expectedOrientation = listOf(
            ExifInterface.ORIENTATION_NORMAL, ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_ROTATE_180, ExifInterface.ORIENTATION_ROTATE_270,
        )
        val expectedTopLeft = listOf(Color.RED, Color.GREEN, Color.YELLOW, Color.BLUE)
        val original = assertNotNull(BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)).also(bitmaps::add)
        for ((index, rotation) in listOf(0, 90, 180, 270).withIndex()) {
            val photo = CapturedPhoto(jpeg, rotation)
            val preview = assertNotNull(photo.decodePreview()).track()
            val file = outputFile()
            photo.saveTo(file)
            val saved = assertNotNull(decodeSampledBitmap(file, 1600)).also(bitmaps::add)
            val encodedPixels = assertNotNull(BitmapFactory.decodeFile(file.path)).also(bitmaps::add)
            assertTrue(original.sameAs(encodedPixels), "JPEG pixels were recompressed at $rotation degrees")
            assertTrue(preview.frame.sameAs(saved), "Preview differs from saved photo at $rotation degrees")
            assertEquals(if (rotation % 180 == 0) 80 else 40, preview.frame.width)
            assertEquals(if (rotation % 180 == 0) 40 else 80, preview.frame.height)
            val actual = preview.frame.getPixel(5, 5)
            val expected = expectedTopLeft[index]
            assertTrue(kotlin.math.abs(Color.red(actual) - Color.red(expected)) < 20)
            assertTrue(kotlin.math.abs(Color.green(actual) - Color.green(expected)) < 20)
            assertTrue(kotlin.math.abs(Color.blue(actual) - Color.blue(expected)) < 20)
            assertEquals(expectedOrientation[index],
                ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, 0))
            assertFalse(File(file.parentFile, "${file.name}.partial").exists())
        }
    }

    @Test
    fun previewAndThumbnailHaveBoundedDimensions() {
        val preview = assertNotNull(CapturedPhoto(fixture(2000, 1000), 90).decodePreview()).track()
        assertEquals(800, preview.frame.width)
        assertEquals(1600, preview.frame.height)
        assertEquals(128, preview.thumbnail.width)
        assertEquals(256, preview.thumbnail.height)
    }

    @Test
    fun metadataWriteFailureRemovesPartialFileAndDoesNotPublishPhoto() {
        val file = outputFile()
        assertFailsWith<IllegalStateException> { CapturedPhoto(fixture(), 45).saveTo(file) }
        assertFalse(file.exists())
        assertTrue(file.parentFile!!.listFiles()!!.isEmpty())
    }

    @Test
    fun previewCanBeDisplayedWhileSavingIsStillPending() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val saving = CompletableDeferred<Unit>()
            val photo = CapturedPhoto(fixture(), 90)
            val preview = assertNotNull(photo.decodePreview()).track()
            var shown: CapturedPhotoPreview? = null
            val saved = mutableListOf<String>()
            val processor = CapturedPhotoProcessor(this,
                decode = { preview }, save = { capture, file -> saving.await(); capture.saveTo(file) })
            val file = outputFile()
            processor.process(photo, file, { shown = it }, saved::add, { error("Save failed") })
            runCurrent()
            assertEquals(preview, shown)
            assertTrue(saved.isEmpty())
            assertFalse(file.exists())
            saving.complete(Unit)
            runCurrent()
            assertEquals(listOf(file.name), saved)
            assertTrue(file.exists())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun savingDoesNotWaitForPreviewDecodeAndDecodeFailureIsNotSaveFailure() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val decoding = CompletableDeferred<Unit>()
            val saved = mutableListOf<String>()
            var errors = 0
            val processor = CapturedPhotoProcessor(this,
                decode = { decoding.await(); error("Preview decoder failed") },
                save = { photo, file -> photo.saveTo(file) })
            val file = outputFile()
            processor.process(CapturedPhoto(fixture(), 0), file,
                { error("Unexpected preview") }, saved::add, { errors++ })
            runCurrent()
            assertEquals(listOf(file.name), saved)
            assertTrue(file.exists())
            decoding.complete(Unit)
            runCurrent()
            assertEquals(0, errors)
            assertEquals(1, saved.size)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun acceptedPhotoIsSavedOnceAfterCameraUiIsClosed() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val uiScope = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
        try {
            val saving = CompletableDeferred<Unit>()
            val state = CapturePhotoPreviewState<Bitmap>(uiScope) { _, _ -> null }
            val id = assertNotNull(state.beginCapture())
            val photo = CapturedPhoto(fixture(), 0)
            val preview = assertNotNull(photo.decodePreview()).track()
            val processor = CapturedPhotoProcessor(this,
                decode = { preview }, save = { capture, file -> saving.await(); capture.saveTo(file) })
            val saved = mutableListOf<String>()
            val file = outputFile()
            processor.process(photo, file,
                { state.photoCaptured(id, it.frame, it.thumbnail) },
                { state.photoSaved(id, it); saved.add(it) }, { state.photoFailed(id) })
            runCurrent()
            state.close()
            uiScope.cancel()
            saving.complete(Unit)
            runCurrent()
            assertEquals(listOf(file.name), saved)
            assertTrue(file.exists())
            assertNull(state.flight)
            assertNull(state.thumbnail)
        } finally {
            uiScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun failedSaveRejectsLaterDecodedPhotoAndReportsFailureOnce() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val decoding = CompletableDeferred<Unit>()
            val state = CapturePhotoPreviewState<Bitmap>(backgroundScope) { _, _ -> null }
            val id = assertNotNull(state.beginCapture())
            val photo = CapturedPhoto(fixture(), 0)
            val preview = assertNotNull(photo.decodePreview()).track()
            val processor = CapturedPhotoProcessor(this,
                decode = { decoding.await(); preview }, save = { _, _ -> error("Disk full") })
            var errors = 0
            processor.process(photo, outputFile(),
                { state.photoCaptured(id, it.frame, it.thumbnail) },
                { error("Unexpected success") }, { errors++; state.photoFailed(id) })
            runCurrent()
            assertEquals(1, errors)
            decoding.complete(Unit)
            runCurrent()
            assertNull(state.flight)
            assertNull(state.thumbnail)
            assertEquals(1, errors)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun outputFile(): File {
        val directory = File(RuntimeEnvironment.getApplication().cacheDir,
            "captured-photo-${java.util.UUID.randomUUID()}").also { it.mkdirs(); directories.add(it) }
        return File(directory, "photo.jpg")
    }

    private fun fixture(width: Int = 80, height: Int = 40): ByteArray {
        val source = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            for (y in 0 until height) for (x in 0 until width) {
                source.setPixel(x, y, when {
                    x < width / 2 && y < height / 2 -> Color.RED
                    y < height / 2 -> Color.BLUE
                    x < width / 2 -> Color.GREEN
                    else -> Color.YELLOW
                })
            }
            return ByteArrayOutputStream().use {
                check(source.compress(Bitmap.CompressFormat.JPEG, 95, it))
                it.toByteArray()
            }
        } finally {
            source.recycle()
        }
    }

    private fun CapturedPhotoPreview.track(): CapturedPhotoPreview = also {
        bitmaps.add(frame)
        bitmaps.add(thumbnail)
    }

    private fun image(buffer: ByteBuffer, rotation: Int, format: Int = ImageFormat.JPEG,
        onClose: () -> Unit): ImageProxy {
        val plane = proxy<ImageProxy.PlaneProxy> { name ->
            when (name) { "getBuffer" -> buffer; else -> error(name) }
        }
        val info = proxy<ImageInfo> { name ->
            when (name) { "getRotationDegrees" -> rotation; else -> error(name) }
        }
        return proxy { name ->
            when (name) {
                "getFormat" -> format
                "getPlanes" -> arrayOf(plane)
                "getImageInfo" -> info
                "close" -> { onClose(); null }
                else -> error(name)
            }
        }
    }

    private inline fun <reified T> proxy(crossinline answer: (String) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
            answer(method.name)
        } as T
}

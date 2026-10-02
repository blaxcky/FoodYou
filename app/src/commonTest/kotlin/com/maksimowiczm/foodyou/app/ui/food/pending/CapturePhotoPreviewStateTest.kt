package com.maksimowiczm.foodyou.app.ui.food.pending

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

class CapturePhotoPreviewStateTest {
    @Test
    fun durationStartsWhenDecodedPhotoIsAvailable() = runTest {
        val decoded = CompletableDeferred<String?>()
        val preview = CapturePhotoPreviewState(backgroundScope) { _: String -> decoded.await() }
        preview.photoSaved("first.jpg")
        runCurrent()
        advanceTimeBy(2_000)
        assertFalse(preview.visible)
        assertNull(preview.bitmap)

        decoded.complete("first bitmap")
        runCurrent()
        assertTrue(preview.visible)
        assertEquals("first bitmap", preview.bitmap)
        advanceTimeBy(1_199)
        runCurrent()
        assertTrue(preview.visible)
        advanceTimeBy(1)
        runCurrent()
        assertFalse(preview.visible)
        assertEquals("first bitmap", preview.bitmap)
        advanceTimeBy(180)
        runCurrent()
        assertNull(preview.bitmap)
    }

    @Test
    fun nextCaptureDismissesImmediatelyAndOldTimerCannotHideNewPhoto() = runTest {
        val preview = CapturePhotoPreviewState(backgroundScope) { path: String -> path }
        preview.photoSaved("first.jpg")
        runCurrent()
        advanceTimeBy(700)
        preview.captureStarted()
        assertFalse(preview.visible)
        assertNull(preview.bitmap)
        preview.photoSaved("second.jpg")
        runCurrent()
        advanceTimeBy(600)
        runCurrent()
        assertTrue(preview.visible)
        assertEquals("second.jpg", preview.bitmap)
    }

    @Test
    fun lateNonCancellableDecodeCannotReshowOldPhoto() = runTest {
        val oldImage = CompletableDeferred<String?>()
        val preview = CapturePhotoPreviewState(backgroundScope) { path: String ->
            if (path == "first.jpg") withContext(NonCancellable) { oldImage.await() } else path
        }
        preview.photoSaved("first.jpg")
        runCurrent()
        preview.captureStarted()
        preview.photoSaved("second.jpg")
        runCurrent()
        oldImage.complete("old bitmap")
        runCurrent()
        assertTrue(preview.visible)
        assertEquals("second.jpg", preview.bitmap)
    }

    @Test
    fun dismissalInvalidatesPendingDecode() = runTest {
        val decoded = CompletableDeferred<String?>()
        val preview = CapturePhotoPreviewState(backgroundScope) { _: String ->
            withContext(NonCancellable) { decoded.await() }
        }
        preview.photoSaved("first.jpg")
        runCurrent()
        preview.dismiss()
        decoded.complete("bitmap")
        runCurrent()
        assertFalse(preview.visible)
        assertNull(preview.bitmap)
    }

    @Test
    fun failedDecodeDoesNotShowPreview() = runTest {
        val preview = CapturePhotoPreviewState<String>(backgroundScope) { error("Unreadable photo") }
        preview.photoSaved("broken.jpg")
        runCurrent()
        assertFalse(preview.visible)
        assertNull(preview.bitmap)
    }

    @Test
    fun missingBitmapDoesNotShowPreview() = runTest {
        val preview = CapturePhotoPreviewState<String>(backgroundScope) { null }
        preview.photoSaved("missing.jpg")
        runCurrent()
        assertFalse(preview.visible)
        assertNull(preview.bitmap)
    }

    @Test
    fun closeDiscardsDecodeAndIgnoresLateCameraCallbacks() = runTest {
        val decoded = CompletableDeferred<String?>()
        val preview = CapturePhotoPreviewState(backgroundScope) { _: String ->
            withContext(NonCancellable) { decoded.await() }
        }
        preview.photoSaved("first.jpg")
        runCurrent()
        preview.close()
        decoded.complete("bitmap")
        preview.photoSaved("late.jpg")
        runCurrent()
        assertFalse(preview.visible)
        assertNull(preview.bitmap)
    }
}

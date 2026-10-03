package com.maksimowiczm.foodyou.app.ui.food.pending

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

class CapturePhotoPreviewStateTest {
    private fun decoded(path: String, maxSizePx: Int) = "$path@$maxSizePx"

    @Test
    fun frozenFrameFliesAndStandsInUntilSavedPhotoIsDecoded() = runTest {
        val thumbnail = CompletableDeferred<String?>()
        val preview = CapturePhotoPreviewState(backgroundScope) { _: String, _: Int ->
            thumbnail.await()
        }
        preview.captureStarted("frame")
        val flight = assertNotNull(preview.flight)
        assertEquals("frame", flight.frame)
        assertEquals("frame", preview.thumbnail)

        preview.photoSaved("first.jpg")
        runCurrent()
        assertEquals("frame", preview.thumbnail)
        thumbnail.complete("first thumbnail")
        runCurrent()
        assertEquals("first thumbnail", preview.thumbnail)

        preview.flightFinished(flight.id)
        assertNull(preview.flight)
    }

    @Test
    fun captureWithoutFrameShowsDecodedThumbnailWithoutFlight() = runTest {
        val preview = CapturePhotoPreviewState(backgroundScope, ::decoded)
        preview.captureStarted(null)
        assertNull(preview.flight)
        assertNull(preview.thumbnail)
        preview.photoSaved("first.jpg")
        runCurrent()
        assertEquals("first.jpg@${CapturePhotoPreviewState.THUMBNAIL_SIZE_PX}", preview.thumbnail)
    }

    @Test
    fun staleFlightCannotClearNewerFlight() = runTest {
        val preview = CapturePhotoPreviewState(backgroundScope, ::decoded)
        preview.captureStarted("first frame")
        val first = assertNotNull(preview.flight)
        preview.photoSaved("first.jpg")
        preview.captureStarted("second frame")
        val second = assertNotNull(preview.flight)
        preview.flightFinished(first.id)
        assertEquals(second, preview.flight)
    }

    @Test
    fun failedSaveRestoresPreviousThumbnail() = runTest {
        val preview = CapturePhotoPreviewState(backgroundScope, ::decoded)
        preview.captureStarted("first frame")
        preview.photoSaved("first.jpg")
        runCurrent()
        val first = preview.thumbnail

        preview.captureStarted("second frame")
        assertEquals("second frame", preview.thumbnail)
        preview.photoFailed()
        assertNull(preview.flight)
        assertEquals(first, preview.thumbnail)

        preview.expand()
        runCurrent()
        assertEquals("first.jpg@${CapturePhotoPreviewState.EXPANDED_SIZE_PX}", preview.expandedPhoto)
    }

    @Test
    fun lateDecodeOfOlderPhotoCannotReplaceNewerThumbnail() = runTest {
        val oldImage = CompletableDeferred<String?>()
        val preview = CapturePhotoPreviewState(backgroundScope) { path: String, _: Int ->
            if (path == "first.jpg") withContext(NonCancellable) { oldImage.await() } else path
        }
        preview.captureStarted("first frame")
        preview.photoSaved("first.jpg")
        runCurrent()
        preview.captureStarted("second frame")
        preview.photoSaved("second.jpg")
        runCurrent()
        oldImage.complete("old bitmap")
        runCurrent()
        assertEquals("second.jpg", preview.thumbnail)
    }

    @Test
    fun failedThumbnailDecodeKeepsFrozenFrame() = runTest {
        val preview = CapturePhotoPreviewState<String>(backgroundScope) { _, _ ->
            error("Unreadable photo")
        }
        preview.captureStarted("frame")
        preview.photoSaved("broken.jpg")
        runCurrent()
        assertEquals("frame", preview.thumbnail)
    }

    @Test
    fun expandDecodesSavedPhotoLazilyAndCollapseReleasesItAfterExit() = runTest {
        var loads = 0
        val preview = CapturePhotoPreviewState(backgroundScope) { path: String, size: Int ->
            loads++
            decoded(path, size)
        }
        preview.captureStarted(null)
        preview.photoSaved("first.jpg")
        runCurrent()
        assertEquals(1, loads)
        assertFalse(preview.expanded)

        preview.expand()
        runCurrent()
        assertEquals(2, loads)
        assertTrue(preview.expanded)
        assertEquals("first.jpg@${CapturePhotoPreviewState.EXPANDED_SIZE_PX}", preview.expandedPhoto)

        preview.collapse()
        assertFalse(preview.expanded)
        assertNotNull(preview.expandedPhoto)
        advanceTimeBy(CapturePhotoPreviewState.EXIT_ANIMATION_MILLIS)
        runCurrent()
        assertNull(preview.expandedPhoto)
    }

    @Test
    fun expandIsIgnoredWhileCaptureIsSaving() = runTest {
        val preview = CapturePhotoPreviewState(backgroundScope, ::decoded)
        preview.captureStarted("first frame")
        preview.photoSaved("first.jpg")
        runCurrent()
        preview.captureStarted("second frame")
        preview.expand()
        runCurrent()
        assertFalse(preview.expanded)
        assertNull(preview.expandedPhoto)
    }

    @Test
    fun nextCaptureCollapsesExpandedPhoto() = runTest {
        val preview = CapturePhotoPreviewState(backgroundScope, ::decoded)
        preview.captureStarted(null)
        preview.photoSaved("first.jpg")
        runCurrent()
        preview.expand()
        runCurrent()
        assertTrue(preview.expanded)
        preview.captureStarted("frame")
        assertFalse(preview.expanded)
    }

    @Test
    fun collapseInvalidatesPendingExpandDecode() = runTest {
        val expandedImage = CompletableDeferred<String?>()
        val preview = CapturePhotoPreviewState(backgroundScope) { path: String, size: Int ->
            if (size == CapturePhotoPreviewState.EXPANDED_SIZE_PX) {
                withContext(NonCancellable) { expandedImage.await() }
            } else {
                path
            }
        }
        preview.captureStarted(null)
        preview.photoSaved("first.jpg")
        runCurrent()
        preview.expand()
        runCurrent()
        preview.collapse()
        expandedImage.complete("bitmap")
        runCurrent()
        assertFalse(preview.expanded)
        assertNull(preview.expandedPhoto)
    }

    @Test
    fun closeDiscardsDecodeAndIgnoresLateCameraCallbacks() = runTest {
        val thumbnail = CompletableDeferred<String?>()
        val preview = CapturePhotoPreviewState(backgroundScope) { _: String, _: Int ->
            withContext(NonCancellable) { thumbnail.await() }
        }
        preview.captureStarted("frame")
        preview.photoSaved("first.jpg")
        runCurrent()
        preview.close()
        thumbnail.complete("bitmap")
        preview.captureStarted("late frame")
        preview.photoSaved("late.jpg")
        runCurrent()
        preview.expand()
        runCurrent()
        assertNull(preview.flight)
        assertNull(preview.thumbnail)
        assertFalse(preview.expanded)
        assertNull(preview.expandedPhoto)
    }
}

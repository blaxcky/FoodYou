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
    fun animationStartsFromCapturedPhotoBeforeSavingCompletes() = runTest {
        val preview = CapturePhotoPreviewState(backgroundScope, ::decoded)
        val id = assertNotNull(preview.beginCapture())
        assertNull(preview.flight)
        preview.photoCaptured(id, "actual photo", "actual thumbnail")
        assertEquals("actual photo", assertNotNull(preview.flight).frame)
        assertEquals("actual thumbnail", preview.thumbnail)
        preview.expand()
        runCurrent()
        assertFalse(preview.expanded)

        preview.photoSaved(id, "first.jpg")
        preview.expand()
        runCurrent()
        assertEquals("first.jpg@1600", preview.expandedPhoto)
        preview.flightFinished(id)
        assertNull(preview.flight)
        assertEquals("actual thumbnail", preview.thumbnail)
    }

    @Test
    fun savingCanFinishBeforeCapturedImageIsDecoded() = runTest {
        val preview = CapturePhotoPreviewState(backgroundScope, ::decoded)
        val id = assertNotNull(preview.beginCapture())
        preview.photoSaved(id, "first.jpg")
        assertNull(preview.thumbnail)
        assertNull(preview.flight)
        preview.photoCaptured(id, "actual photo", "thumbnail")
        assertEquals("actual photo", assertNotNull(preview.flight).frame)
        preview.expand()
        runCurrent()
        assertEquals("first.jpg@1600", preview.expandedPhoto)
    }

    @Test
    fun olderCaptureCallbacksCannotReplaceNewerPhotoOrClearItsFlight() = runTest {
        val preview = CapturePhotoPreviewState(backgroundScope, ::decoded)
        val first = assertNotNull(preview.beginCapture())
        preview.photoSaved(first, "first.jpg")
        val second = assertNotNull(preview.beginCapture())
        preview.photoCaptured(second, "second photo", "second thumbnail")
        preview.photoCaptured(first, "late first photo", "late thumbnail")
        preview.photoSaved(first, "first.jpg")
        preview.photoFailed(first)
        preview.flightFinished(first)
        assertEquals(second, assertNotNull(preview.flight).id)
        assertEquals("second thumbnail", preview.thumbnail)
        preview.photoSaved(second, "second.jpg")
        preview.expand()
        runCurrent()
        assertEquals("second.jpg@1600", preview.expandedPhoto)
    }

    @Test
    fun saveFailureRestoresPreviousThumbnailAndRejectsLateDecode() = runTest {
        val preview = CapturePhotoPreviewState(backgroundScope, ::decoded)
        val first = assertNotNull(preview.beginCapture())
        preview.photoCaptured(first, "first photo", "first thumbnail")
        preview.photoSaved(first, "first.jpg")
        val second = assertNotNull(preview.beginCapture())
        preview.photoCaptured(second, "second photo", "second thumbnail")
        preview.photoFailed(second)
        preview.photoCaptured(second, "late photo", "late thumbnail")
        preview.photoSaved(second, "failed.jpg")
        assertNull(preview.flight)
        assertEquals("first thumbnail", preview.thumbnail)
        preview.expand()
        runCurrent()
        assertEquals("first.jpg@1600", preview.expandedPhoto)
    }

    @Test
    fun failureBeforeImageAvailabilityKeepsPreviousPhoto() = runTest {
        val preview = CapturePhotoPreviewState(backgroundScope, ::decoded)
        val first = assertNotNull(preview.beginCapture())
        preview.photoCaptured(first, "photo", "thumbnail")
        preview.photoSaved(first, "first.jpg")
        val second = assertNotNull(preview.beginCapture())
        preview.photoFailed(second)
        assertEquals("thumbnail", preview.thumbnail)
        preview.expand()
        runCurrent()
        assertEquals("first.jpg@1600", preview.expandedPhoto)
    }

    @Test
    fun failedPreviewDecodeDoesNotAssociateOldThumbnailWithNewFile() = runTest {
        val preview = CapturePhotoPreviewState(backgroundScope, ::decoded)
        val first = assertNotNull(preview.beginCapture())
        preview.photoCaptured(first, "photo", "thumbnail")
        preview.photoSaved(first, "first.jpg")
        val second = assertNotNull(preview.beginCapture())
        preview.photoSaved(second, "second.jpg")
        assertEquals("thumbnail", preview.thumbnail)
        preview.expand()
        runCurrent()
        assertEquals("first.jpg@1600", preview.expandedPhoto)
    }

    @Test
    fun duplicateImageAndSaveCallbacksDoNotRestartAnimationOrChangePath() = runTest {
        val preview = CapturePhotoPreviewState(backgroundScope, ::decoded)
        val id = assertNotNull(preview.beginCapture())
        preview.photoCaptured(id, "photo", "thumbnail")
        preview.photoSaved(id, "first.jpg")
        preview.flightFinished(id)
        preview.photoCaptured(id, "duplicate", "duplicate")
        preview.photoSaved(id, "wrong.jpg")
        assertNull(preview.flight)
        preview.expand()
        runCurrent()
        assertEquals("first.jpg@1600", preview.expandedPhoto)
    }

    @Test
    fun collapseRetainsExpandedPhotoUntilExitAndNextCaptureCollapsesIt() = runTest {
        val preview = CapturePhotoPreviewState(backgroundScope, ::decoded)
        val id = assertNotNull(preview.beginCapture())
        preview.photoCaptured(id, "photo", "thumbnail")
        preview.photoSaved(id, "first.jpg")
        preview.expand()
        runCurrent()
        assertTrue(preview.expanded)
        preview.collapse()
        assertFalse(preview.expanded)
        assertNotNull(preview.expandedPhoto)
        advanceTimeBy(CapturePhotoPreviewState.EXIT_ANIMATION_MILLIS)
        runCurrent()
        assertNull(preview.expandedPhoto)
        preview.expand()
        runCurrent()
        preview.beginCapture()
        assertFalse(preview.expanded)
    }

    @Test
    fun collapseInvalidatesPendingExpandDecode() = runTest {
        val expandedImage = CompletableDeferred<String?>()
        val preview = CapturePhotoPreviewState(backgroundScope) { _: String, _: Int ->
            withContext(NonCancellable) { expandedImage.await() }
        }
        val id = assertNotNull(preview.beginCapture())
        preview.photoCaptured(id, "photo", "thumbnail")
        preview.photoSaved(id, "first.jpg")
        preview.expand()
        runCurrent()
        preview.collapse()
        expandedImage.complete("bitmap")
        runCurrent()
        assertFalse(preview.expanded)
        assertNull(preview.expandedPhoto)
    }

    @Test
    fun closeDiscardsPendingDecodeAndIgnoresLateCaptureCallbacks() = runTest {
        val expandedImage = CompletableDeferred<String?>()
        val preview = CapturePhotoPreviewState(backgroundScope) { _: String, _: Int ->
            withContext(NonCancellable) { expandedImage.await() }
        }
        val id = assertNotNull(preview.beginCapture())
        preview.photoCaptured(id, "photo", "thumbnail")
        preview.photoSaved(id, "first.jpg")
        preview.expand()
        runCurrent()
        preview.close()
        expandedImage.complete("bitmap")
        preview.photoCaptured(id, "late", "late")
        preview.photoSaved(id, "late.jpg")
        preview.photoFailed(id)
        runCurrent()
        assertNull(preview.beginCapture())
        assertFalse(preview.isCurrentCapture(id))
        assertNull(preview.flight)
        assertNull(preview.thumbnail)
        assertNull(preview.expandedPhoto)
    }
}

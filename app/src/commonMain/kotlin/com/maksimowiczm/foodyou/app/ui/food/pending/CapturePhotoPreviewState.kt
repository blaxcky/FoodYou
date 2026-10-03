package com.maksimowiczm.foodyou.app.ui.food.pending

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A frozen viewfinder frame that animates from full screen into the thumbnail slot. */
internal data class CaptureFlight<T>(val id: Int, val frame: T)

/**
 * Keeps the last captured photo as a thumbnail next to the shutter, independent of the camera's
 * capture lock. The frozen viewfinder frame stands in for the thumbnail until the saved photo is
 * decoded, so the capture animation never waits for disk or decoder.
 */
internal class CapturePhotoPreviewState<T>(
    private val scope: CoroutineScope,
    private val loadPhoto: suspend (path: String, maxSizePx: Int) -> T?,
) {
    var flight: CaptureFlight<T>? by mutableStateOf(null)
        private set
    var thumbnail: T? by mutableStateOf(null)
        private set
    var expandedPhoto: T? by mutableStateOf(null)
        private set
    var expanded: Boolean by mutableStateOf(false)
        private set

    private var thumbnailPath: String? = null
    private var previousThumbnail: T? = null
    private var previousThumbnailPath: String? = null
    private var capturing = false
    private var flightCount = 0
    private var captureGeneration = 0
    private var expandGeneration = 0
    private var thumbnailJob: Job? = null
    private var expandJob: Job? = null
    private var closed = false

    fun captureStarted(frame: T?) {
        if (closed) return
        collapse()
        captureGeneration++
        thumbnailJob?.cancel()
        capturing = true
        previousThumbnail = thumbnail
        previousThumbnailPath = thumbnailPath
        if (frame != null) {
            flight = CaptureFlight(++flightCount, frame)
            thumbnail = frame
        }
    }

    fun flightFinished(id: Int) {
        if (flight?.id == id) flight = null
    }

    fun photoSaved(path: String) {
        if (closed) return
        capturing = false
        thumbnailPath = path
        previousThumbnail = null
        val request = captureGeneration
        thumbnailJob = scope.launch {
            val image = load(path, THUMBNAIL_SIZE_PX)
            if (closed || request != captureGeneration || image == null) return@launch
            thumbnail = image
        }
    }

    fun photoFailed() {
        if (closed) return
        capturing = false
        captureGeneration++
        flight = null
        thumbnail = previousThumbnail
        thumbnailPath = previousThumbnailPath
        previousThumbnail = null
    }

    fun expand() {
        val path = thumbnailPath
        if (closed || capturing || path == null) return
        val request = ++expandGeneration
        expandJob?.cancel()
        expandJob = scope.launch {
            val image = load(path, EXPANDED_SIZE_PX)
            if (closed || request != expandGeneration || image == null) return@launch
            expandedPhoto = image
            expanded = true
        }
    }

    fun collapse() {
        val request = ++expandGeneration
        expandJob?.cancel()
        if (!expanded) {
            expandedPhoto = null
            return
        }
        expanded = false
        // Retain the image while the exit animation draws its final frames.
        expandJob = scope.launch {
            delay(EXIT_ANIMATION_MILLIS)
            if (request == expandGeneration) expandedPhoto = null
        }
    }

    fun close() {
        closed = true
        captureGeneration++
        expandGeneration++
        thumbnailJob?.cancel()
        expandJob?.cancel()
        flight = null
        thumbnail = null
        thumbnailPath = null
        previousThumbnail = null
        expanded = false
        expandedPhoto = null
    }

    private suspend fun load(path: String, maxSizePx: Int): T? =
        try {
            loadPhoto(path, maxSizePx)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }

    internal companion object {
        const val THUMBNAIL_SIZE_PX = 256
        const val EXPANDED_SIZE_PX = 1600
        const val EXIT_ANIMATION_MILLIS = 300L
    }
}

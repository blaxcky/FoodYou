package com.maksimowiczm.foodyou.app.ui.food.pending

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Keeps preview loading and expiry independent of the camera's capture lock. */
internal class CapturePhotoPreviewState<T>(
    private val scope: CoroutineScope,
    private val loadPhoto: suspend (String) -> T?,
) {
    var bitmap: T? by mutableStateOf(null)
        private set
    var visible: Boolean by mutableStateOf(false)
        private set

    private var generation = 0
    private var job: Job? = null
    private var closed = false

    fun captureStarted() = dismiss()

    fun photoSaved(path: String) {
        if (closed) return
        dismiss()
        val request = generation
        job = scope.launch {
            val image = try {
                loadPhoto(path)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (closed || request != generation || image == null) return@launch
            bitmap = image
            visible = true
            delay(1_200)
            if (request != generation) return@launch
            visible = false
            // Retain the image while the exit animation draws its final frames.
            delay(180)
            if (request == generation) bitmap = null
        }
    }

    fun dismiss() {
        generation++
        job?.cancel()
        job = null
        visible = false
        bitmap = null
    }

    fun close() {
        closed = true
        dismiss()
    }
}

package com.maksimowiczm.foodyou.ai

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class ScaleModelImage(val bytes: ByteArray, val imageSize: String)

internal sealed interface NativeModelResponse {
    data class Answer(val text: String, val truncated: Boolean = false) : NativeModelResponse
    data class Failure(val error: ScaleRecognitionResult.Error) : NativeModelResponse
}

/** A new conversation per stage prevents the localization answer from biasing the weight reading. */
internal suspend fun analyzeLocalScalePhoto(
    photo: ScaleModelImage,
    isCancelled: () -> Boolean,
    crop: (ScaleModelImage, ScaleDisplayBox) -> ScaleModelImage,
    generate: suspend (ScaleModelImage, String, String) -> NativeModelResponse,
): ScaleRecognitionResult {
    suspend fun cancelled(): Boolean {
        currentCoroutineContext().ensureActive()
        return isCancelled()
    }
    fun cancellation() = ScaleRecognitionResult.Error("Analyse abgebrochen.", fatal = true)
    if (cancelled()) return cancellation()
    val location = generate(photo, SCALE_LOCATE_PROMPT, "locate")
    if (location is NativeModelResponse.Failure) return location.error
    if (cancelled()) return cancellation()
    location as NativeModelResponse.Answer
    val box = if (location.truncated) null else parseScaleDisplayBox(location.text)
    val weightPhoto = if (box == null) photo else crop(photo, box)
    if (cancelled()) return cancellation()
    val response = generate(weightPhoto, SCALE_PROMPT, "read")
    if (response is NativeModelResponse.Failure) return response.error
    response as NativeModelResponse.Answer
    val result = parseScaleReading(response.text, response.truncated)
    if (cancelled()) return cancellation()
    // A geometrically valid box can still miss digits. Retry once in the original scene;
    // never turn a native failure into another call on a potentially unhealthy engine.
    val retryFullPhoto = result == ScaleRecognitionResult.Unreadable ||
        (result is ScaleRecognitionResult.Error && !result.fatal && result.kind in setOf(
            ScaleErrorKind.ResponseFormat, ScaleErrorKind.NonWholeGrams, ScaleErrorKind.Truncated,
        ))
    if (box == null || !retryFullPhoto) return result
    return when (val retry = generate(photo, SCALE_PROMPT, "read_full")) {
        is NativeModelResponse.Failure -> retry.error
        is NativeModelResponse.Answer -> parseScaleReading(retry.text, retry.truncated)
    }
}

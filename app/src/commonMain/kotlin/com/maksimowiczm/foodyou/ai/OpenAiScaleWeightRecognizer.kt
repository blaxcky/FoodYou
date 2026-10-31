package com.maksimowiczm.foodyou.ai

import io.ktor.http.fromHttpToGmtDate
import kotlin.random.Random
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** A session shares its cooldown, but keeps image data and attempt counts per worker. */
internal class OpenAiScaleWeightRecognizer(
    private val api: OpenAiClient,
    private val settings: OpenAiSettings,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val nowMillis: () -> Long = { kotlin.time.Clock.System.now().toEpochMilliseconds() },
    private val jitterMillis: () -> Long = { Random.nextLong(251) },
    private val loadImage: suspend (String) -> String,
) : ScaleWeightRecognizer {
    override val maxConcurrentPhotos: Int = settings.photoParallelism
    private val started = timeSource.markNow()
    private val gate = Mutex()
    private var resumeAt = 0L
    private fun elapsed() = started.elapsedNow().inWholeMilliseconds

    override suspend fun recognize(photoPath: String): ScaleRecognitionResult {
        return withTimeoutOrNull(300_000) {
            val deadline = elapsed() + 300_000
            val image = try { loadImage(photoPath) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { return@withTimeoutOrNull ScaleRecognitionResult.Error("Foto konnte nicht gelesen werden.") }
            try {
                repeat(3) { attempt ->
                    while (true) {
                        val wait = gate.withLock { resumeAt - elapsed() }
                        if (wait <= 0) break
                        if (elapsed() + wait >= deadline) return@withTimeoutOrNull exhausted()
                        delay(wait)
                    }
                    try {
                        return@withTimeoutOrNull parseScaleReading(api.request(settings.photo, SCALE_PROMPT, image).csv)
                    } catch (e: OpenAiHttpException) {
                        if (!e.temporary) return@withTimeoutOrNull ScaleRecognitionResult.Error(e.message.orEmpty(), true)
                        if (attempt == 2) return@withTimeoutOrNull exhausted()
                        val wait = retryAfterMillis(e.retryAfter, nowMillis()) ?: (1_000L shl attempt)
                        val until = elapsed() + wait.coerceAtMost(300_000) + jitterMillis()
                        if (wait >= 300_000 || until >= deadline) return@withTimeoutOrNull exhausted()
                        gate.withLock { resumeAt = maxOf(resumeAt, until) }
                    }
                }
                exhausted()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { ScaleRecognitionResult.Error(e.message ?: "OpenAI: Fehler", true) }
        } ?: ScaleRecognitionResult.Error("OpenAI: Zeitlimit der Fotoanalyse überschritten. Bitte erneut versuchen.", true)
    }

    private fun exhausted() = ScaleRecognitionResult.Error(
        "OpenAI: Vorübergehend ausgelastet oder Anfragelimit erreicht. Bitte später erneut versuchen.", true)

    override suspend fun close() = Unit
}

internal fun retryAfterMillis(value: String?, nowMillis: Long): Long? {
    val text = value?.trim() ?: return null
    text.toLongOrNull()?.let { seconds ->
        return if (seconds >= 0) seconds.coerceAtMost(Long.MAX_VALUE / 1000) * 1000 else null
    }
    return runCatching { (text.fromHttpToGmtDate().timestamp - nowMillis).coerceAtLeast(0) }.getOrNull()
}

package com.maksimowiczm.foodyou.ai

import android.os.SystemClock
import java.util.Locale

internal enum class AiTimingOutcome { Completed, Unreadable, Error, Cancelled }

internal data class AiTimingRecord(
    val run: Long,
    val photo: Int?,
    val totalMillis: Long,
    val firstResponseMillis: Long?,
    val outcome: AiTimingOutcome,
) {
    fun reportLine(): String {
        fun seconds(ms: Long) = String.format(Locale.ROOT, "%.3f s", ms / 1000.0)
        val elapsed = seconds(totalMillis)
        val timing = if (photo == null) "Modellladen=$elapsed" else
            "Foto $photo: Gesamt=$elapsed; Erste Antwort=${firstResponseMillis?.let(::seconds) ?: "nicht empfangen"}"
        return "Durchlauf=$run; $timing; Status=${outcome.name}"
    }
}

/** All durations use elapsed real time, never the user-adjustable wall clock. One instance per batch. */
internal class AiRunTimings(
    private val record: (AiTimingRecord) -> Unit,
    private val now: () -> Long = SystemClock::elapsedRealtime,
) {
    private val run = now()
    private var photoNumber = 0

    fun modelLoading(): (AiTimingOutcome) -> Unit {
        val start = now()
        return { outcome -> record(AiTimingRecord(run, null, now() - start, null, outcome)) }
    }

    fun photo(): PhotoTiming = PhotoTiming(run, ++photoNumber, now, record)
}

/** Callbacks may arrive on the native thread. Ignore empty, repeated or late response notifications. */
internal class PhotoTiming(
    private val run: Long,
    private val photo: Int,
    private val now: () -> Long,
    private val record: (AiTimingRecord) -> Unit,
) {
    private val start = now()
    private var generationStart: Long? = null
    private var firstResponse: Long? = null
    private var finished = false

    @Synchronized fun generationStarted() {
        if (!finished && generationStart == null) generationStart = now()
    }

    @Synchronized fun responseReceived(text: String) {
        if (!finished && firstResponse == null && text.isNotEmpty()) {
            generationStart?.let { firstResponse = now() - it }
        }
    }

    @Synchronized fun finish(outcome: AiTimingOutcome) {
        if (finished) return
        finished = true
        record(AiTimingRecord(run, photo, now() - start, firstResponse, outcome))
    }
}

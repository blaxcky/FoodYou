package com.maksimowiczm.foodyou.ai

import android.os.Bundle
import kotlinx.coroutines.CompletableDeferred

internal object LocalAiProtocol {
    const val OPEN = 1
    const val RECOGNIZE = 2
    const val CLOSE = 3
    const val TERMINATE = 4
    const val REPLY = 5
    const val REQUEST = "request"
    const val SESSION = "session"
    const val PHOTO = "photo"
    const val RESULT = "result"
    const val ERROR_KIND = "error_kind"
    const val GRAMS = "grams"
    const val MESSAGE = "message"
    const val FATAL = "fatal"
}

internal fun ScaleRecognitionResult.toIpcBundle() = Bundle().apply {
    when (val result = this@toIpcBundle) {
        is ScaleRecognitionResult.Recognized -> { putString("result", "recognized"); putDouble("grams", result.grams) }
        ScaleRecognitionResult.Unreadable -> putString("result", "unreadable")
        is ScaleRecognitionResult.Error -> {
            putString("result", "error")
            putString("message", result.message)
            putBoolean("fatal", result.fatal)
            putString("error_kind", result.kind.name)
        }
    }
}

internal fun Bundle.readRecognitionResult(): ScaleRecognitionResult = when (getString("result")) {
    "recognized" -> getDouble("grams").let {
        if (it.isFinite() && it > 0) ScaleRecognitionResult.Recognized(it)
        else ScaleRecognitionResult.Error("Ungültiges Gewicht vom KI-Prozess.", kind = ScaleErrorKind.ResponseFormat)
    }
    "unreadable" -> ScaleRecognitionResult.Unreadable
    else -> ScaleRecognitionResult.Error(getString("message") ?: "Lokaler KI-Prozess meldet einen Fehler.",
        getBoolean("fatal", true), runCatching { ScaleErrorKind.valueOf(getString("error_kind").orEmpty()) }.getOrDefault(ScaleErrorKind.Runtime))
}

/** Request IDs prevent a late response from completing a later photo. Confined to the main thread. */
internal class PendingAiRequests {
    private var nextId = 0
    private val requests = mutableMapOf<Int, CompletableDeferred<Bundle>>()
    fun create(): Pair<Int, CompletableDeferred<Bundle>> {
        val id = ++nextId
        return id to CompletableDeferred<Bundle>().also { requests[id] = it }
    }
    fun complete(id: Int, reply: Bundle) { requests.remove(id)?.complete(reply) }
    fun remove(id: Int) { requests.remove(id) }
    fun failAll() {
        val result = ScaleRecognitionResult.Error(
            "Der lokale KI-Prozess wurde beendet. Ursache unbekannt; bitte den Diagnosebericht unter Einstellungen → KI prüfen.",
            fatal = true, kind = ScaleErrorKind.ProcessDied,
        ).toIpcBundle()
        requests.values.forEach { it.complete(result) }
        requests.clear()
    }
}

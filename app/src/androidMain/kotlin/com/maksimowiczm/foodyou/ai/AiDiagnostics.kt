package com.maksimowiczm.foodyou.ai

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.Process
import com.maksimowiczm.foodyou.app.BuildConfig
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive

internal const val LOCAL_AI_PROCESS_SUFFIX = ":local_ai"
internal const val LOCAL_AI_RUNTIME = "0.16.1"

/** Diagnostics stay app-private. Rejected model text is retained so format failures are actionable. */
internal class AiDiagnostics(private val context: Context, private val worker: Boolean = false) {
    private val directory = File(context.noBackupFilesDir, "ai/diagnostics")
    private val logFile get() = File(directory, if (worker) "worker.log" else "client.log")

    @Synchronized
    fun record(phase: String, width: Int? = null, height: Int? = null) {
        try {
            directory.mkdirs()
            val memory = ActivityManager.MemoryInfo()
            context.getSystemService(ActivityManager::class.java).getMemoryInfo(memory)
            val processMemory = Debug.MemoryInfo().also(Debug::getMemoryInfo)
            val line = "${System.currentTimeMillis()} pid=${Process.myPid()} phase=$phase" +
                " availableMiB=${memory.availMem / 1048576} lowMemory=${memory.lowMemory}" +
                " pssKiB=${processMemory.totalPss} nativeHeapKiB=${Debug.getNativeHeapAllocatedSize() / 1024}" +
                if (width != null && height != null) " image=${width}x$height" else ""
            append(logFile, listOf(line))
        } catch (_: Exception) { /* Diagnostics must never break recognition. */ }
    }

    @Synchronized
    fun recordTiming(timing: AiTimingRecord) {
        try {
            append(File(directory, "timings.log"),
                listOf("${System.currentTimeMillis()} pid=${Process.myPid()} ${timing.reportLine()}"))
        } catch (_: Exception) { /* Timing diagnostics must not interrupt a batch. */ }
    }

    @Synchronized
    fun recordRejectedResponse(kind: ScaleErrorKind, response: String) {
        try {
            directory.mkdirs()
            val file = File(directory, "rejected-response.log")
            val temp = File(directory, "${file.name}.tmp")
            temp.writeText(buildString {
                appendLine("${System.currentTimeMillis()} kind=${kind.name}")
                appendLine("Antwort (JSON-kodiert): ${JsonPrimitive(response)}")
            })
            temp.renameTo(file)
        } catch (_: Exception) { /* Diagnostics must never break recognition. */ }
    }

    /** Keeps every model answer, not only rejected ones, so unreadable results are explainable. */
    @Synchronized
    fun recordResponse(outcome: String, model: String, imageSize: String, visionBackend: String, response: String) {
        try {
            append(File(directory, "responses.log"), listOf(
                "${System.currentTimeMillis()} result=$outcome model=$model image=$imageSize vision=$visionBackend" +
                    " raw=${JsonPrimitive(response.take(2_000))}",
            ))
        } catch (_: Exception) { /* Diagnostics must never break recognition. */ }
    }

    /**
     * Copies this process's own LiteRT log lines since [sinceMillis]; reading one's own logcat needs no
     * permission. The preprocessor line shows the image size Gemma actually received on the device.
     */
    fun recordNativeLog(sinceMillis: Long) {
        try {
            val since = String.format(Locale.ROOT, "%d.%03d", sinceMillis / 1000, sinceMillis % 1000)
            val process = ProcessBuilder("logcat", "-d", "-m", "500", "-v", "epoch",
                "--pid=${Process.myPid()}", "-T", since).redirectErrorStream(true).start()
            val lines = try { process.inputStream.bufferedReader().use { it.readLines() } }
                finally { process.destroy() }
            val selected = selectNativeLogLines(lines)
            if (selected.isNotEmpty()) synchronized(this) { append(File(directory, "native.log"), selected) }
        } catch (_: Exception) { /* Diagnostics must never break recognition. */ }
    }

    private fun append(file: File, lines: List<String>) {
        directory.mkdirs()
        val previous = if (file.isFile) file.readLines() else emptyList()
        val temp = File(directory, "${file.name}.tmp")
        temp.writeText((previous + lines).takeLast(80).joinToString("\n", postfix = "\n"))
        temp.renameTo(file)
    }

    suspend fun report(): String = withContext(Dispatchers.IO) {
        buildString {
            appendLine("FoodYou KI-Diagnose · ${BuildConfig.VERSION_NAME}")
            appendLine("Gerät: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("LiteRT-LM: $LOCAL_AI_RUNTIME")
            appendLine("Lokale Modelle: " + GemmaModel.entries.joinToString { "${it.displayName} · ${it.revision}" })
            appendLine("Backend: GPU · Vision: GPU oder CPU laut Einstellung (vision= je Antwort) · Kontext: 4096 · Ausgabe: 128")
            appendLine("Zeitangaben: Unix-Millisekunden; keine Fotos oder API-Schlüssel.")
            appendLine("Modellantworten können erkannten Bildtext enthalten; vor Weitergabe prüfen.")
            appendLine()
            appendLine("Letzte verworfene Modellantwort:")
            appendLine(readLog("rejected-response.log"))
            appendLine()
            appendLine("Letzte Modellantworten (alle Ergebnisse mit Modell und Vision-Backend, JSON-kodiert):")
            appendLine(readLog("responses.log"))
            appendLine()
            appendLine("Native LiteRT-Meldungen des KI-Prozesses (u. a. tatsächliche Bildgröße für Gemma):")
            appendLine(readLog("native.log"))
            appendLine()
            appendLine("Laufzeiten (monotone Uhr, Sekunden):")
            appendLine("Modellladen separat; Foto-Gesamtzeit enthält Bildvorbereitung, Sitzung und Aufräumen.")
            appendLine("Erste Antwort: ab Generierungsstart. Fehlende Werte nach Prozessabbruch sind keine abgeschlossenen Messungen.")
            appendLine(readLog("timings.log"))
            appendLine()
            appendLine("Android-Prozessabbruchinformationen:")
            if (Build.VERSION.SDK_INT >= 30) {
                try {
                    val records = context.getSystemService(ActivityManager::class.java)
                        .getHistoricalProcessExitReasons(context.packageName, 0, 20)
                        .filter { it.processName == context.packageName || it.processName == context.packageName + LOCAL_AI_PROCESS_SUFFIX }
                        .take(5)
                    if (records.isEmpty()) appendLine("Kein gespeicherter Abbruchgrund; Ursache unbekannt.")
                    for (record in records) {
                        appendLine("${record.timestamp} process=${record.processName} pid=${record.pid}" +
                            " reason=${exitReasonLabel(record.reason)} (${record.reason}) status=${record.status}" +
                            " pssKiB=${record.pss} rssKiB=${record.rss}")
                    }
                    appendLine("SIGKILL allein belegt keinen RAM-Mangel. Nicht jeder Hersteller meldet LOW_MEMORY.")
                } catch (_: Exception) { appendLine("Abbruchgrund nicht abrufbar; Ursache unbekannt.") }
            } else appendLine("Vor Android 11 nicht verfügbar; Ursache unbekannt.")
            for (name in listOf("client.log", "worker.log")) {
                appendLine("\n$name:")
                appendLine(readLog(name))
            }
        }
    }
    private fun readLog(name: String): String = try {
        val file = File(directory, name)
        if (file.isFile) file.readText().takeLast(32_000) else "Noch keine Aufzeichnung."
    } catch (_: Exception) { "Aufzeichnung nicht lesbar." }

}

internal fun exitReasonLabel(reason: Int): String = when (reason) {
    ApplicationExitInfo.REASON_LOW_MEMORY -> "Android meldet Speichermangel"
    ApplicationExitInfo.REASON_CRASH_NATIVE -> "Nativer Absturz"
    ApplicationExitInfo.REASON_CRASH -> "Java/Kotlin-Absturz"
    ApplicationExitInfo.REASON_ANR -> "App reagiert nicht (ANR)"
    ApplicationExitInfo.REASON_SIGNALED -> "Prozess durch Signal beendet; Ursache unbekannt"
    ApplicationExitInfo.REASON_EXIT_SELF -> "Prozess hat sich beendet"
    ApplicationExitInfo.REASON_USER_REQUESTED -> "Vom Nutzer/System beendet"
    else -> "Ursache unbekannt"
}

private val nativeLogKeywords =
    Regex("litert|stb_image|patches|resize image|vision|opencl|ml_drift|gpu|delegate|tflite", RegexOption.IGNORE_CASE)
private val logcatLevel = Regex("""^\S+\s+\d+\s+\d+\s+([VDIWEF])\s""")

/** Keeps LiteRT-related lines and any warning or error of the worker process, newest last. */
internal fun selectNativeLogLines(lines: List<String>, limit: Int = 40): List<String> =
    // `logcat -v epoch` right-aligns the timestamp, so lines start with spaces.
    lines.map(String::trim).filter { line ->
        nativeLogKeywords.containsMatchIn(line) ||
            logcatLevel.find(line)?.groupValues?.get(1) in setOf("W", "E", "F")
    }.takeLast(limit)

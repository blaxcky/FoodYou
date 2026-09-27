package com.maksimowiczm.foodyou.ai

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.Process
import com.maksimowiczm.foodyou.app.BuildConfig
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val LOCAL_AI_PROCESS_SUFFIX = ":local_ai"
internal const val LOCAL_AI_RUNTIME = "0.16.1"

/** Only explicit numeric/enum metadata belongs here, never paths, responses or exception messages. */
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
            append(logFile, line)
        } catch (_: Exception) { /* Diagnostics must never break recognition. */ }
    }

    @Synchronized
    fun recordTiming(timing: AiTimingRecord) {
        try {
            append(File(directory, "timings.log"),
                "${System.currentTimeMillis()} pid=${Process.myPid()} ${timing.reportLine()}")
        } catch (_: Exception) { /* Timing diagnostics must not interrupt a batch. */ }
    }

    private fun append(file: File, line: String) {
        directory.mkdirs()
        val previous = if (file.isFile) file.readLines().takeLast(79) else emptyList()
        val temp = File(directory, "${file.name}.tmp")
        temp.writeText((previous + line).joinToString("\n", postfix = "\n"))
        temp.renameTo(file)
    }

    suspend fun report(): String = withContext(Dispatchers.IO) {
        buildString {
            appendLine("FoodYou KI-Diagnose · ${BuildConfig.VERSION_NAME}")
            appendLine("Gerät: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("LiteRT-LM: $LOCAL_AI_RUNTIME · Gemma 4 E4B · $GEMMA_REVISION")
            appendLine("Backend: GPU / Vision GPU · Kontext: 4096 · Ausgabe: 128")
            appendLine("Zeitangaben: Unix-Millisekunden; keine Fotos, Modellantworten oder API-Schlüssel.")
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

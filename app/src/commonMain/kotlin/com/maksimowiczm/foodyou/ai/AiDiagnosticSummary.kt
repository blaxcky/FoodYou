package com.maksimowiczm.foodyou.ai

internal data class AiDiagnosticSummary(
    val headline: String,
    val details: List<String>,
)

private data class DiagnosticTiming(
    val timestamp: Long,
    val run: String,
    val photo: Int?,
    val status: String,
)

/** Turns the deliberately technical copied report into a short, local-only explanation. */
internal fun summarizeAiDiagnosticReport(report: String): AiDiagnosticSummary {
    val timingPattern = Regex(
        """^(\d+)\s+.*Durchlauf=(\d+);\s+(?:Modellladen=.*|Foto\s+(\d+):.*);\s+Status=(Completed|Unreadable|Error|Cancelled)$""",
    )
    val timings = report.lineSequence().mapNotNull { line ->
        timingPattern.matchEntire(line.trim())?.let { match ->
            DiagnosticTiming(
                timestamp = match.groupValues[1].toLong(),
                run = match.groupValues[2],
                photo = match.groupValues[3].toIntOrNull(),
                status = match.groupValues[4],
            )
        }
    }.toList()
    val latestRunId = timings.lastOrNull()?.run
        ?: return AiDiagnosticSummary(
            headline = "Noch kein auswertbarer KI-Durchlauf",
            details = listOf("Starte eine lokale Analyse, damit hier eine Kurzauswertung erscheint."),
        )
    val latest = timings.filter { it.run == latestRunId }
    val model = latest.lastOrNull { it.photo == null }
    val photos = latest.filter { it.photo != null }
    val recognized = photos.count { it.status == "Completed" }
    val unreadable = photos.count { it.status == "Unreadable" }
    val errors = photos.count { it.status == "Error" }
    val cancelled = photos.count { it.status == "Cancelled" }
    val details = mutableListOf<String>()

    if (photos.isNotEmpty()) {
        details += buildString {
            append("Letzter Durchlauf: $recognized von ${photos.size} Fotos erkannt")
            if (unreadable > 0) append(", $unreadable unlesbar")
            if (errors > 0) append(", $errors Fehler")
            if (cancelled > 0) append(", $cancelled abgebrochen")
            append('.')
        }
    }

    val firstTiming = latest.minOf { it.timestamp }
    val recentLog = report.lineSequence().filter { line ->
        line.substringBefore(' ').toLongOrNull()?.let { it >= firstTiming } == true
    }.joinToString("\n")
    val reason = when {
        "phase=result_ResponseFormat" in recentLog ->
            "Gemma hat geantwortet, aber nicht im erwarteten Datenformat. Das Bild wurde verarbeitet; ein erneuter Versuch kann genügen."
        "phase=result_NonWholeGrams" in recentLog ->
            "Gemma hat kein gültiges Gewicht in ganzen Gramm geliefert. Prüfe Einheit und Dezimalstellen auf der Anzeige."
        "phase=result_Truncated" in recentLog ->
            "Die Modellantwort wurde abgeschnitten. Bitte das Foto erneut analysieren."
        "phase=result_unreadable" in recentLog || unreadable > 0 ->
            "Gemma konnte die Gewichtsanzeige nicht sicher lesen. Ein frontaler, scharfer Ausschnitt ohne Spiegelung hilft."
        "phase=recognition_failed" in recentLog ->
            "Die lokale Bildverarbeitung ist technisch fehlgeschlagen. Bitte erneut versuchen und bei Wiederholung die Details kopieren."
        "phase=worker_connection_lost" in recentLog ->
            "Der lokale KI-Prozess wurde während der Analyse beendet. Freien Arbeitsspeicher prüfen und erneut versuchen."
        errors > 0 ->
            "Mindestens eine lokale Analyse ist technisch fehlgeschlagen. Die genaue Phase steht in den technischen Details."
        else -> null
    }
    reason?.let(details::add)
    val rejectedResponseRelevant = listOf(
        "phase=result_ResponseFormat",
        "phase=result_NonWholeGrams",
        "phase=result_Truncated",
    ).any { it in recentLog }
    if (rejectedResponseRelevant) {
        report.lineSequence().lastOrNull { it.startsWith("Antwort (JSON-kodiert): ") }
            ?.removePrefix("Antwort (JSON-kodiert): ")
            ?.let { encoded ->
                val preview = encoded.take(240)
                details += "Verworfene Antwort: $preview${if (encoded.length > preview.length) "… (vollständig in den Details)" else ""}"
            }
    }

    recentLog.lineSequence().lastOrNull { " result=" in it && " raw=" in it }?.let { line ->
        val context = listOfNotNull(
            Regex(" image=(\\d+x\\d+)").find(line)?.let { "Foto ${it.groupValues[1]}" },
            Regex(" vision=(\\w+)").find(line)?.let { "Bildanalyse ${it.groupValues[1].uppercase()}" },
        )
        details += "Letzte Modellantwort: ${line.substringAfter(" raw=").take(160)}" +
            if (context.isEmpty()) "" else context.joinToString(", ", " (", ")")
    }
    Regex("Resize image from (\\d+x\\d+) to (\\d+x\\d+)").findAll(report).lastOrNull()?.let {
        details += "Gemma erhielt zuletzt ein Bild mit ${it.groupValues[2]} Pixeln (vorbereitet: ${it.groupValues[1]})."
    }

    val errorMemory = recentLog.lineSequence().lastOrNull {
        "phase=result_" in it || "phase=recognition_failed" in it
    }
    val availableMiB = errorMemory?.let { Regex("availableMiB=(\\d+)").find(it)?.groupValues?.get(1)?.toLongOrNull() }
    val lowMemory = errorMemory?.let { Regex("lowMemory=(true|false)").find(it)?.groupValues?.get(1) }
    if (availableMiB != null && lowMemory == "false") {
        details += "Beim Fehler meldete Android keinen Speichermangel (${availableMiB} MiB frei)."
    }

    val headline = when {
        model?.status == "Error" -> "Lokales Modell konnte nicht geladen werden"
        model?.status == "Cancelled" && photos.isEmpty() -> "Modellladen wurde abgebrochen"
        errors > 0 -> "$errors von ${photos.size} Fotos fehlgeschlagen"
        unreadable > 0 -> "$unreadable von ${photos.size} Fotos nicht lesbar"
        cancelled > 0 -> "Analyse wurde abgebrochen"
        photos.isNotEmpty() -> "Letzter Durchlauf ohne Fehler"
        else -> "Modell wurde erfolgreich geladen"
    }
    return AiDiagnosticSummary(headline, details)
}

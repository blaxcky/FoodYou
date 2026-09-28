package com.maksimowiczm.foodyou.training

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

const val TRAINING_PROJECT = "krafttraining-59773"
const val TRAINING_SOURCE = "krafttraining-tracker"

data class TrainingAccount(val project: String = TRAINING_PROJECT, val uid: String, val email: String?)
data class TrainingDocument(val id: String, val fields: JsonObject, val receivedAt: Instant?)

@Serializable
data class TrainingSession(
    val schemaVersion: Int,
    val source: String,
    val sessionId: String,
    val startedAt: String,
    val endedAt: String,
    val activityDate: String,
    val timeZone: String,
    val strengthKcal: Long,
    val cardioKcal: Long,
) {
    fun canonicalPayload(): String = Json.encodeToString(this)
}

class TrainingValidationException(message: String) : IllegalArgumentException(message)

fun validateTrainingDocument(document: TrainingDocument): TrainingSession {
    fun invalid(message: String): Nothing = throw TrainingValidationException(message)
    val fields = document.fields
    fun string(name: String): String = (fields[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
        ?: invalid("Ungültiges Feld: $name")
    fun integer(name: String): Long = (fields[name] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull
        ?: invalid("Ganzzahl erwartet: $name")
    if (integer("schemaVersion") != 1L) invalid("Unbekannte Trainings-Schema-Version")
    if (string("source") != TRAINING_SOURCE) invalid("Unbekannte Trainingsquelle")
    val id = string("sessionId")
    if (id != document.id || !Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}").matches(id))
        invalid("Ungültige Trainings-ID")
    val start = string("startedAt")
    val end = string("endedAt")
    val date = string("activityDate")
    val zone = string("timeZone")
    val utc = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{3}Z")
    try {
        require(utc.matches(start) && utc.matches(end) && Instant.parse(end) >= Instant.parse(start))
        require(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(date))
        LocalDate.parse(date)
        TimeZone.of(zone)
    } catch (_: IllegalArgumentException) { invalid("Ungültiges Trainingsdatum oder Zeitangabe") }
    if (document.receivedAt == null) invalid("Server-Empfangszeit fehlt")
    val strength = integer("strengthKcal")
    val cardio = integer("cardioKcal")
    if (strength !in 0..9_007_199_254_740_991L || cardio !in 0..9_007_199_254_740_991L)
        invalid("Kalorien müssen nichtnegative sichere Ganzzahlen sein")
    return TrainingSession(1, TRAINING_SOURCE, id, start, end, date, zone, strength, cardio)
}

enum class TrainingImportResult { Imported, AlreadyImported, ZeroCalories, Conflict }

@JvmInline value class ImportedActivityId(val value: Long)

data class ImportedActivity(
    val id: ImportedActivityId,
    val importId: String,
    val date: LocalDate,
    val name: String,
    val energyKcal: Long,
)

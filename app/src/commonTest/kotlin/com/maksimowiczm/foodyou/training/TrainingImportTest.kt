package com.maksimowiczm.foodyou.training

import kotlin.test.*
import kotlin.time.Instant
import kotlinx.serialization.json.*

internal const val TEST_SESSION = "86aa75c0-0686-4e8c-8b31-750c13409d23"
internal fun trainingDocument(id: String = TEST_SESSION, strength: Long = 210, cardio: Long = 120): TrainingDocument = TrainingDocument(
    id, buildJsonObject {
        put("schemaVersion", 1); put("source", TRAINING_SOURCE); put("sessionId", id)
        put("startedAt", "2026-09-27T21:50:00.000Z"); put("endedAt", "2026-09-28T00:30:00.000Z")
        put("activityDate", "2026-09-27"); put("timeZone", "Europe/Vienna")
        put("strengthKcal", strength); put("cardioKcal", cardio)
    }, Instant.parse("2026-10-02T12:00:00Z"),
)

class TrainingImportTest {
    @Test fun preservesBookingDateDespiteMidnightAndDelayedReceipt() {
        val result = validateTrainingDocument(trainingDocument())
        assertEquals("2026-09-27", result.activityDate)
        assertEquals(210, result.strengthKcal)
        assertEquals(120, result.cardioKcal)
    }
    @Test fun rejectsUnknownSchemaBadIdsDatesUnitsAndNumbers() {
        val document = trainingDocument()
        for ((field, value) in listOf(
            "schemaVersion" to JsonPrimitive(2), "source" to JsonPrimitive("unknown"),
            "sessionId" to JsonPrimitive("not-a-uuid"), "activityDate" to JsonPrimitive("2026-02-30"),
            "timeZone" to JsonPrimitive("No/Zone"), "endedAt" to JsonPrimitive("2026-09-26T00:00:00.000Z"),
            "strengthKcal" to JsonPrimitive(-1), "cardioKcal" to JsonPrimitive(1.5),
            "strengthKcal" to JsonPrimitive("210"), "strengthKcal" to JsonPrimitive(9007199254740992L),
        )) assertFailsWith<TrainingValidationException> {
            validateTrainingDocument(document.copy(fields = JsonObject(document.fields + (field to value))))
        }
        assertFailsWith<TrainingValidationException> { validateTrainingDocument(document.copy(id = "other")) }
        assertFailsWith<TrainingValidationException> { validateTrainingDocument(document.copy(receivedAt = null)) }
    }
}

package com.maksimowiczm.foodyou.ai

import kotlin.test.*
import org.junit.Test

class AiRunTimingsTest {
    @Test fun separatesLoadingFirstResponseAndPhotoTotalAndNumbersEachBatch() {
        var now = 1000L
        val records = mutableListOf<AiTimingRecord>()
        val timing = AiRunTimings(records::add) { now }
        val loaded = timing.modelLoading()
        now += 4200
        loaded(AiTimingOutcome.Completed)
        val photo = timing.photo()
        now += 500 // Decode and create session.
        photo.generationStarted()
        now += 14500
        photo.responseReceived("{\"value\"")
        now += 5000 // Remaining output and cleanup.
        photo.responseReceived(":161}")
        photo.finish(AiTimingOutcome.Completed)
        assertEquals(4200L, records[0].totalMillis)
        assertNull(records[0].photo)
        assertEquals(20000L, records[1].totalMillis)
        assertEquals(14500L, records[1].firstResponseMillis)
        assertEquals(1, records[1].photo)
        val next = timing.photo()
        now += 2000
        next.finish(AiTimingOutcome.Error)
        assertEquals(2, records[2].photo)
        assertEquals(2000L, records[2].totalMillis)
        assertNull(records[2].firstResponseMillis)
        AiRunTimings(records::add) { now }.photo().finish(AiTimingOutcome.Cancelled)
        assertEquals(1, records.last().photo)
        assertNotEquals(records.first().run, records.last().run)
    }

    @Test fun cancellationAndLateCallbacksCannotInventOrOverwriteCompletedMeasurements() {
        var now = 0L
        val records = mutableListOf<AiTimingRecord>()
        val photo = AiRunTimings(records::add) { now }.photo()
        photo.responseReceived("before start")
        photo.generationStarted()
        now = 5000
        photo.responseReceived("")
        photo.finish(AiTimingOutcome.Cancelled)
        now = 10000
        photo.responseReceived("late response")
        photo.finish(AiTimingOutcome.Completed)
        assertEquals(1, records.size)
        assertEquals(AiTimingOutcome.Cancelled, records.single().outcome)
        assertNull(records.single().firstResponseMillis)
        assertEquals(5000L, records.single().totalMillis)
    }

    @Test fun reportsSecondsWithoutAnyResponseContent() {
        val record = AiTimingRecord(1, 2, 20123, 14501, AiTimingOutcome.Completed)
        assertEquals("Durchlauf=1; Foto 2: Gesamt=20.123 s; Erste Antwort=14.501 s; Status=Completed", record.reportLine())
        assertTrue(record.copy(photo = null).reportLine().contains("Modellladen=20.123 s"))
        assertTrue(record.copy(firstResponseMillis = null).reportLine().contains("nicht empfangen"))
    }
}

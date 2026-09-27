package com.maksimowiczm.foodyou.ai

import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*

class ScaleRecognitionTest {
    @Test fun parsesDisplayUnitsAndDecimalSeparators() {
        assertEquals(ScaleRecognitionResult.Recognized(125.5), parseScaleReading("""{"readable":true,"value":"125,5","unit":"g"}"""))
        assertEquals(ScaleRecognitionResult.Recognized(1250.0), parseScaleReading("""{"readable":true,"value":1.25,"unit":"kg"}"""))
        assertEquals(ScaleRecognitionResult.Recognized(5.0), parseScaleReading("```json\n{\"readable\":true,\"value\":5,\"unit\":\"g\"}\n```"))
    }

    @Test fun refusesGuessesMalformedNumbersAndMissingUnits() {
        listOf("", "125 g", "[]", "null", "{bad}", """{"readable":false,"value":100,"unit":"g"}""",
            """{"value":100,"unit":"g"}""", """{"readable":true,"value":100}""").forEach {
            assertEquals(ScaleRecognitionResult.Unreadable, parseScaleReading(it))
        }
        listOf("0", "-1", "NaN", "Infinity", "1,234.5", "1e3", "100 g").forEach {
            assertEquals(ScaleRecognitionResult.Unreadable, parseScaleReading("""{"readable":true,"value":"$it","unit":"g"}"""))
        }
        assertEquals(ScaleRecognitionResult.Unreadable, parseScaleReading("""{"readable":true,"value":5,"unit":"oz"}"""))
    }

    @Test fun batchUsesOneSessionSkipsDeletedPhotosAndContinuesAfterUnreadable() = runTest {
        val coordinator = ScaleAnalysisCoordinator()
        var opened = 0
        var closed = 0
        val saved = mutableListOf<Long>()
        val recognizer = object : ScaleWeightRecognizer {
            override suspend fun recognize(photoPath: String) = if (photoPath == "a") ScaleRecognitionResult.Unreadable else ScaleRecognitionResult.Recognized(100.0)
            override suspend fun close() { closed++ }
        }
        coordinator.run(listOf(AnalysisPhoto(1,"a"), AnalysisPhoto(2,"b"), AnalysisPhoto(3,"c")),
            open = { opened++; recognizer }, isPending = { it != 2L }, save = { photo, _ -> saved += photo.id })
        assertEquals(1, opened)
        assertEquals(1, closed)
        assertEquals(listOf(1L,3L), saved)
        assertEquals(1, coordinator.progress.value.recognized)
        assertFalse(coordinator.progress.value.running)
    }

    @Test fun cancellationKeepsCompletedResultsAndPreventsOverlappingSessions() = runTest {
        val coordinator = ScaleAnalysisCoordinator()
        var closed = 0
        val saved = mutableListOf<Long>()
        val recognizer = object : ScaleWeightRecognizer {
            override suspend fun recognize(photoPath: String): ScaleRecognitionResult {
                if (photoPath == "b") awaitCancellation()
                return ScaleRecognitionResult.Recognized(12.0)
            }
            override suspend fun close() { closed++ }
        }
        val job = launch { coordinator.run(listOf(AnalysisPhoto(1,"a"), AnalysisPhoto(2,"b")),
            { recognizer }, { true }, { photo, _ -> saved += photo.id }) }
        runCurrent()
        coordinator.run(listOf(AnalysisPhoto(3,"c")), { error("Must not open a second engine") }, { true }, { _, _ -> })
        job.cancelAndJoin()
        assertEquals(listOf(1L), saved)
        assertEquals(1, closed)
        assertFalse(coordinator.progress.value.running)
        coordinator.run(listOf(AnalysisPhoto(3,"c")), { recognizer }, { true }, { photo, _ -> saved += photo.id })
        assertEquals(listOf(1L,3L), saved)
    }

    @Test fun fatalErrorStopsRemainingPhotosAndClosesEngine() = runTest {
        val coordinator = ScaleAnalysisCoordinator()
        var calls = 0
        var closed = false
        val recognizer = object : ScaleWeightRecognizer {
            override suspend fun recognize(photoPath: String): ScaleRecognitionResult {
                calls++
                return ScaleRecognitionResult.Error("quota", true)
            }
            override suspend fun close() { closed = true }
        }
        coordinator.run(listOf(AnalysisPhoto(1,"a"), AnalysisPhoto(2,"b")), { recognizer }, { true }, { _, _ -> })
        assertEquals(1, calls)
        assertTrue(closed)
        assertEquals("quota", coordinator.progress.value.message)
    }
}

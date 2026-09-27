package com.maksimowiczm.foodyou.ai

import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*

class ScaleRecognitionTest {
    @Test fun acceptsOnlyWholeGramsWithExactKilogramConversion() {
        for ((raw, unit, grams) in listOf(
            Triple("161", "g", 161.0), Triple("269", "g", 269.0),
            Triple("161.0", "g", 161.0), Triple("1.001", "kg", 1001.0),
            Triple("0,161", "kg", 161.0), Triple("0.2690", "kg", 269.0),
            Triple("1.25", "kg", 1250.0), Triple("2", "kg", 2000.0),
        )) assertEquals(ScaleRecognitionResult.Recognized(grams), parseScaleReading("""{"value":"$raw","unit":"$unit"}"""))
        assertEquals(ScaleRecognitionResult.Recognized(5.0), parseScaleReading("```json\n{\"value\":5,\"unit\":\"g\"}\n```"))
    }

    @Test fun rejectsFractionalGramsWithoutRoundingOrRemovingDecimalPoints() {
        for ((raw, unit) in listOf("16.1" to "g", "16,1" to "g", "26.9" to "g", "0.1611" to "kg", "161.00000000000000001" to "g")) {
            val error = assertIs<ScaleRecognitionResult.Error>(parseScaleReading("""{"value":"$raw","unit":"$unit"}"""))
            assertEquals(ScaleErrorKind.NonWholeGrams, error.kind)
            assertEquals("Kein gültiger Vorschlag in ganzen Gramm", error.message)
            assertFalse(error.fatal)
        }
    }

    @Test fun refusesGuessesMalformedNumbersAndMissingUnits() {
        listOf("", "125 g", "[]", "null", "{bad}", "{}", """{"value":100}""",
            """{"value":100,"unit":"g","extra":1}""", """{"readable":false}""",
            """{"value":null,"unit":"g"}""", """{"value":"null"}""").forEach {
            assertEquals(ScaleErrorKind.ResponseFormat, assertIs<ScaleRecognitionResult.Error>(parseScaleReading(it)).kind)
        }
        listOf("0", "0.000", "-1", "NaN", "Infinity", "1,234.5", "1e3", "100 g", "9007199254740992").forEach {
            assertTrue(parseScaleReading("""{"value":"$it","unit":"g"}""") is ScaleRecognitionResult.Error)
        }
        assertTrue(parseScaleReading("""{"value":5,"unit":"oz"}""") is ScaleRecognitionResult.Error)
    }

    @Test fun distinguishesUnreadableTruncatedAndInvalidAnswers() {
        assertEquals(ScaleRecognitionResult.Unreadable, parseScaleReading("""{"value":null}"""))
        assertEquals(ScaleErrorKind.Truncated, assertIs<ScaleRecognitionResult.Error>(parseScaleReading("{\"value\":" )).kind)
        assertEquals(ScaleErrorKind.Truncated, assertIs<ScaleRecognitionResult.Error>(parseScaleReading("{}", truncated = true)).kind)
    }

    @Test fun invalidDecimalDoesNotTriggerAutomaticRetryOrBlockNextPhoto() = runTest {
        val coordinator = ScaleAnalysisCoordinator()
        val calls = mutableListOf<String>()
        val saved = mutableListOf<ScaleRecognitionResult>()
        val recognizer = object : ScaleWeightRecognizer {
            override suspend fun recognize(photoPath: String): ScaleRecognitionResult {
                calls += photoPath
                return parseScaleReading(if (photoPath == "a") """{"value":16.1,"unit":"g"}""" else """{"value":269,"unit":"g"}""")
            }
            override suspend fun close() = Unit
        }
        coordinator.run(listOf(AnalysisPhoto(1,"a"), AnalysisPhoto(2,"b")),
            { recognizer }, { true }, { _, result -> saved += result })
        assertEquals(listOf("a", "b"), calls)
        assertEquals(ScaleErrorKind.NonWholeGrams, assertIs<ScaleRecognitionResult.Error>(saved.first()).kind)
        assertEquals(ScaleRecognitionResult.Recognized(269.0), saved.last())
    }

    @Test fun processLossAndTimeoutPreserveCompletedResultsAndStopBatch() = runTest {
        for (kind in listOf(ScaleErrorKind.ProcessDied, ScaleErrorKind.Timeout)) {
            val coordinator = ScaleAnalysisCoordinator()
            val saved = mutableListOf<ScaleRecognitionResult>()
            var calls = 0
            var closed = false
            val recognizer = object : ScaleWeightRecognizer {
                override suspend fun recognize(photoPath: String): ScaleRecognitionResult {
                    calls++
                    return if (calls == 1) ScaleRecognitionResult.Recognized(269.0)
                    else ScaleRecognitionResult.Error("worker failed", fatal = true, kind = kind)
                }
                override suspend fun close() { closed = true }
            }
            coordinator.run(listOf(AnalysisPhoto(1,"a"), AnalysisPhoto(2,"b"), AnalysisPhoto(3,"c")),
                { recognizer }, { true }, { _, result -> saved += result })
            assertEquals(2, calls)
            assertEquals(ScaleRecognitionResult.Recognized(269.0), saved.first())
            assertTrue(closed)
            assertFalse(coordinator.progress.value.running)
        }
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

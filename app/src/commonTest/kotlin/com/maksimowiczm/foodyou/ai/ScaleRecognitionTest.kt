package com.maksimowiczm.foodyou.ai

import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*

class ScaleRecognitionTest {
    @Test fun stopwatchIncludesLoadingUpdatesDuringRecognitionAndFreezesUntilNextRun() = runTest {
        val time = TestTimeSource()
        val coordinator = ScaleAnalysisCoordinator(time)
        val loaded = CompletableDeferred<Unit>()
        val reading = CompletableDeferred<ScaleRecognitionResult>()
        val recognizer = object : ScaleWeightRecognizer {
            override suspend fun recognize(photoPath: String) = reading.await()
            override suspend fun close() { time += 50.milliseconds }
        }
        val job = launch {
            coordinator.run(listOf(AnalysisPhoto(1, "a")),
                { loaded.await(); recognizer }, { true }, { _, _ -> })
        }
        runCurrent()
        assertEquals(0L, coordinator.progress.value.elapsedMillis)
        time += 1_200.milliseconds
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1_200L, coordinator.progress.value.elapsedMillis)
        assertEquals(0, coordinator.progress.value.completed)

        coordinator.run(listOf(AnalysisPhoto(2, "b")),
            { error("Must not reset the active stopwatch") }, { true }, { _, _ -> })
        assertEquals(1_200L, coordinator.progress.value.elapsedMillis)
        loaded.complete(Unit)
        runCurrent()
        time += 800.milliseconds
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(2_000L, coordinator.progress.value.elapsedMillis)
        time += 345.milliseconds
        reading.complete(ScaleRecognitionResult.Recognized(100.0))
        job.join()
        assertFalse(coordinator.progress.value.running)
        assertEquals(1, coordinator.progress.value.completed)
        assertEquals(1, coordinator.progress.value.recognized)
        assertEquals(2_395L, coordinator.progress.value.elapsedMillis)

        time += 5_000.milliseconds
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(2_395L, coordinator.progress.value.elapsedMillis)
        coordinator.run(listOf(AnalysisPhoto(2, "b")),
            open = {
                assertEquals(0L, coordinator.progress.value.elapsedMillis)
                recognizer
            }, isPending = { true }, save = { _, _ -> })
        assertEquals(50L, coordinator.progress.value.elapsedMillis)
    }

    @Test fun stopwatchStopsWhenLoadingFails() = runTest {
        val time = TestTimeSource()
        val coordinator = ScaleAnalysisCoordinator(time)
        coordinator.run(listOf(AnalysisPhoto(1, "a")),
            open = { time += 700.milliseconds; error("Loading failed") },
            isPending = { true }, save = { _, _ -> })
        assertFalse(coordinator.progress.value.running)
        assertNotNull(coordinator.progress.value.message)
        assertEquals(700L, coordinator.progress.value.elapsedMillis)
    }

    @Test fun acceptsOnlyWholeGramsWithExactKilogramConversion() {
        for ((raw, unit, grams) in listOf(
            Triple("161", "g", 161.0), Triple("269", "g", 269.0),
            Triple("161.0", "g", 161.0), Triple("1.001", "kg", 1001.0),
            Triple("0,161", "kg", 161.0), Triple("0.2690", "kg", 269.0),
            Triple("1.25", "kg", 1250.0), Triple("2", "kg", 2000.0),
        )) assertEquals(ScaleRecognitionResult.Recognized(grams), parseScaleReading("""{"value":"$raw","unit":"$unit"}"""))
        assertEquals(ScaleRecognitionResult.Recognized(5.0), parseScaleReading("```json\n{\"value\":5,\"unit\":\"g\"}\n```"))
        assertEquals(ScaleRecognitionResult.Recognized(5.0), parseScaleReading("""{"value":5}"""))
    }

    @Test fun rejectsFractionalGramsWithoutRoundingOrRemovingDecimalPoints() {
        for ((raw, unit) in listOf("16.1" to "g", "16,1" to "g", "26.9" to "g", "0.1611" to "kg", "161.00000000000000001" to "g")) {
            val error = assertIs<ScaleRecognitionResult.Error>(parseScaleReading("""{"value":"$raw","unit":"$unit"}"""))
            assertEquals(ScaleErrorKind.NonWholeGrams, error.kind)
            assertEquals("Kein gültiger Vorschlag in ganzen Gramm", error.message)
            assertFalse(error.fatal)
        }
    }

    @Test fun refusesGuessesMalformedNumbersAndUnexpectedFields() {
        listOf("", "125 g", "[]", "null", "{bad}", "{}",
            """{"value":100,"unit":"g","extra":1}""", """{"readable":false}""",
            """{"value":null,"unit":"g"}""", """{"value":5,"unit":null}""", """{"value":"null"}""").forEach {
            assertEquals(ScaleErrorKind.ResponseFormat, assertIs<ScaleRecognitionResult.Error>(parseScaleReading(it)).kind)
        }
        listOf("0", "0.000", "-1", "NaN", "Infinity", "1,234.5", "1e3", "100 g", "9007199254740992").forEach {
            assertTrue(parseScaleReading("""{"value":"$it","unit":"g"}""") is ScaleRecognitionResult.Error)
        }
        assertTrue(parseScaleReading("""{"value":5,"unit":"oz"}""") is ScaleRecognitionResult.Error)
        assertEquals(
            ScaleErrorKind.NonWholeGrams,
            assertIs<ScaleRecognitionResult.Error>(parseScaleReading("""{"value":5.5}""")).kind,
        )
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
        val time = TestTimeSource()
        val coordinator = ScaleAnalysisCoordinator(time)
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
        time += 350.milliseconds
        job.cancelAndJoin()
        assertEquals(listOf(1L), saved)
        assertEquals(1, closed)
        assertFalse(coordinator.progress.value.running)
        assertTrue(coordinator.progress.value.paused)
        assertEquals(350L, coordinator.progress.value.elapsedMillis)
        coordinator.run(listOf(AnalysisPhoto(3,"c")), { recognizer }, { true }, { photo, _ -> saved += photo.id })
        assertEquals(listOf(1L,3L), saved)
        assertEquals(0L, coordinator.progress.value.elapsedMillis)
    }

    @Test fun fatalErrorStopsRemainingPhotosAndClosesEngine() = runTest {
        val time = TestTimeSource()
        val coordinator = ScaleAnalysisCoordinator(time)
        var calls = 0
        var closed = false
        val recognizer = object : ScaleWeightRecognizer {
            override suspend fun recognize(photoPath: String): ScaleRecognitionResult {
                calls++
                time += 1_500.milliseconds
                return ScaleRecognitionResult.Error("quota", true)
            }
            override suspend fun close() { closed = true }
        }
        coordinator.run(listOf(AnalysisPhoto(1,"a"), AnalysisPhoto(2,"b")), { recognizer }, { true }, { _, _ -> })
        assertEquals(1, calls)
        assertTrue(closed)
        assertEquals("quota", coordinator.progress.value.message)
        assertEquals(1_500L, coordinator.progress.value.elapsedMillis)
    }
}

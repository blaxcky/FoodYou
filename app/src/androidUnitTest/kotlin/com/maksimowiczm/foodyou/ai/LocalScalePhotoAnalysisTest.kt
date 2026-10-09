package com.maksimowiczm.foodyou.ai

import kotlin.test.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LocalScalePhotoAnalysisTest {
    private val photo = ScaleModelImage(byteArrayOf(1), "542x723")
    private val display = ScaleModelImage(byteArrayOf(2), "856x701")
    private val location = NativeModelResponse.Answer("""{"value":19,"unit":"g","box_2d":[465,626,563,786]}""")

    @Test fun readsCroppedDisplayAndPreservesGenuineOnesInSingleAndBatchAnalyses() = runTest {
        val prompts = mutableListOf<String>()
        val stages = mutableListOf<String>()
        // Different photos in one run must not reuse another photo's location or reading.
        for (grams in listOf(79,117,199,79)) {
            val result = analyzeLocalScalePhoto(photo, { false }, { original, box ->
                assertSame(photo, original)
                assertEquals(ScaleDisplayBox(465,626,563,786), box)
                display
            }) { image, prompt, stage ->
                prompts += prompt
                stages += stage
                when (stage) {
                    "locate" -> { assertSame(photo, image); location }
                    "read" -> {
                        assertSame(display, image)
                        NativeModelResponse.Answer("""{"value":$grams,"unit":"g"}""")
                    }
                    else -> error("Unexpected stage")
                }
            }
            assertEquals(ScaleRecognitionResult.Recognized(grams.toDouble()), result)
        }
        assertEquals(List(4) { listOf(SCALE_LOCATE_PROMPT, SCALE_PROMPT) }.flatten(), prompts)
        assertEquals(List(4) { listOf("locate", "read") }.flatten(), stages)
    }

    @Test fun invalidAndTruncatedLocationsReadFullPhotoInsteadOfDroppingItsWeight() = runTest {
        for (answer in listOf(
            NativeModelResponse.Answer("{}"), NativeModelResponse.Answer("not json"),
            NativeModelResponse.Answer("""{"value":null,"box_2d":[465,626,563,786]}"""),
            NativeModelResponse.Answer("""{"box_2d":[563,626,465,786]}"""),
            NativeModelResponse.Answer(location.text, truncated = true),
        )) {
            val stages = mutableListOf<String>()
            val result = analyzeLocalScalePhoto(photo, { false }, { _, _ -> error("Must not crop invalid box") }) {
                    image, _, stage ->
                assertSame(photo, image)
                stages += stage
                if (stage == "locate") answer else NativeModelResponse.Answer("""{"value":199,"unit":"g"}""")
            }
            assertEquals(ScaleRecognitionResult.Recognized(199.0), result)
            assertEquals(listOf("locate", "read"), stages)
        }
    }

    @Test fun noDisplayAndInvalidWholeGramReadingsKeepTheirExistingResults() = runTest {
        for ((answer, expected) in listOf(
            """{"value":null}""" to ScaleRecognitionResult.Unreadable,
            """{"value":16.1,"unit":"g"}""" to parseScaleReading("""{"value":16.1,"unit":"g"}"""),
        )) {
            assertEquals(expected, analyzeLocalScalePhoto(photo, { false }, { _, _ -> display }) { _, _, stage ->
                if (stage == "locate") location else NativeModelResponse.Answer(answer)
            })
        }
    }

    @Test fun fatalLocalizationFailureNeverStartsCroppingOrReading() = runTest {
        val failure = ScaleRecognitionResult.Error("native failure", fatal = true)
        var calls = 0
        assertEquals(failure, analyzeLocalScalePhoto(photo, { false }, { _, _ -> error("Must not crop") }) { _, _, _ ->
            calls++
            NativeModelResponse.Failure(failure)
        })
        assertEquals(1, calls)
    }

    @Test fun unreadableOrInvalidCropRetriesOriginalPhotoOnlyOnce() = runTest {
        for (answer in listOf("""{"value":null}""", """{"value":269.2,"unit":"g"}""", "not json", "{\"value\":")) {
            val stages = mutableListOf<String>()
            val result = analyzeLocalScalePhoto(photo, { false }, { _, _ -> display }) { image, prompt, stage ->
                stages += stage
                when (stage) {
                    "locate" -> location
                    "read" -> { assertSame(display, image); NativeModelResponse.Answer(answer) }
                    "read_full" -> {
                        assertSame(photo, image)
                        assertEquals(SCALE_PROMPT, prompt)
                        NativeModelResponse.Answer("""{"value":269,"unit":"g"}""")
                    }
                    else -> error("Unexpected stage")
                }
            }
            assertEquals(ScaleRecognitionResult.Recognized(269.0), result)
            assertEquals(listOf("locate", "read", "read_full"), stages)
        }
    }

    @Test fun nativeCropFailureAndCancellationBeforeRetryNeverReadFullPhoto() = runTest {
        for (stop in listOf(false, true)) {
            var stopped = false
            val stages = mutableListOf<String>()
            val result = analyzeLocalScalePhoto(photo, { stopped }, { _, _ -> display }) { _, _, stage ->
                stages += stage
                when (stage) {
                    "locate" -> location
                    "read" -> {
                        stopped = stop
                        if (stop) NativeModelResponse.Answer("""{"value":null}""") else
                            NativeModelResponse.Failure(ScaleRecognitionResult.Error("native failure", fatal = true))
                    }
                    else -> error("Must not retry")
                }
            }
            assertTrue(assertIs<ScaleRecognitionResult.Error>(result).fatal)
            assertEquals(listOf("locate", "read"), stages)
        }
    }

    @Test fun stopBetweenStagesOrDuringCropDoesNotStartWeightGeneration() = runTest {
        for (stopDuringCrop in listOf(false, true)) {
            var stopped = false
            var calls = 0
            val result = analyzeLocalScalePhoto(photo, { stopped }, { _, _ ->
                assertTrue(stopDuringCrop)
                stopped = true
                display
            }) { _, _, stage ->
                assertEquals("locate", stage)
                calls++
                if (!stopDuringCrop) stopped = true
                location
            }
            assertTrue(assertIs<ScaleRecognitionResult.Error>(result).fatal)
            assertEquals(1, calls)
        }
        analyzeLocalScalePhoto(photo, { true }, { _, _ -> error("Must not crop") }) { _, _, _ ->
            error("Must not start a cancelled photo")
        }
    }

    @Test fun coroutineCancellationBetweenStagesIsPropagated() = runTest {
        var calls = 0
        val job = launch {
            assertFailsWith<CancellationException> {
                analyzeLocalScalePhoto(photo, { false }, { _, _ -> error("Must not crop") }) { _, _, _ ->
                    calls++
                    currentCoroutineContext().job.cancel()
                    location
                }
            }
        }
        job.join()
        assertEquals(1, calls)
    }
}

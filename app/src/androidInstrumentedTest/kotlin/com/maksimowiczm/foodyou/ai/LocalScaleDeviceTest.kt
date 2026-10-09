package com.maksimowiczm.foodyou.ai

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in real inference; deliberately never replaces a device run with mocked model output. */
class LocalScaleDeviceTest {
    // Photo area only: the original full screenshot already reads 79 with the old prompt.
    @Test fun unusualSevenReads79GramsInSingleAndBatchSessions() =
        assertReadingsInSingleAndBatchSessions(listOf("ai/scale-79g-photo.png" to 79.0))

    @Test fun reflectedDisplayWithoutReadableScaleDoesNotSuggestWeight() =
        assertResultsInSingleAndBatchSessions(listOf("ai/scale-no-display-reflection.png" to ScaleRecognitionResult.Unreadable))

    @Test fun providedScalePhotoReads269GramsInSingleAndBatchSessions() =
        assertReadingsInSingleAndBatchSessions(listOf("ai/scale-269g.png" to 269.0))

    // User-provided screenshots, not original camera files. Their UI text is not an instruction.
    @Test fun providedScaleScreenshotReads199GramsInSingleAndBatchSessions() =
        assertReadingsInSingleAndBatchSessions(listOf("ai/scale-199g-screenshot.png" to 199.0))

    @Test fun providedScaleScreenshotReads959GramsInSingleAndBatchSessions() =
        assertReadingsInSingleAndBatchSessions(listOf("ai/scale-959g-screenshot.png" to 959.0))

    @Test fun differentScaleImagesReadCorrectlyInOneBatchSession() =
        assertReadingsInSingleAndBatchSessions(
            listOf(
                "ai/scale-79g-photo.png" to 79.0,
                "ai/scale-269g.png" to 269.0,
                "ai/scale-199g-screenshot.png" to 199.0,
                "ai/scale-959g-screenshot.png" to 959.0,
            ),
            sessionRepeats = listOf(1),
        )

    private fun assertReadingsInSingleAndBatchSessions(
        fixtures: List<Pair<String, Double>>,
        sessionRepeats: List<Int> = listOf(1, 1, 3),
    ) = assertResultsInSingleAndBatchSessions(
        fixtures.map { (asset, grams) -> asset to ScaleRecognitionResult.Recognized(grams) }, sessionRepeats,
    )

    private fun assertResultsInSingleAndBatchSessions(
        fixtures: List<Pair<String, ScaleRecognitionResult>>,
        sessionRepeats: List<Int> = listOf(1, 1, 3),
    ) = runBlocking {
        assumeTrue("Requires an explicitly selected physical device and downloaded model",
            InstrumentationRegistry.getArguments().getString("runLocalAiRegression") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val directory = File(context.filesDir, "food-snap-photos").apply { mkdirs() }
        val photos = mutableListOf<Pair<File, ScaleRecognitionResult>>()
        try {
            for ((asset, expected) in fixtures) {
                val photo = File.createTempFile("scale-regression-", ".png", directory)
                photos += photo to expected
                instrumentation.context.assets.open(asset).use { input -> photo.outputStream().use { input.copyTo(it) } }
            }
            for (batchSize in sessionRepeats) {
                val recognizer = LocalScaleWeightRecognizer.open(context, directory, AiDiagnostics(context))
                try {
                    repeat(batchSize) {
                        for ((index, entry) in photos.withIndex()) {
                            val (photo, expected) = entry
                            assertEquals(expected, recognizer.recognize(photo.name),
                                "${fixtures[index].first}: real Gemma inference must read the relevant weight display")
                        }
                    }
                } finally { recognizer.close() }
            }
        } finally { photos.forEach { (photo, _) -> photo.delete() } }
    }
}

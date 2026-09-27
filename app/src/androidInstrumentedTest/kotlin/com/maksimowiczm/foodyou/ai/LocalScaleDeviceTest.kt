package com.maksimowiczm.foodyou.ai

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in real inference; deliberately never replaces a device run with mocked model output. */
class LocalScaleDeviceTest {
    @Test fun providedScalePhotoReads269GramsInSingleAndBatchSessions() = runBlocking {
        assumeTrue("Requires an explicitly selected physical device and downloaded model",
            InstrumentationRegistry.getArguments().getString("runLocalAiRegression") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val directory = File(context.filesDir, "food-snap-photos").apply { mkdirs() }
        val photo = File.createTempFile("scale-regression-", ".png", directory)
        try {
            instrumentation.context.assets.open("ai/scale-269g.png").use { input -> photo.outputStream().use { input.copyTo(it) } }
            for (batchSize in listOf(1, 1, 3)) {
                val recognizer = LocalScaleWeightRecognizer.open(context, directory, AiDiagnostics(context))
                try {
                    repeat(batchSize) {
                        assertEquals(ScaleRecognitionResult.Recognized(269.0), recognizer.recognize(photo.name),
                            "Real Gemma inference must read the weight display, not the auxiliary display")
                    }
                } finally { recognizer.close() }
            }
        } finally { photo.delete() }
    }
}

package com.maksimowiczm.foodyou.ai

import java.io.File
import kotlin.test.*
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AiInfrastructureTest {
    @Test fun resumedDownloadChecksRangeAndRestartsWhenIgnored() {
        assertTrue(acceptsDownloadResponse(206, "bytes 123-999/$GEMMA_SIZE", 123))
        assertFalse(acceptsDownloadResponse(200, null, 123))
        assertFailsWith<IllegalArgumentException> { acceptsDownloadResponse(206, "bytes 0-999/$GEMMA_SIZE", 123) }
        assertFailsWith<IllegalArgumentException> { acceptsDownloadResponse(206, "bytes 123-999/1000", 123) }
    }

    @Test fun verifiesBothSizeAndHashBeforeActivation() = runTest {
        val file = File.createTempFile("gemma-test", ".part")
        try {
            file.writeText("abc")
            assertTrue(verifyModelFile(file, 3, "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"))
            assertFalse(verifyModelFile(file, 4, "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"))
            assertFalse(verifyModelFile(file, 3, "invalid"))
        } finally { file.delete() }
    }

    @Test fun geminiErrorsAreSafeAndActionable() {
        listOf(400,401,403,404,429,500).forEach { assertTrue(geminiHttpError(it).fatal) }
        assertTrue(geminiHttpError(429).message.contains("Kontingent"))
        assertTrue(parseGeminiResponse("""{"promptFeedback":{"blockReason":"SAFETY"}}""" ) is ScaleRecognitionResult.Error)
        assertTrue(parseGeminiResponse("not json") is ScaleRecognitionResult.Error)
        assertTrue(parseGeminiResponse("""{"candidates":[{"finishReason":"MAX_TOKENS","content":{"parts":[{"text":"{}"}]}}]}""" ) is ScaleRecognitionResult.Error)
        assertEquals(ScaleRecognitionResult.Recognized(42.0), parseGeminiResponse("""{"candidates":[{"finishReason":"STOP","content":{"parts":[{"thought":true,"text":"reasoning"},{"text":"{\"readable\":true,\"value\":42,\"unit\":\"g\"}"}]}}]}"""))
    }
}

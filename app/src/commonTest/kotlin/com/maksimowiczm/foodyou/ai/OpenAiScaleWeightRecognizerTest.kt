package com.maksimowiczm.foodyou.ai

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlin.test.*
import kotlin.time.TestTimeSource
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*

class OpenAiScaleWeightRecognizerTest {
    private val success = """{"status":"completed","output":[{"type":"message","content":[{"type":"output_text","text":"{\"value\":42}"}]}]}"""

    @Test fun retriesOnlyTemporaryLimitsAndOverload() = runTest {
        for (status in listOf(HttpStatusCode.TooManyRequests, HttpStatusCode.ServiceUnavailable)) {
            var calls = 0
            val time = TestTimeSource()
            val client = HttpClient(MockEngine) { engine { dispatcher = StandardTestDispatcher(testScheduler); addHandler {
                calls++
                if (calls < 3) respond("""{"error":{"type":"rate_limit_error"}}""", status,
                    headersOf(HttpHeaders.RetryAfter, "2")) else respond(success)
            } } }
            try {
                val recognizer = OpenAiScaleWeightRecognizer(OpenAiClient(client, "test"), OpenAiSettings(), time,
                    jitterMillis = { 0 }) { "image" }
                val answer = async { recognizer.recognize("a") }
                runCurrent()
                repeat(2) { time += 2_000.milliseconds; advanceTimeBy(2_000); runCurrent() }
                assertEquals(ScaleRecognitionResult.Recognized(42.0), answer.await())
                assertEquals(3, calls)
            } finally { client.close() }
        }
    }

    @Test fun invalidRetryAfterUsesExponentialDelaysAndUnreadableImagesStayLocal() = runTest {
        val time = TestTimeSource()
        var calls = 0
        val client = HttpClient(MockEngine) { engine {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler {
                calls++
                if (calls < 3) respond("{}", HttpStatusCode.ServiceUnavailable, headersOf(HttpHeaders.RetryAfter, "bad"))
                else respond(success)
            }
        } }
        try {
            val recognizer = OpenAiScaleWeightRecognizer(OpenAiClient(client, "test"), OpenAiSettings(), time,
                jitterMillis = { 0 }) { path -> if (path == "missing") error("missing") else "image" }
            assertFalse(assertIs<ScaleRecognitionResult.Error>(recognizer.recognize("missing")).fatal)
            assertEquals(0, calls)
            val result = async { recognizer.recognize("a") }
            runCurrent()
            assertEquals(1, calls)
            time += 1_000.milliseconds; advanceTimeBy(1_000); runCurrent()
            assertEquals(2, calls)
            time += 2_000.milliseconds; advanceTimeBy(2_000); runCurrent()
            assertEquals(ScaleRecognitionResult.Recognized(42.0), result.await())
            assertEquals(3, calls)
        } finally { client.close() }
    }

    @Test fun quotaUnknownLimitsAndAuthenticationNeverRetry() = runTest {
        for ((status, body) in listOf(
            HttpStatusCode.TooManyRequests to """{"error":{"code":"insufficient_quota","type":"rate_limit_error"}}""",
            HttpStatusCode.TooManyRequests to "{}",
            HttpStatusCode.Unauthorized to "{}",
        )) {
            var calls = 0
            val client = HttpClient(MockEngine) { engine { dispatcher = StandardTestDispatcher(testScheduler); addHandler { calls++; respond(body, status) } } }
            try {
                val result = OpenAiScaleWeightRecognizer(OpenAiClient(client, "test"), OpenAiSettings()) { "image" }.recognize("a")
                assertTrue(assertIs<ScaleRecognitionResult.Error>(result).fatal)
                assertEquals(1, calls)
            } finally { client.close() }
        }
    }

    @Test fun sharedCooldownBlocksNewRequestsAndCancellationInterruptsIt() = runTest {
        var calls = 0
        val client = HttpClient(MockEngine) { engine { dispatcher = StandardTestDispatcher(testScheduler); addHandler {
            calls++
            respond("""{"error":{"code":"slow_down"}}""", HttpStatusCode.TooManyRequests,
                headersOf(HttpHeaders.RetryAfter, "30"))
        } } }
        try {
            val recognizer = OpenAiScaleWeightRecognizer(OpenAiClient(client, "test"), OpenAiSettings(), TestTimeSource(),
                jitterMillis = { 0 }) { "image" }
            val first = launch { recognizer.recognize("a") }
            runCurrent()
            val second = launch { recognizer.recognize("b") }
            runCurrent()
            assertEquals(1, calls)
            first.cancelAndJoin()
            second.cancelAndJoin()
            assertEquals(1, calls)
        } finally { client.close() }
    }

    @Test fun attemptAndDeadlineLimitsAreBounded() = runTest {
        for (header in listOf("0", "301")) {
            var calls = 0
            val client = HttpClient(MockEngine) { engine { dispatcher = StandardTestDispatcher(testScheduler); addHandler {
                calls++
                respond("{}", HttpStatusCode.ServiceUnavailable, headersOf(HttpHeaders.RetryAfter, header))
            } } }
            try {
                val result = OpenAiScaleWeightRecognizer(OpenAiClient(client, "test"), OpenAiSettings(),
                    jitterMillis = { 0 }) { "image" }.recognize("a")
                assertTrue(assertIs<ScaleRecognitionResult.Error>(result).fatal)
                assertEquals(if (header == "0") 3 else 1, calls)
            } finally { client.close() }
        }
        val client = HttpClient(MockEngine) { engine { dispatcher = StandardTestDispatcher(testScheduler); addHandler { awaitCancellation() } } }
        try {
            val result = OpenAiScaleWeightRecognizer(OpenAiClient(client, "test"), OpenAiSettings()) { "image" }.recognize("a")
            assertTrue(assertIs<ScaleRecognitionResult.Error>(result).fatal)
            assertTrue(result.message.contains("Zeitlimit"))
        } finally { client.close() }
    }

    @Test fun parsesRetryAfterSecondsDatesAndInvalidHeaders() {
        assertEquals(2_000L, retryAfterMillis("2", 0))
        assertEquals(1_000L, retryAfterMillis("Thu, 01 Jan 1970 00:00:02 GMT", 1_000))
        assertEquals(0L, retryAfterMillis("Thu, 01 Jan 1970 00:00:02 GMT", 3_000))
        assertNull(retryAfterMillis("bad", 0))
        assertNull(retryAfterMillis("-1", 0))
    }
}

package com.maksimowiczm.foodyou.ai

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.serialization.json.*
import kotlin.test.*

class ClaudeClientTest {
    private fun turn(text: String = "{\"value\":42}", reason: String = "end_turn") = buildJsonObject {
        put("stop_reason", reason)
        putJsonArray("content") { addJsonObject { put("type", "text"); put("text", text) } }
    }.toString()

    @Test fun sendsAnthropicImageAndEffortAndParsesWholeGrams() = runTest(UnconfinedTestDispatcher()) {
        val http = HttpClient(MockEngine) { engine { dispatcher = UnconfinedTestDispatcher(testScheduler); addHandler { request ->
            assertEquals("https://api.anthropic.com/v1/messages", request.url.toString())
            assertEquals("secret", request.headers["x-api-key"])
            assertEquals("2023-06-01", request.headers["anthropic-version"])
            assertNull(request.headers["Authorization"])
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals("low", body["output_config"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
            assertTrue(body.toString().contains("image/jpeg"))
            assertTrue(body.toString().contains("abc"))
            assertNull(body["temperature"])
            respond(turn())
        } } }
        try {
            val result = ClaudeClient(http, "secret").request(ClaudeTask(effort = "low"), SCALE_PROMPT, "abc")
            assertEquals(ScaleRecognitionResult.Recognized(42.0), parseScaleReading(result.csv))
        } finally { http.close() }
    }

    @Test fun resumesPausedTurnWithUnchangedThinkingAndCollectsSources() = runTest(UnconfinedTestDispatcher()) {
        val paused = """{"stop_reason":"pause_turn","content":[{"type":"thinking","thinking":"","signature":"signed"},{"type":"server_tool_use","name":"web_search","id":"s1","input":{"query":"apple"}},{"type":"web_search_tool_result","tool_use_id":"s1","content":[{"type":"web_search_result","title":"Nutrition","url":"https://example.org"}]}]}"""
        var calls = 0
        val http = HttpClient(MockEngine) { engine { dispatcher = UnconfinedTestDispatcher(testScheduler); addHandler { request ->
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertNull(body["output_config"])
            assertEquals("auto", body["tool_choice"]!!.jsonObject["type"]!!.jsonPrimitive.content)
            if (calls++ == 0) respond(paused) else {
                assertEquals(Json.parseToJsonElement(paused).jsonObject["content"], body["messages"]!!.jsonArray[1].jsonObject["content"])
                respond(turn("name,energy,proteins,carbohydrates,fats\nApple,50,1,10,0"))
            }
        } } }
        try {
            val result = ClaudeClient(http, "secret").request(ClaudeTask(), "foods", web = true)
            assertEquals(2, calls)
            assertTrue(result.csv.startsWith("name,"))
            assertEquals(listOf(AiSource("Nutrition", "https://example.org")), result.sources)
        } finally { http.close() }
    }

    @Test fun ignoresThinkingAndCommentaryBeforeSearchAndKeepsCitationsSeparate() {
        val result = parseClaudeTurn("""{"stop_reason":"end_turn","content":[{"type":"text","text":"Searching..."},{"type":"server_tool_use","name":"web_search"},{"type":"web_search_tool_result","content":[]},{"type":"thinking","thinking":"private"},{"type":"text","text":"csv","citations":[{"url":"https://example.org","title":"Food"}]}]}""")
        assertEquals("csv", result.text)
        assertTrue(result.searched)
        assertEquals("Food", result.sources.single().title)
    }

    @Test fun rejectsSearchErrorsEvenWithSuccessfulHttpStatus() {
        assertFails { parseClaudeTurn("""{"stop_reason":"end_turn","content":[{"type":"web_search_tool_result","content":{"type":"web_search_tool_result_error","error_code":"unavailable"}}]}""") }
    }

    @Test fun rejectsMissingSearchAndTruncatedOrRefusedResponses() = runTest(UnconfinedTestDispatcher()) {
        for ((reason, web) in listOf("end_turn" to true, "max_tokens" to false, "refusal" to false)) {
            val http = HttpClient(MockEngine) { engine { dispatcher = UnconfinedTestDispatcher(testScheduler); addHandler { respond(turn(reason = reason)) } } }
            try { assertFailsWith<IllegalStateException> { ClaudeClient(http, "secret").request(ClaudeTask(), "test", web = web) } }
            finally { http.close() }
        }
    }

    @Test fun continuationAndTimeLimitsAreBounded() = runTest(UnconfinedTestDispatcher()) {
        var calls = 0
        val http = HttpClient(MockEngine) { engine { dispatcher = UnconfinedTestDispatcher(testScheduler); addHandler { calls++; respond(turn(reason = "pause_turn")) } } }
        try { assertFailsWith<IllegalStateException> { ClaudeClient(http, "secret").request(ClaudeTask(), "test", web = true) }; assertEquals(4, calls) }
        finally { http.close() }
        val slow = HttpClient(MockEngine) { engine { dispatcher = UnconfinedTestDispatcher(testScheduler); addHandler { delay(310_000); respond(turn()) } } }
        try { assertFailsWith<IllegalStateException> { ClaudeClient(slow, "secret").request(ClaudeTask(), "test") } }
        finally { slow.close() }
    }

    @Test fun photoRetriesOnlyTemporaryLimits() = runTest(UnconfinedTestDispatcher()) {
        var calls = 0
        val http = HttpClient(MockEngine) { engine { dispatcher = UnconfinedTestDispatcher(testScheduler); addHandler {
            if (calls++ == 0) respond("private error body", HttpStatusCode.TooManyRequests, headersOf("Retry-After", "0")) else respond(turn())
        } } }
        try {
            val reader = ClaudeScaleWeightRecognizer(ClaudeClient(http, "secret"), ClaudeSettings(), jitterMillis = { 0 }) { "abc" }
            assertEquals(4, reader.maxConcurrentPhotos)
            assertEquals(ScaleRecognitionResult.Recognized(42.0), reader.recognize("photo.jpg"))
            assertEquals(2, calls)
        } finally { http.close() }
    }

    @Test fun permanentHttpFailureDoesNotRetryOrExposeResponseBody() = runTest(UnconfinedTestDispatcher()) {
        var calls = 0
        val http = HttpClient(MockEngine) { engine { dispatcher = UnconfinedTestDispatcher(testScheduler); addHandler { calls++; respond("sensitive provider response", HttpStatusCode.Unauthorized) } } }
        try {
            val reader = ClaudeScaleWeightRecognizer(ClaudeClient(http, "secret"), ClaudeSettings()) { "abc" }
            val result = assertIs<ScaleRecognitionResult.Error>(reader.recognize("photo.jpg"))
            assertTrue(result.fatal)
            assertFalse(result.message.contains("sensitive"))
            assertEquals(1, calls)
        } finally { http.close() }
    }

    @Test fun effortChoicesFollowModelAndUnknownModelsReset() {
        assertEquals(listOf("low", "medium", "high", "xhigh", "max"), claudeSupportedEfforts("claude-sonnet-5-5"))
        assertEquals("high", ClaudeTask(effort = "high").withModel("claude-opus-5-5").effort)
        assertNull(ClaudeTask(effort = "high").withModel("custom").effort)
    }
}

package com.maksimowiczm.foodyou.ai

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlin.test.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*

class OpenAiClientTest {
    @Test fun incompatibleEffortResetsAndCompatibleEffortSurvives() {
        assertNull(OpenAiTask("gpt-6-luna", "none").withModel("gpt-6.1-sol").effort)
        assertEquals("high", OpenAiTask("gpt-6-luna", "high").withModel("gpt-6-astra").effort)
        assertNull(OpenAiTask("gpt-6-luna", "low").withModel("custom").effort)
    }
    @Test fun sendsImageAndSelectedEffortWithoutStorage() = runTest {
        val client = HttpClient(MockEngine { request ->
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals("low", body["reasoning"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
            assertEquals(false, body["store"]!!.jsonPrimitive.boolean)
            assertTrue(body.toString().contains("data:image/jpeg;base64,abc"))
            assertEquals("Bearer test", request.headers["Authorization"])
            respond(complete("{\\\"value\\\":42}"), HttpStatusCode.OK)
        })
        try {
            val result = OpenAiClient(client, "test").request(OpenAiTask("gpt-6-luna", "low"), "weight", "abc")
            assertEquals(ScaleRecognitionResult.Recognized(42.0), parseScaleReading(result.csv))
        }
        finally { client.close() }
    }
    @Test fun requiresWebAndOmitsDefaultEffort() = runTest {
        val client = HttpClient(MockEngine { request ->
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertNull(body["reasoning"])
            assertEquals("required", body["tool_choice"]!!.jsonPrimitive.content)
            assertTrue(body.toString().contains("web_search"))
            respond("""{"status":"completed","output":[{"type":"web_search_call","status":"completed"},{"type":"message","content":[{"type":"output_text","text":"csv"}]}]}""", HttpStatusCode.OK)
        })
        try { assertEquals("csv", OpenAiClient(client, "test").request(OpenAiTask("custom"), "foods", web = true).csv) }
        finally { client.close() }
    }
    @Test fun incompleteMissingSearchAndRefusalAreRejected() {
        assertFails { parseOpenAiResponse("""{"status":"incomplete","output":[]}""") }
        assertFails { parseOpenAiResponse(complete("csv"), true) }
        assertFails { parseOpenAiResponse("""{"status":"completed","output":[{"type":"message","content":[{"type":"refusal"}]}]}""") }
    }
    @Test fun extractsOnlyAnnotatedCitations() {
        val result = parseOpenAiResponse("""{"status":"completed","output":[{"type":"message","content":[{"type":"output_text","text":"abc[1]def","annotations":[{"type":"url_citation","start_index":3,"end_index":6,"url":"https://example.org","title":"Source"}]}]}]}""")
        assertEquals("abcdef", result.csv)
        assertEquals(listOf(AiSource("Source", "https://example.org")), result.sources)
    }
    private fun complete(text: String) = """{"status":"completed","output":[{"type":"message","content":[{"type":"output_text","text":"$text"}]}]}"""
}

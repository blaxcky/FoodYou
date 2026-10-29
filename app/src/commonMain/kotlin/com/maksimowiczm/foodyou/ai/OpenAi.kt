package com.maksimowiczm.foodyou.ai

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*

data class OpenAiTask(val model: String, val effort: String? = null)
data class OpenAiSettings(
    val photo: OpenAiTask = OpenAiTask("gpt-6-luna", "low"),
    val nutrition: OpenAiTask = OpenAiTask("gpt-6.1-sol", "medium"),
    val hasApiKey: Boolean = false,
)
data class AiSource(val title: String, val url: String)
data class NutritionEstimate(val csv: String, val sources: List<AiSource>)

val openAiModels = listOf("gpt-6-luna", "gpt-6.1-sol", "gpt-6-astra")
fun supportedEfforts(model: String): List<String>? = when (model) {
    "gpt-6-luna" -> listOf("none", "low", "medium", "high", "xhigh", "max")
    "gpt-6.1-sol", "gpt-6-astra" -> listOf("low", "medium", "high", "xhigh", "max")
    else -> null
}
fun OpenAiTask.withModel(model: String): OpenAiTask = copy(model = model,
    effort = effort?.takeIf { supportedEfforts(model)?.contains(it) == true })

/** Dedicated caller: no authentication or body logging from the shared app client. */
internal class OpenAiClient(private val client: HttpClient, private val key: String) {
    suspend fun request(task: OpenAiTask, prompt: String, imageBase64: String? = null, web: Boolean = false): NutritionEstimate {
        require(key.isNotBlank()) { "OpenAI: API-Key fehlt / API key missing." }
        require(task.model.matches(Regex("[A-Za-z0-9._:/-]+"))) { "OpenAI: ungültiges Modell / invalid model." }
        val body = buildJsonObject {
            put("model", task.model)
            put("store", false)
            put("max_output_tokens", if (web) 32768 else 16384)
            task.effort?.let { effort -> putJsonObject("reasoning") { put("effort", effort) } }
            putJsonArray("input") { addJsonObject {
                put("role", "user")
                putJsonArray("content") {
                    addJsonObject { put("type", "input_text"); put("text", prompt) }
                    imageBase64?.let { addJsonObject {
                        put("type", "input_image"); put("image_url", "data:image/jpeg;base64,$it"); put("detail", "high")
                    } }
                }
            } }
            if (web) {
                putJsonArray("tools") { addJsonObject { put("type", "web_search") } }
                put("tool_choice", "required")
                putJsonArray("include") { add("web_search_call.action.sources") }
            }
        }
        try {
            val response = client.post("https://api.openai.com/v1/responses") {
                bearerAuth(key)
                contentType(ContentType.Application.Json)
                timeout { requestTimeoutMillis = 300_000; socketTimeoutMillis = 300_000 }
                setBody(body.toString())
            }
            check(response.status.value in 200..299) { when (response.status.value) {
                401, 403 -> "OpenAI: API-Key oder Modellzugriff prüfen / check API key and model access."
                429 -> "OpenAI: Kontingent erschöpft / quota or rate limit exceeded."
                400, 404 -> "OpenAI: Modell und Denkaufwand prüfen / check model and reasoning effort."
                else -> "OpenAI: HTTP ${response.status.value}"
            } }
            return parseOpenAiResponse(response.bodyAsText(), web)
        } catch (e: CancellationException) { throw e
        } catch (e: IllegalStateException) { throw e
        } catch (_: Exception) { error("OpenAI nicht erreichbar oder Zeitlimit überschritten / unavailable or timed out.") }
    }
}

internal fun parseOpenAiResponse(body: String, requireWeb: Boolean = false): NutritionEstimate {
    val root = Json.parseToJsonElement(body).jsonObject
    check(root["status"]?.jsonPrimitive?.content == "completed") { "OpenAI: unvollständige Antwort / incomplete response." }
    val output = root["output"]?.jsonArray.orEmpty()
    check(!requireWeb || output.any { it.jsonObject["type"]?.jsonPrimitive?.content == "web_search_call" &&
        it.jsonObject["status"]?.jsonPrimitive?.content == "completed" }) { "OpenAI: Websuche fehlgeschlagen / web search failed." }
    val sources = mutableListOf<AiSource>()
    val text = buildString {
        for (item in output) {
            val obj = item.jsonObject
            if (obj["type"]?.jsonPrimitive?.content == "web_search_call") {
                obj["action"]?.jsonObject?.get("sources")?.jsonArray?.forEach {
                    val source = it.jsonObject
                    val url = source["url"]?.jsonPrimitive?.content.orEmpty()
                    if (url.startsWith("https://") || url.startsWith("http://"))
                        sources += AiSource(source["title"]?.jsonPrimitive?.content ?: url, url)
                }
            }
            if (obj["type"]?.jsonPrimitive?.content != "message") continue
            for (part in obj["content"]?.jsonArray.orEmpty()) {
                val p = part.jsonObject
                check(p["type"]?.jsonPrimitive?.content != "refusal") { "OpenAI: Anfrage abgelehnt / request refused." }
                if (p["type"]?.jsonPrimitive?.content != "output_text") continue
                var value = p["text"]!!.jsonPrimitive.content
                val spans = mutableListOf<Pair<Int, Int>>()
                for (a in p["annotations"]?.jsonArray.orEmpty()) {
                    val annotation = a.jsonObject
                    if (annotation["type"]?.jsonPrimitive?.content != "url_citation") continue
                    val url = annotation["url"]?.jsonPrimitive?.content.orEmpty()
                    if (url.startsWith("https://") || url.startsWith("http://"))
                        sources += AiSource(annotation["title"]?.jsonPrimitive?.content ?: url, url)
                    val start = annotation["start_index"]?.jsonPrimitive?.intOrNull
                    val end = annotation["end_index"]?.jsonPrimitive?.intOrNull
                    if (start != null && end != null && start in 0..value.length && end in start..value.length) spans += start to end
                }
                for ((start, end) in spans.distinct().sortedByDescending { it.first }) value = value.removeRange(start, end)
                append(value)
            }
        }
    }.trim()
    check(text.isNotBlank()) { "OpenAI: leere Antwort / empty response." }
    return NutritionEstimate(text, sources.distinctBy { it.url })
}

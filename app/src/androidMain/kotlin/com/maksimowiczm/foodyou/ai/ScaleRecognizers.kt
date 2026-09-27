package com.maksimowiczm.foodyou.ai

import android.util.Base64
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import java.io.File
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

internal class GeminiScaleWeightRecognizer(
    private val client: HttpClient,
    private val key: String,
    private val model: String,
    private val photos: File,
) : ScaleWeightRecognizer {
    override suspend fun recognize(photoPath: String): ScaleRecognitionResult {
        val bytes = try { withContext(Dispatchers.IO) { decodeScalePhoto(photos, photoPath) } }
        catch (_: java.io.IOException) { return ScaleRecognitionResult.Error("Foto konnte nicht gelesen werden.") }
        catch (_: IllegalArgumentException) { return ScaleRecognitionResult.Error("Foto konnte nicht gelesen werden.") }
        return request(bytes)
    }

    suspend fun testConnection(): String = when (val result = request(null)) {
        is ScaleRecognitionResult.Error -> result.message
        else -> "Verbindung erfolgreich. Modell antwortet."
    }

    private suspend fun request(photo: ByteArray?): ScaleRecognitionResult {
        if (key.isBlank()) return ScaleRecognitionResult.Error("Bitte einen Google-AI-Studio-API-Key hinterlegen.", true)
        if (!Regex("[A-Za-z0-9._-]+").matches(model)) return ScaleRecognitionResult.Error("Ungültiger Modellname.", true)
        val body = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("parts") {
                        if (photo != null) addJsonObject {
                            putJsonObject("inline_data") {
                                put("mime_type", "image/jpeg")
                                put("data", Base64.encodeToString(photo, Base64.NO_WRAP))
                            }
                        }
                        addJsonObject { put("text", if (photo == null) "Return exactly {\"readable\":false}" else SCALE_PROMPT) }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("responseMimeType", "application/json")
                put("maxOutputTokens", 256)
                put("temperature", 0)
            }
        }
        return try {
            val response = client.post("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent") {
                header("x-goog-api-key", key)
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
            if (response.status.value !in 200..299) return geminiHttpError(response.status.value)
            parseGeminiResponse(response.bodyAsText())
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) {
            ScaleRecognitionResult.Error("Google AI Studio ist nicht erreichbar. Verbindung prüfen.", true)
        }
    }

    override suspend fun close() = Unit
}

internal fun geminiHttpError(code: Int) = ScaleRecognitionResult.Error(when (code) {
    400 -> "Google hat die Anfrage abgelehnt. API-Key und Modell prüfen."
    401, 403 -> "API-Key ungültig oder Zugriff auf das Modell verweigert."
    404 -> "Das eingestellte Gemini-Modell ist nicht verfügbar."
    429 -> "Google-AI-Studio-Kontingent erschöpft. Bitte später erneut versuchen."
    else -> "Google AI Studio meldet einen Fehler (HTTP $code)."
}, fatal = true)

internal fun parseGeminiResponse(body: String): ScaleRecognitionResult = try {
    val obj = Json.parseToJsonElement(body).jsonObject
    val candidate = obj["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
    val parts = candidate?.get("content")?.jsonObject?.get("parts")?.jsonArray
    val text = parts?.mapNotNull { part ->
        val p = part.jsonObject
        if ((p["thought"] as? JsonPrimitive)?.booleanOrNull == true) null
        else (p["text"] as? JsonPrimitive)?.contentOrNull
    }?.joinToString("")
    if (candidate?.get("finishReason")?.jsonPrimitive?.content == "MAX_TOKENS")
        parseScaleReading(text.orEmpty(), truncated = true)
    else if (candidate?.get("finishReason")?.jsonPrimitive?.content != "STOP" || text.isNullOrBlank())
        ScaleRecognitionResult.Error("Google lieferte keine vollständige Antwort.")
    else parseScaleReading(text)
} catch (_: Exception) { ScaleRecognitionResult.Error("Google lieferte eine ungültige Antwort.") }

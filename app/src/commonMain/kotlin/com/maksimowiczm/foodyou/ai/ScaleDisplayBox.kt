package com.maksimowiczm.foodyou.ai

import kotlinx.serialization.json.*

/** Coordinates refer to the EXIF-oriented image, normalized to a 1000 × 1000 grid. */
internal data class ScaleDisplayBox(val top: Int, val left: Int, val bottom: Int, val right: Int)

internal fun parseScaleDisplayBox(text: String): ScaleDisplayBox? {
    val clean = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    val json = try { Json.parseToJsonElement(clean) } catch (_: Exception) { return null }
    val obj = when (json) {
        is JsonObject -> json
        is JsonArray -> json.filterIsInstance<JsonObject>().firstOrNull { "box_2d" in it }
        else -> null
    } ?: return null
    if (obj["value"] == JsonNull) return null
    val coordinates = obj["box_2d"] as? JsonArray ?: return null
    if (coordinates.size != 4) return null
    val values = coordinates.map { coordinate ->
        val primitive = coordinate as? JsonPrimitive ?: return null
        if (primitive.isString) return null
        val value = primitive.doubleOrNull ?: return null
        if (!value.isFinite() || value !in 0.0..1000.0) return null
        value.toInt()
    }
    val (top, left, bottom, right) = values
    if (top >= bottom || left >= right) return null
    val area = (bottom - top).toDouble() * (right - left) / 1_000_000
    if (area < 0.0005 || area > 0.6) return null
    return ScaleDisplayBox(top, left, bottom, right)
}

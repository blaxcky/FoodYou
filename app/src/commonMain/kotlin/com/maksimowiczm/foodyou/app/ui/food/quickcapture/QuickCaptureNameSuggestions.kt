package com.maksimowiczm.foodyou.app.ui.food.quickcapture

import com.maksimowiczm.foodyou.food.domain.entity.QuickCaptureFoodName
import com.maksimowiczm.foodyou.food.domain.entity.normalizeQuickCaptureFoodName

internal fun quickCaptureNameSuggestions(
    query: String,
    names: List<QuickCaptureFoodName>,
): List<QuickCaptureFoodName> {
    val normalizedQuery = normalizeQuickCaptureNameSearch(query)
    if (normalizedQuery.isBlank()) return emptyList()

    return names.asSequence()
        .filter { normalizeQuickCaptureNameSearch(it.name).contains(normalizedQuery) }
        .take(8)
        .toList()
}

private fun normalizeQuickCaptureNameSearch(input: String): String =
    normalizeQuickCaptureFoodName(input)
        .replace('ä', 'a')
        .replace('ö', 'o')
        .replace('ü', 'u')
        .replace("\u0308", "")
        .replace("ß", "ss")

package com.maksimowiczm.foodyou.app.ui.food.quickcapture

import com.maksimowiczm.foodyou.ai.AiSource
import com.maksimowiczm.foodyou.app.ui.food.diary.quickadd.QuickAddCsvData
import com.maksimowiczm.foodyou.app.ui.food.diary.quickadd.QuickAddCsvError

/**
 * Readable form of the CSV in the import dialog.
 *
 * @property csv The text this preview was parsed from; the dialog ignores a preview of older text.
 * @property rows The parsed rows, or null when the text is not a valid quick-add table.
 * @property expectedRowCount Number of prompt items the CSV must answer, when known.
 * @property mealName Meal the rows would be added to today.
 * @property parseError Why the text could not be read, when [rows] is null.
 */
internal data class QuickCaptureCsvPreview(
    val csv: String,
    val rows: List<QuickAddCsvData>?,
    val expectedRowCount: Int?,
    val mealName: String?,
    val parseError: QuickAddCsvError? = null,
) {
    val missingRowCount: Int
        get() = if (rows == null || expectedRowCount == null) 0 else (expectedRowCount - rows.size).coerceAtLeast(0)

    val hasRowCountMismatch: Boolean
        get() = rows != null && expectedRowCount != null && rows.size != expectedRowCount
}

/** A CSV row name split into the food name and the weight the prompt put in front of it. */
internal data class QuickCaptureCsvRowName(val name: String, val weightInGrams: Double?)

private val LeadingWeight = Regex("""^\s*(\d+(?:[.,]\d+)?)\s*g\s+(.+)$""", RegexOption.IGNORE_CASE)

/** The prompt sends items as "131g Krapfen"; the preview shows the weight apart from the name. */
internal fun String.toQuickCaptureCsvRowName(): QuickCaptureCsvRowName {
    val match = LeadingWeight.matchEntire(this) ?: return QuickCaptureCsvRowName(trim(), null)
    val weight = match.groupValues[1].replace(',', '.').toDoubleOrNull()
    val name = match.groupValues[2].trim()
    return if (weight == null || name.isEmpty()) QuickCaptureCsvRowName(trim(), null)
    else QuickCaptureCsvRowName(name, weight)
}

/** Short label for a source link: its host without "www.", or the URL when it has no host. */
internal val AiSource.host: String
    get() {
        val host = url.substringAfter("://", "").substringBefore('/').substringBefore('?')
            .substringBefore('#').substringAfterLast('@').substringBefore(':')
        return host.removePrefix("www.").ifBlank { url }
    }

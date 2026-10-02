package com.maksimowiczm.foodyou.common.compose.utility

expect fun Float.formatClipZeros(format: String = "%.2f"): String

expect fun Double.formatClipZeros(format: String = "%.2f"): String

/**
 * Formats this number for display in the current locale, rounded half up to at most
 * [maxFractionDigits] fraction digits and without trailing zeros.
 *
 * For example, 8.48 becomes "8.5" in English and "8,5" in German.
 */
expect fun Double.formatLocalized(maxFractionDigits: Int = 1): String

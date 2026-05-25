package com.maksimowiczm.foodyou.food.infrastructure.fddb

import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.food.domain.entity.FddbDiaryEntry
import kotlinx.datetime.LocalDate

class FddbDiaryParser {
    fun parse(html: String, referenceDate: LocalDate): List<FddbDiaryEntry> {
        val entries = mutableListOf<FddbDiaryEntry>()
        var currentDate: LocalDate? = null
        var currentMeal: String? = null

        for (match in TokenRegex.findAll(html)) {
            val token = match.value
            when {
                NotepadDateClassRegex.containsMatchIn(token) -> {
                    currentDate =
                        H3Regex.find(token)
                            ?.groupValues
                            ?.get(1)
                            ?.stripTags()
                            ?.decodeFddbHtml()
                            ?.parseFddbDiaryDate(referenceDate)
                }

                token.startsWith("<h4", ignoreCase = true) -> {
                    currentMeal = token.stripTags().decodeFddbHtml().trim().takeIf { it.isNotBlank() }
                }

                token.startsWith("<tr", ignoreCase = true) -> {
                    val date = currentDate ?: continue
                    val meal = currentMeal ?: continue
                    val parsed = token.parseDiaryRow(date = date, meal = meal) ?: continue
                    entries += parsed
                }
            }
        }

        return entries
    }
}

private fun String.parseDiaryRow(date: LocalDate, meal: String): FddbDiaryEntry? {
    val linkMatch = ProductLinkRegex.find(this) ?: return null
    val productUrl = linkMatch.groupValues[1].toAbsoluteFddbUrl()
    val linkText = linkMatch.groupValues[2].stripTags().decodeFddbHtml().replace(WhitespaceRegex, " ").trim()
    val amountMatch = AmountRegex.find(linkText) ?: return null
    val value = amountMatch.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
    val unit = amountMatch.groupValues[2].lowercase()
    val measurement =
        when (unit) {
            "g" -> Measurement.Gram(value)
            "ml" -> Measurement.Milliliter(value)
            else -> return null
        }
    val entryId =
        TrIdRegex.find(this)?.groupValues?.get(1)
            ?: QueryIdRegex.find(this)?.groupValues?.get(1)
            ?: return null

    return FddbDiaryEntry(
        entryId = entryId,
        date = date,
        mealName = meal,
        productName = linkText.removePrefix(amountMatch.value).trim().ifBlank { linkText },
        productUrl = productUrl,
        measurement = measurement,
    )
}

private fun String.parseFddbDiaryDate(referenceDate: LocalDate): LocalDate? {
    val match = DateRegex.find(this) ?: return null
    val day = match.groupValues[1].toIntOrNull() ?: return null
    val month = GermanMonths[match.groupValues[2].trimEnd('.').lowercase()] ?: return null
    val currentYearDate = LocalDate(referenceDate.year, month, day)
    return if (currentYearDate > referenceDate) {
        LocalDate(referenceDate.year - 1, month, day)
    } else {
        currentYearDate
    }
}

private fun String.toAbsoluteFddbUrl(): String =
    when {
        startsWith("https://", ignoreCase = true) -> this
        startsWith("//") -> "https:$this"
        startsWith("/") -> "https://fddb.info$this"
        else -> "https://fddb.info/$this"
    }

private fun String.stripTags(): String = replace(Regex("""<[^>]+>"""), " ")

private fun String.decodeFddbHtml(): String =
    replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#34;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&Auml;", "Ä")
        .replace("&auml;", "ä")
        .replace("&Ouml;", "Ö")
        .replace("&ouml;", "ö")
        .replace("&Uuml;", "Ü")
        .replace("&uuml;", "ü")
        .replace("&szlig;", "ß")

private val TokenRegex =
    Regex(
        """<td\b(?=[^>]*\bclass\s*=\s*(?:"[^"]*\bnotepaddate\b[^"]*"|'[^']*\bnotepaddate\b[^']*'|[^\s>]*\bnotepaddate\b[^\s>]*))[^>]*>.*?</td>|<h4\b[^>]*>.*?</h4>|<tr\b[^>]*\bid\s*=\s*["']np[^"']*["'][^>]*>.*?</tr>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
private val NotepadDateClassRegex =
    Regex(
        """\bclass\s*=\s*(?:"[^"]*\bnotepaddate\b[^"]*"|'[^']*\bnotepaddate\b[^']*'|[^\s>]*\bnotepaddate\b[^\s>]*)""",
        RegexOption.IGNORE_CASE,
    )
private val H3Regex = Regex("""<h3\b[^>]*>(.*?)</h3>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val ProductLinkRegex =
    Regex(
        """<a\b[^>]*href\s*=\s*["']([^"']*/db/(?:de|i18n)/lebensmittel/[^"']*/index\.html[^"']*)["'][^>]*>(.*?)</a>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
private val TrIdRegex = Regex("""<tr\b[^>]*\bid\s*=\s*["']np(\d+)["']""", RegexOption.IGNORE_CASE)
private val QueryIdRegex = Regex("""[?&]q=(\d+)""", RegexOption.IGNORE_CASE)
private val AmountRegex = Regex("""^\s*([-+]?\d+(?:[,.]\d+)?)\s*(g|ml)\b""", RegexOption.IGNORE_CASE)
private val DateRegex =
    Regex("""(?:^|\s)(\d{1,2})\.\s*([A-Za-zÄÖÜäöüß]+)\.?""", RegexOption.IGNORE_CASE)
private val WhitespaceRegex = Regex("""\s+""")
private val GermanMonths =
    mapOf(
        "januar" to 1,
        "jan" to 1,
        "februar" to 2,
        "feb" to 2,
        "märz" to 3,
        "maerz" to 3,
        "mrz" to 3,
        "april" to 4,
        "apr" to 4,
        "mai" to 5,
        "juni" to 6,
        "jun" to 6,
        "juli" to 7,
        "jul" to 7,
        "august" to 8,
        "aug" to 8,
        "september" to 9,
        "sep" to 9,
        "oktober" to 10,
        "okt" to 10,
        "november" to 11,
        "nov" to 11,
        "dezember" to 12,
        "dez" to 12,
    )

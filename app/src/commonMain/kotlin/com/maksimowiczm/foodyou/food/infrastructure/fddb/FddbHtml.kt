package com.maksimowiczm.foodyou.food.infrastructure.fddb

/**
 * Decodes HTML character references in text scraped from FDDB.
 *
 * Handles decimal and hexadecimal numeric references (including zero-padded forms such as
 * `&#039;`) and a table of common named entities. Unknown or invalid references are kept as they
 * are. FDDB sometimes escapes already escaped text (`&amp;#039;`), so decoding is repeated until
 * the text no longer changes.
 */
internal fun String.decodeFddbHtmlEntities(): String {
    var current = this
    repeat(MAX_DECODE_PASSES) {
        if ('&' !in current) return current
        val decoded = EntityRegex.replace(current) { match -> match.decodeEntity() ?: match.value }
        if (decoded == current) return current
        current = decoded
    }
    return current
}

private fun MatchResult.decodeEntity(): String? {
    val body = groupValues[1]
    if (!body.startsWith('#')) return NamedEntities[body]

    val codePoint =
        if (body.length > 1 && (body[1] == 'x' || body[1] == 'X')) {
            body.substring(2).toIntOrNull(16)
        } else {
            body.substring(1).toIntOrNull()
        } ?: return null

    return codePoint.toCodePointString()
}

private fun Int.toCodePointString(): String? =
    when {
        this <= 0 || this > 0x10FFFF -> null
        this in 0xD800..0xDFFF -> null
        this < 0x10000 -> this.toChar().toString()
        else -> {
            val offset = this - 0x10000
            charArrayOf(
                    (0xD800 + (offset shr 10)).toChar(),
                    (0xDC00 + (offset and 0x3FF)).toChar(),
                )
                .concatToString()
        }
    }

private const val MAX_DECODE_PASSES = 3

private val EntityRegex = Regex("""&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[A-Za-z][A-Za-z0-9]{1,31});""")

private val NamedEntities =
    mapOf(
        "nbsp" to " ",
        "amp" to "&",
        "quot" to "\"",
        "apos" to "'",
        "lt" to "<",
        "gt" to ">",
        "micro" to "µ",
        "Auml" to "Ä",
        "auml" to "ä",
        "Ouml" to "Ö",
        "ouml" to "ö",
        "Uuml" to "Ü",
        "uuml" to "ü",
        "szlig" to "ß",
        "Eacute" to "É",
        "eacute" to "é",
        "Egrave" to "È",
        "egrave" to "è",
        "ecirc" to "ê",
        "Agrave" to "À",
        "agrave" to "à",
        "Aacute" to "Á",
        "aacute" to "á",
        "acirc" to "â",
        "iacute" to "í",
        "oacute" to "ó",
        "uacute" to "ú",
        "Ccedil" to "Ç",
        "ccedil" to "ç",
        "Ntilde" to "Ñ",
        "ntilde" to "ñ",
        "Oslash" to "Ø",
        "oslash" to "ø",
        "Aring" to "Å",
        "aring" to "å",
        "euro" to "€",
        "ndash" to "–",
        "mdash" to "—",
        "hellip" to "…",
        "laquo" to "«",
        "raquo" to "»",
        "bdquo" to "„",
        "ldquo" to "“",
        "rdquo" to "”",
        "lsquo" to "‘",
        "rsquo" to "’",
        "sbquo" to "‚",
        "reg" to "®",
        "trade" to "™",
        "copy" to "©",
        "deg" to "°",
        "frac12" to "½",
        "frac14" to "¼",
        "frac34" to "¾",
        "middot" to "·",
        "times" to "×",
        "shy" to "",
    )

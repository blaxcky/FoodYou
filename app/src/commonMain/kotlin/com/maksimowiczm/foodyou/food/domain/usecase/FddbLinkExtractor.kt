package com.maksimowiczm.foodyou.food.domain.usecase

internal object FddbLinkExtractor {
    fun extractLinks(text: String): List<String> =
        FddbUrlRegex.findAll(text)
            .map { it.value.trimEnd('.', ',', ';', ')', ']') }
            .distinct()
            .toList()

    fun normalizeSingleLink(text: String): String? {
        val trimmed = text.trim()
        val link = extractLinks(trimmed).singleOrNull() ?: return null
        if (link != trimmed) return null
        return link.replace(FddbHostRegex, "https://fddb.info")
    }

    fun equivalentLinks(normalizedLink: String): List<String> {
        val path = normalizedLink.removePrefix("https://fddb.info")
        return listOf(
            normalizedLink,
            "https://www.fddb.info$path",
            "http://fddb.info$path",
            "http://www.fddb.info$path",
        )
    }

    private val FddbUrlRegex =
        Regex(
            """https?://(?:www\.)?fddb\.info/db/(?:de/lebensmittel|en/food)/[^\s<>"']+/index\.html"""
        )

    private val FddbHostRegex = Regex("""^https?://(?:www\.)?fddb\.info""")
}

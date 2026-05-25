package com.maksimowiczm.foodyou.food.domain.usecase

internal object FddbLinkExtractor {
    fun extractLinks(text: String): List<String> =
        FddbUrlRegex.findAll(text)
            .map { it.value.trimEnd('.', ',', ';', ')', ']') }
            .distinct()
            .toList()

    private val FddbUrlRegex =
        Regex(
            """https?://(?:www\.)?fddb\.info/db/(?:de/lebensmittel|en/food)/[^\s<>"']+/index\.html"""
        )
}

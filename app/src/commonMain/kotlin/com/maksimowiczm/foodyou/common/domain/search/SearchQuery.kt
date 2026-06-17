package com.maksimowiczm.foodyou.common.domain.search

sealed interface SearchQuery {
    val query: String?

    data object Blank : SearchQuery {
        override val query: String? = null
    }

    sealed interface NotBlank : SearchQuery {
        override val query: String
    }

    data class Barcode(override val query: String) : NotBlank

    data class Text(override val query: String) : NotBlank
}

fun searchQuery(query: String?): SearchQuery {
    val normalizedQuery = query?.trim()?.replace(Regex("\\s+"), " ")

    return when {
        normalizedQuery.isNullOrBlank() -> SearchQuery.Blank
        normalizedQuery.all(Char::isDigit) -> SearchQuery.Barcode(normalizedQuery)
        else -> SearchQuery.Text(normalizedQuery)
    }
}

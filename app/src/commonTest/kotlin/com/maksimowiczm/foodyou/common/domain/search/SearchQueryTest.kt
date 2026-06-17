package com.maksimowiczm.foodyou.common.domain.search

import kotlin.test.Test
import kotlin.test.assertEquals

class SearchQueryTest {
    @Test
    fun textQuery() {
        assertEquals(SearchQuery.Text("suppe"), searchQuery("suppe"))
    }

    @Test
    fun trimsTrailingWhitespace() {
        assertEquals(SearchQuery.Text("suppe"), searchQuery("suppe "))
    }

    @Test
    fun trimsLeadingWhitespace() {
        assertEquals(SearchQuery.Text("suppe"), searchQuery("  suppe"))
    }

    @Test
    fun collapsesInnerWhitespace() {
        assertEquals(SearchQuery.Text("tomaten suppe"), searchQuery("tomaten   suppe"))
    }

    @Test
    fun blankWhitespaceQuery() {
        assertEquals(SearchQuery.Blank, searchQuery("   "))
    }

    @Test
    fun trimsBarcodeQuery() {
        assertEquals(SearchQuery.Barcode("12345"), searchQuery(" 12345 "))
    }
}

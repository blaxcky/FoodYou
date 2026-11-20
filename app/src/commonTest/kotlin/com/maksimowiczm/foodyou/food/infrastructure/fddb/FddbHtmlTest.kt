package com.maksimowiczm.foodyou.food.infrastructure.fddb

import kotlin.test.Test
import kotlin.test.assertEquals

class FddbHtmlTest {
    @Test
    fun decodesZeroPaddedNumericReference() {
        assertEquals("M&M's", "M&amp;M&#039;s".decodeFddbHtmlEntities())
    }

    @Test
    fun decodesHexadecimalAndSupplementaryReferences() {
        assertEquals("Ben's – 🍫", "Ben&#x27;s &#X2013; &#x1F36B;".decodeFddbHtmlEntities())
    }

    @Test
    fun decodesDoubleEscapedText() {
        assertEquals("M&M's", "M&amp;M&amp;#039;s".decodeFddbHtmlEntities())
    }

    @Test
    fun decodesNamedEntities() {
        assertEquals(
            "Käse & Crème brulée „Bio“ – 5 €",
            "K&auml;se &amp; Cr&egrave;me brul&eacute;e &bdquo;Bio&ldquo; &ndash; 5&nbsp;&euro;"
                .decodeFddbHtmlEntities(),
        )
    }

    @Test
    fun keepsUnknownAndInvalidReferences() {
        assertEquals("A &foo; B &#0; C &#xD800; D & E", "A &foo; B &#0; C &#xD800; D & E".decodeFddbHtmlEntities())
    }
}

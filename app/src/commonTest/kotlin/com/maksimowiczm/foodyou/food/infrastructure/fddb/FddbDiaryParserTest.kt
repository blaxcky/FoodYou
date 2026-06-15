package com.maksimowiczm.foodyou.food.infrastructure.fddb

import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate

class FddbDiaryParserTest {
    private val parser = FddbDiaryParser()

    @Test
    fun parsesDateMealEntryIdProductLinkAndMeasurement() {
        val entries = parser.parse(Fixture, LocalDate(2026, 5, 25))

        val entry = entries.single { it.entryId == "2131111111" }
        assertEquals(LocalDate(2026, 5, 25), entry.date)
        assertEquals("Morgens", entry.mealName)
        assertEquals("https://fddb.info/db/de/lebensmittel/example_food/index.html", entry.productUrl)
        assertEquals(Measurement.Gram(310.0), entry.measurement)
    }

    @Test
    fun ignoresActivitiesRowsRowsWithoutProductLinksAndDummyProducts() {
        val entries = parser.parse(Fixture, LocalDate(2026, 5, 25))

        assertEquals(listOf("2131111111", "2132222222", "2133333333"), entries.map { it.entryId })
        assertEquals(Measurement.Milliliter(5.0), entries[1].measurement)
    }

    @Test
    fun resolvesPreviousYearForFutureDayWithoutYear() {
        val html =
            """
            <td class="notepaddate"><h3>Mittwoch 31. Dezember</h3></td>
            <h4>Abends</h4>
            <tr id="np1"><td><a href="/db/de/lebensmittel/foo/index.html">1 g Food</a></td></tr>
            """
                .trimIndent()

        val entry = parser.parse(html, LocalDate(2026, 1, 2)).single()

        assertEquals(LocalDate(2025, 12, 31), entry.date)
    }

    @Test
    fun parsesRealFddbDiarySnippetWithUnquotedNotepadDateClass() {
        val html =
            """
            <div class="contentblockgrey-lower-noheader"><div class="standardcontent">
                <table width=100% border=0 cellpadding=1 cellspacing=0>
                    <tr><td colspan=7 class=notepaddate><h3>Montag 25. Mai</h3></td></tr>
                    <tr><td colspan=7><h4>Morgens</h4></td></tr>
                    <tr id='np2132383052' class='standardblock-darkercolor'>
                        <td width=90%>
                            <a href='/db/de/lebensmittel/naturprodukt_apfel_frisch/index.html'>310 g Apfel, frisch</a>
                        </td>
                        <td valign=middle>
                            <a href='db/i18n/notepad/?lang=de&q=2132383052&action=change'>Portion</a>
                        </td>
                    </tr>
                </table>
            </div></div>
            """
                .trimIndent()

        val entry = parser.parse(html, LocalDate(2026, 5, 25)).single()

        assertEquals("2132383052", entry.entryId)
        assertEquals(LocalDate(2026, 5, 25), entry.date)
        assertEquals("Morgens", entry.mealName)
        assertEquals("Apfel, frisch", entry.productName)
        assertEquals("https://fddb.info/db/de/lebensmittel/naturprodukt_apfel_frisch/index.html", entry.productUrl)
        assertEquals(Measurement.Gram(310.0), entry.measurement)
    }

    @Test
    fun parsesNonMetricPortionMeasurementAsRawFddbPortion() {
        val html =
            """
            <td class="notepaddate"><h3>Montag 25. Mai</h3></td>
            <h4>Morgens</h4>
            <tr id="np1">
                <td><a href="/db/de/lebensmittel/apfel/index.html">1 Stück Apfel</a></td>
            </tr>
            """
                .trimIndent()

        val entry = parser.parse(html, LocalDate(2026, 5, 25)).single()

        assertNull(entry.measurement)
        assertEquals(1.0, entry.portionMeasurement?.quantity)
        assertEquals("Stück Apfel", entry.portionMeasurement?.labelAndProductName)
        assertEquals("Stück Apfel", entry.productName)
    }

    @Test
    fun recognizesNotepadDateInSingleQuotedDoubleQuotedAndMultiClassAttributes() {
        val html =
            """
            <td class='foo notepaddate bar'><h3>Montag 25. Mai</h3></td>
            <h4>Morgens</h4>
            <tr id="np1"><td><a href="/db/de/lebensmittel/foo/index.html">1 g Foo</a></td></tr>
            <td class="foo notepaddate bar"><h3>Dienstag 26. Mai</h3></td>
            <h4>Mittags</h4>
            <tr id="np2"><td><a href="/db/de/lebensmittel/bar/index.html">2 g Bar</a></td></tr>
            <td class=notepaddate><h3>Mittwoch 27. Mai</h3></td>
            <h4>Abends</h4>
            <tr id="np3"><td><a href="/db/de/lebensmittel/baz/index.html">3 g Baz</a></td></tr>
            """
                .trimIndent()

        val entries = parser.parse(html, LocalDate(2026, 5, 27))

        assertEquals(
            listOf(LocalDate(2026, 5, 25), LocalDate(2026, 5, 26), LocalDate(2026, 5, 27)),
            entries.map { it.date },
        )
    }

    private companion object {
        private val Fixture =
            """
            <table>
                <tr><td class="notepaddate"><h3>Montag 25. Mai</h3></td></tr>
                <tr><td><h4>Morgens</h4></td></tr>
                <tr id="np2131111111">
                    <td><a href="/db/de/lebensmittel/example_food/index.html">310 g Beispielprodukt</a></td>
                    <td><a href="/db/i18n/notepad/?q=2131111111">ändern</a></td>
                </tr>
                <tr id="npactivity"><td><a href="/db/de/activity/run/index.html">30 min Aktivität</a></td></tr>
                <tr id="npwithoutlink"><td>100 g Freitext</td></tr>
                <tr><td><h4>Mittags</h4></td></tr>
                <tr id="np2132222222">
                    <td><a href="/db/de/lebensmittel/example_drink/index.html">5 ml Beispielgetränk</a></td>
                </tr>
                <tr><td><h4>Abends</h4></td></tr>
                <tr id="np2133333333">
                    <td><a href="/db/de/lebensmittel/dummy_food/index.html">10 g Dummy Produkt</a></td>
                </tr>
            </table>
            """
                .trimIndent()
    }
}

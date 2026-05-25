package com.maksimowiczm.foodyou.food.infrastructure.fddb

import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import kotlin.test.Test
import kotlin.test.assertEquals
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

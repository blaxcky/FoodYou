package com.maksimowiczm.foodyou.food.infrastructure.fddb

import com.maksimowiczm.foodyou.common.domain.food.NutrientValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class FddbProductParserTest {
    private val parser = FddbProductParser()

    @Test
    fun parsesGermanProductPage() {
        val product = parser.parse(Fixture)

        assertEquals("ungarische salami", product.name)
        assertEquals("Ich bin Österreich", product.brand)
        assertEquals("9120087699650", product.barcode)
        assertFalse(product.isLiquid)
        assertNull(product.packageWeight)
        assertNull(product.servingWeight)
        assertEquals(430.0, product.nutritionFacts.energy.value)
        assertEquals(24.0, product.nutritionFacts.proteins.value)
        assertEquals(0.5, product.nutritionFacts.carbohydrates.value)
        assertEquals(0.5, product.nutritionFacts.sugars.value)
        assertEquals(37.0, product.nutritionFacts.fats.value)
        assertEquals(4.5, product.nutritionFacts.salt.value)
        assertEquals(NutrientValue.Incomplete(null), product.nutritionFacts.vitaminC)
    }

    private companion object {
        const val Fixture =
            """
            <html>
                <body>
                    <h1 id="fddb-headline1">ungarische salami</h1>
                    <h2 id="fddb-headline2">Ich bin &Ouml;sterreich</h2>
                    <p>Datenquelle: Extern. Produkt eingetragen von einem Fddb Nutzer. EAN: 9120087699650</p>
                    <h3>Nährwerte für 100 g</h3>
                    <table>
                        <tr><td>Brennwert</td><td>1800 kJ</td></tr>
                        <tr><td>Kalorien</td><td>430 kcal</td></tr>
                        <tr><td>Protein</td><td>24 g</td></tr>
                        <tr><td>Kohlenhydrate</td><td>0,5 g</td></tr>
                        <tr><td>davon Zucker</td><td>0,5 g</td></tr>
                        <tr><td>Fett</td><td>37 g</td></tr>
                    </table>
                    <h3>Vitamine</h3>
                    <table><tr><td>Vitamin C</td><td>k. A.</td></tr></table>
                    <h3>Mineralstoffe</h3>
                    <table><tr><td>Salz</td><td>4,5 g</td></tr></table>
                    <h3>Portionen</h3>
                    <p>100 g (100 g)</p>
                </body>
            </html>
            """
    }
}

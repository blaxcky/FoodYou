package com.maksimowiczm.foodyou.food.infrastructure.fddb

import com.maksimowiczm.foodyou.common.domain.food.NutrientValue
import com.maksimowiczm.foodyou.food.domain.entity.FddbPortion
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

    @Test
    fun parsesPackageAndServingPortions() {
        val product = parser.parse(MiniSalamiSticksFixture)

        assertEquals("Clever Mini Salami Sticks", product.name)
        assertEquals("Clever", product.brand)
        assertEquals(200.0, product.packageWeight)
        assertEquals(12.0, product.servingWeight)
        assertEquals(
            listOf(
                FddbPortion("Stück", 12.0, FddbPortion.Unit.Gram),
                FddbPortion("Packung", 200.0, FddbPortion.Unit.Gram),
            ),
            product.portions,
        )
        assertEquals(526.0, product.nutritionFacts.energy.value)
        assertEquals(30.0, product.nutritionFacts.proteins.value)
        assertEquals(0.5, product.nutritionFacts.carbohydrates.value)
        assertEquals(45.0, product.nutritionFacts.fats.value)
        assertEquals(4.6, product.nutritionFacts.salt.value)
    }

    @Test
    fun parsesOnlyManufacturerAsBrand() {
        val product = parser.parse(ProductGroupFixture)

        assertEquals("Hofer", product.brand)
    }

    @Test
    fun parsesMultiplePortionLabelsAndUnits() {
        val product = parser.parse(PortionsFixture)

        assertEquals(
            listOf(
                FddbPortion("Scheibe", 30.0, FddbPortion.Unit.Gram),
                FddbPortion("Riegel", 25.5, FddbPortion.Unit.Gram),
                FddbPortion("Glas", 200.0, FddbPortion.Unit.Milliliter),
            ),
            product.portions,
        )
    }

    @Test
    fun parsesProteinFromSidrowInsteadOfNearbyUploaderName() {
        val product = parser.parse(ProteinRiegelFixture)

        assertEquals("Protein Riegel, Schoko Orange", product.name)
        assertEquals("Rühls Bestes", product.brand)
        assertEquals("714824380957", product.barcode)
        assertEquals(55.0, product.servingWeight)
        assertEquals(390.0, product.nutritionFacts.energy.value)
        assertEquals(33.0, product.nutritionFacts.proteins.value)
        assertEquals(33.0, product.nutritionFacts.carbohydrates.value)
        assertEquals(2.5, product.nutritionFacts.sugars.value)
        assertEquals(14.0, product.nutritionFacts.fats.value)
        assertEquals(0.98, product.nutritionFacts.salt.value)
    }

    @Test
    fun matchesNutrientLabelsExactly() {
        val product = parser.parse(VitaminB12OnlyFixture)

        assertEquals(NutrientValue.Incomplete(null), product.nutritionFacts.vitaminB1)
        assertEquals(12.0, product.nutritionFacts.vitaminB12.value)
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

        const val MiniSalamiSticksFixture =
            """
            <html>
                <body>
                    <h1 id="fddb-headline1">Clever Mini Salami Sticks</h1>
                    <h2 id="fddb-headline2">Clever</h2>
                    <p>Datenquelle: Extern. Produkt eingetragen von einem Fddb Nutzer.</p>
                    <h3>Nährwerte für 100 g</h3>
                    <table>
                        <tr><td>Brennwert</td><td>2202 kJ</td></tr>
                        <tr><td>Kalorien</td><td>526 kcal</td></tr>
                        <tr><td>Protein</td><td>30 g</td></tr>
                        <tr><td>Kohlenhydrate</td><td>0,5 g</td></tr>
                        <tr><td>Fett</td><td>45 g</td></tr>
                    </table>
                    <h3>Mineralstoffe</h3>
                    <table><tr><td>Salz</td><td>4,6 g</td></tr></table>
                    <h3>Portionen</h3>
                    <p>100 g (100 g)</p>
                    <p>Stück (12 g)</p>
                    <p>Packung (200 g)</p>
                </body>
            </html>
            """

        const val ProductGroupFixture =
            """
            <html>
                <body>
                    <h1 id="fddb-headline1">Kornspitz</h1>
                    <h2 id="fddb-headline2">Hofer
                        ,
                        Backwaren
                    </h2>
                    <h3>Nährwerte für 100 g</h3>
                    <table>
                        <tr><td>Kalorien</td><td>250 kcal</td></tr>
                    </table>
                </body>
            </html>
            """

        const val PortionsFixture =
            """
            <html>
                <body>
                    <h1 id="fddb-headline1">Portion Test</h1>
                    <h3>Nährwerte für 100 ml</h3>
                    <table>
                        <tr><td>Kalorien</td><td>50 kcal</td></tr>
                    </table>
                    <h3>Portionen</h3>
                    <p>100 g (100 g)</p>
                    <p>100 ml (100 ml)</p>
                    <p>Scheibe (30 g)</p>
                    <p>Riegel (25,5 g)</p>
                    <p>Glas (200 ml)</p>
                </body>
            </html>
            """

        const val ProteinRiegelFixture =
            """
            <html>
                <body>
                    <h1 id="fddb-headline1">Protein Riegel, Schoko Orange</h1>
                    <h2 id="fddb-headline2"><a>R&uuml;hls Bestes</a></h2>
                    <a href="/foto.html">
                        <img title="Hochgeladen von: maximilian1405" alt="Hochgeladen von: maximilian1405">
                    </a>
                    <a>Hochgeladen von: maximilian1405</a>
                    <p>Datenquelle: Extern. EAN: 714824380957</p>
                    <h2>Nährwerte für 100 g</h2>
                    <div style="background-color:#f0f5f9;padding:2px 4px;">
                        <div class="sidrow"><a href="/db/de/lexikon/brennwert/index.html">Brennwert</a></div>
                        <div>1633 kj</div>
                    </div>
                    <div style="padding:2px 4px;">
                        <div class="sidrow"><span>Kalorien</span></div>
                        <div>390 kcal</div>
                    </div>
                    <div style="background-color:#f0f5f9;padding:2px 4px;">
                        <div class="sidrow"><a href="/db/de/lexikon/protein/index.html">Protein</a></div>
                        <div>33 g</div>
                    </div>
                    <div style="padding:2px 4px;">
                        <div class="sidrow"><a href="/db/de/lexikon/kohlenhydrate/index.html">Kohlenhydrate</a></div>
                        <div>33 g</div>
                    </div>
                    <div style="background-color:#f0f5f9;padding:2px 4px;">
                        <div class="sidrow"><a href="/db/de/lexikon/information_zucker/index.html">davon Zucker</a></div>
                        <div>2,5 g</div>
                    </div>
                    <div style="padding:2px 4px;">
                        <div class="sidrow"><a href="/db/de/lexikon/fett/index.html">Fett</a></div>
                        <div>14 g</div>
                    </div>
                    <h2>Mineralstoffe</h2>
                    <div style="background-color:#f0f5f9;padding:2px 4px;">
                        <div class="sidrow"><a href="/db/de/lexikon/information_mineralstoff-natrium/index.html">Salz</a></div>
                        <div>0,98 g</div>
                    </div>
                    <p>100 g (100 g)</p>
                    <p>Riegel (55 g)</p>
                </body>
            </html>
            """

        const val VitaminB12OnlyFixture =
            """
            <html>
                <body>
                    <h1 id="fddb-headline1">Vitamin Test</h1>
                    <h3>Nährwerte für 100 g</h3>
                    <table>
                        <tr><td>Kalorien</td><td>10 kcal</td></tr>
                        <tr><td>Vitamin B12</td><td>12 &micro;g</td></tr>
                    </table>
                </body>
            </html>
            """
    }
}

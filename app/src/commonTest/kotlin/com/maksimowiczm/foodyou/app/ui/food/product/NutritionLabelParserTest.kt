package com.maksimowiczm.foodyou.app.ui.food.product

import com.maksimowiczm.foodyou.barcodescanner.ui.RecognizedTextElement
import com.maksimowiczm.foodyou.barcodescanner.ui.RecognizedTextLine
import com.maksimowiczm.foodyou.barcodescanner.ui.TextBounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NutritionLabelParserTest {
    @Test
    fun parsesOnlyValuesInPro100Column() {
        val result = NutritionLabelParser.parse(nutritionTable())

        assertTrue(result.hasPer100Basis)
        assertEquals(66f, result.energy?.value)
        assertEquals(NutritionLabelUnit.Kcal, result.energy?.unit)
        assertEquals(3.6f, result.fats?.value)
        assertEquals(3.9f, result.carbohydrates?.value)
        assertEquals(4.4f, result.proteins?.value)
    }

    @Test
    fun ignoresServingAndReferenceIntakeColumns() {
        val result = NutritionLabelParser.parse(nutritionTable())

        assertEquals(66f, result.energy?.value)
        assertEquals(3.6f, result.fats?.value)
        assertEquals(3.9f, result.carbohydrates?.value)
        assertEquals(4.4f, result.proteins?.value)
        assertFalse(result.fields.any { it.value in listOf(9f, 9.8f, 11f) })
    }

    @Test
    fun neverUsesPer100HeaderAsMacroValue() {
        val result =
            NutritionLabelParser.parse(
                listOf(
                    line(
                        0,
                        "Fett Pro 100 g",
                        "Fett" at 10,
                        "Pro" at 210,
                        "100" at 240,
                        "g" at 275,
                    )
                )
            )

        assertTrue(result.hasPer100Basis)
        assertNull(result.fats)
    }

    @Test
    fun neverUsesEnergyNumbersAsMacroValues() {
        val result =
            NutritionLabelParser.parse(
                listOf(
                    line(0, "Pro 100 g", "Pro" at 210, "100" at 240, "g" at 275),
                    line(
                        40,
                        "Kohlenhydrate 274 kJ 66 kcal",
                        "Kohlenhydrate" at 10,
                        "274" at 225,
                        "kJ" at 260,
                        "66" at 305,
                        "kcal" at 335,
                    ),
                )
            )

        assertNull(result.carbohydrates)
    }

    @Test
    fun doesNotUseDecimalWithoutGramUnitAsMacroValue() {
        val result =
            NutritionLabelParser.parse(
                listOf(
                    line(0, "Pro 100 g", "Pro" at 210, "100" at 240, "g" at 275),
                    line(40, "Eiweiss 4,49", "Eiweiss" at 10, "4,49" at 225),
                )
            )

        assertNull(result.proteins)
    }

    @Test
    fun returnsNoValuesWithoutBoundingBoxBasedPro100Column() {
        val result =
            NutritionLabelParser.parse(
                listOf(
                    line(40, "Energy 42 kcal", "Energy" at 10, "42" at 225, "kcal" at 255),
                    line(80, "Fat 0.8 g", "Fat" at 10, "0.8" at 225, "g" at 260),
                )
            )

        assertFalse(result.hasPer100Basis)
        assertTrue(result.fields.isEmpty())
    }

    @Test
    fun acceptsKjEnergyWhenKcalIsAbsent() {
        val result =
            NutritionLabelParser.parse(
                listOf(
                    line(0, "per 100 ml", "per" at 210, "100" at 240, "ml" at 275),
                    line(40, "Energy 418 kJ", "Energy" at 10, "418" at 225, "kJ" at 265),
                )
            )

        assertEquals(418f, result.energy?.value)
        assertEquals(NutritionLabelUnit.Kj, result.energy?.unit)
    }

    @Test
    fun parsesSeparateUnitColumnWithNumericOnlyPer100Values() {
        val result = NutritionLabelParser.parse(separateUnitColumnNutritionTable())

        assertTrue(result.hasPer100Basis)
        assertEquals(539f, result.energy?.value)
        assertEquals(NutritionLabelUnit.Kcal, result.energy?.unit)
        assertEquals(30.9f, result.fats?.value)
        assertEquals(57.5f, result.carbohydrates?.value)
        assertEquals(6.3f, result.proteins?.value)
        assertFalse(result.fields.any { it.value in listOf(12.4f, 23f, 7f, 40f, 44f) })
    }

    @Test
    fun geometryFallbackParsesFragmentedUnreadableNutritionTable() {
        val result =
            NutritionLabelParser.parse(
                listOf(
                    line(40, "2252 / 539", "2252" at 235, "/" at 285, "539" at 305),
                    line(80, "() 30,9", "()" at 165, "30,9" at 250, "12,4" at 430, "44" at 560),
                    line(120, "() 11,1", "()" at 165, "11,1" at 250, "4,4" at 430, "56" at 560),
                    line(160, "(o) 57,5", "(o)" at 165, "57,5" at 250, "23" at 430, "7" at 560),
                    line(200, "g 0,107", "g" at 165, "0,107" at 250, "0,043" at 430, "0" at 560),
                    line(240, "() 6,3", "()" at 165, "6,3" at 250, "2,5" at 430, "40" at 560),
                )
            )

        assertTrue(result.hasPer100Basis)
        assertEquals(539f, result.energy?.value)
        assertEquals(NutritionLabelUnit.Kcal, result.energy?.unit)
        assertEquals(30.9f, result.fats?.value)
        assertEquals(57.5f, result.carbohydrates?.value)
        assertEquals(6.3f, result.proteins?.value)
    }

    @Test
    fun geometryFallbackSkipsReadableSubRowsWhenMappingByOrder() {
        val result =
            NutritionLabelParser.parse(
                listOf(
                    line(40, "2252 / 539", "2252" at 235, "/" at 285, "539" at 305),
                    line(80, "x () 30,9", "x" at 10, "()" at 165, "30,9" at 250),
                    line(
                        120,
                        "of which saturates () 11,1",
                        "of" at 10,
                        "which" at 35,
                        "saturates" at 90,
                        "()" at 165,
                        "11,1" at 250,
                    ),
                    line(160, "x () 57,5", "x" at 10, "()" at 165, "57,5" at 250),
                    line(
                        200,
                        "of which sugars () 0,107",
                        "of" at 10,
                        "which" at 35,
                        "sugars" at 90,
                        "()" at 165,
                        "0,107" at 250,
                    ),
                    line(240, "x () 6,3", "x" at 10, "()" at 165, "6,3" at 250),
                )
            )

        assertEquals(30.9f, result.fats?.value)
        assertEquals(57.5f, result.carbohydrates?.value)
        assertEquals(6.3f, result.proteins?.value)
    }

    @Test
    fun geometryFallbackUsesReadableMainLabelsBeforeRowOrder() {
        val result =
            NutritionLabelParser.parse(
                listOf(
                    line(40, "2252 / 539", "2252" at 235, "/" at 285, "539" at 305),
                    line(80, "protein () 6,3", "protein" at 10, "()" at 165, "6,3" at 250),
                    line(120, "fat () 30,9", "fat" at 10, "()" at 165, "30,9" at 250),
                    line(
                        160,
                        "carbohydrates () 57,5",
                        "carbohydrates" at 10,
                        "()" at 165,
                        "57,5" at 250,
                    ),
                )
            )

        assertEquals(30.9f, result.fats?.value)
        assertEquals(57.5f, result.carbohydrates?.value)
        assertEquals(6.3f, result.proteins?.value)
    }

    @Test
    fun geometryFallbackDoesNotMapReadableSubRowsAsMainMacros() {
        val result =
            NutritionLabelParser.parse(
                listOf(
                    line(40, "2252 / 539", "2252" at 235, "/" at 285, "539" at 305),
                    line(
                        80,
                        "saturated fat () 30,9",
                        "saturated" at 10,
                        "fat" at 105,
                        "()" at 165,
                        "30,9" at 250,
                    ),
                    line(
                        120,
                        "of which sugars () 57,5",
                        "of" at 10,
                        "which" at 35,
                        "sugars" at 90,
                        "()" at 165,
                        "57,5" at 250,
                    ),
                    line(160, "protein () 6,3", "protein" at 10, "()" at 165, "6,3" at 250),
                )
            )

        assertNull(result.fats)
        assertNull(result.carbohydrates)
        assertEquals(6.3f, result.proteins?.value)
    }

    @Test
    fun geometryFallbackIgnoresNumericColumnsWithoutCoherentUnitColumn() {
        val result =
            NutritionLabelParser.parse(
                listOf(
                    line(40, "2252 / 539", "2252" at 235, "/" at 285, "539" at 305),
                    line(80, "30,9 12,4 44", "30,9" at 250, "12,4" at 430, "44" at 560),
                    line(120, "57,5 23 7", "57,5" at 250, "23" at 430, "7" at 560),
                    line(160, "6,3 2,5 40", "6,3" at 250, "2,5" at 430, "40" at 560),
                )
            )

        assertFalse(result.hasPer100Basis)
        assertTrue(result.fields.isEmpty())
    }

    @Test
    fun geometryFallbackIgnoresNumericColumnsWithoutEnergyAnchor() {
        val result =
            NutritionLabelParser.parse(
                listOf(
                    line(80, "() 30,9", "()" at 165, "30,9" at 250),
                    line(120, "() 57,5", "()" at 165, "57,5" at 250),
                    line(160, "() 6,3", "()" at 165, "6,3" at 250),
                )
            )

        assertFalse(result.hasPer100Basis)
        assertTrue(result.fields.isEmpty())
    }

    @Test
    fun geometryFallbackKeepsAmbiguousIntegerMacroValuesUncertain() {
        val result =
            NutritionLabelParser.parse(
                listOf(
                    line(40, "2252 / 539", "2252" at 235, "/" at 285, "539" at 305),
                    line(80, "() 36", "()" at 165, "36" at 250),
                    line(120, "() 39", "()" at 165, "39" at 250),
                    line(160, "() 44", "()" at 165, "44" at 250),
                )
            )

        assertFalse(result.hasPer100Basis)
        assertTrue(result.fields.isEmpty())
    }

    @Test
    fun ignoresNumericOnlyMacroValuesWithoutSeparateRowUnit() {
        val result =
            NutritionLabelParser.parse(
                listOf(
                    line(
                        0,
                        "Per / 100 g Serving RI",
                        "Per" at 205,
                        "/" at 235,
                        "100" at 250,
                        "g" at 285,
                    ),
                    line(
                        40,
                        "Fat 30,9 12,4 44 %",
                        "Fat" at 10,
                        "30,9" at 250,
                        "12,4" at 430,
                        "44" at 560,
                        "%" at 590,
                    ),
                )
            )

        assertNull(result.fats)
    }

    @Test
    fun parsesSmallEuropeanDecimalMacroValuesFromPer100Column() {
        val result =
            NutritionLabelParser.parse(
                listOf(
                    line(0, "Pro 100 g", "Pro" at 210, "100" at 240, "g" at 275),
                    line(40, "Fett 3,6 g", "Fett" at 10, "3,6" at 225, "g" at 265),
                    line(
                        80,
                        "Kohlenhydrate 3,9 g",
                        "Kohlenhydrate" at 10,
                        "3,9" at 225,
                        "g" at 265,
                    ),
                    line(120, "Eiweiss 4,4 g", "Eiweiss" at 10, "4,4" at 225, "g" at 265),
                    line(160, "Salz 0,13 g", "Salz" at 10, "0,13" at 225, "g" at 275),
                )
            )

        assertEquals(3.6f, result.fats?.value)
        assertEquals(3.9f, result.carbohydrates?.value)
        assertEquals(4.4f, result.proteins?.value)
    }

    @Test
    fun treatsLikelyMissingDecimalMacroOcrValuesAsUncertain() {
        val result =
            NutritionLabelParser.parse(
                listOf(
                    line(0, "Pro 100 g", "Pro" at 210, "100" at 240, "g" at 275),
                    line(40, "Fett 36 g", "Fett" at 10, "36" at 225, "g" at 265),
                    line(
                        80,
                        "Kohlenhydrate 39 g",
                        "Kohlenhydrate" at 10,
                        "39" at 225,
                        "g" at 265,
                    ),
                    line(120, "Eiweiss 44 g", "Eiweiss" at 10, "44" at 225, "g" at 265),
                )
            )

        assertNull(result.fats)
        assertNull(result.carbohydrates)
        assertNull(result.proteins)
    }

    @Test
    fun treatsSeparateUnitIntegerMacroOcrValuesAsUncertain() {
        val result =
            NutritionLabelParser.parse(
                listOf(
                    line(
                        0,
                        "Per / 100 g Serving RI",
                        "Per" at 205,
                        "/" at 235,
                        "100" at 250,
                        "g" at 285,
                    ),
                    line(
                        40,
                        "Fat (g) 36 12,4 44 %",
                        "Fat" at 10,
                        "(g)" at 165,
                        "36" at 260,
                        "12,4" at 430,
                        "44" at 560,
                        "%" at 590,
                    ),
                    line(
                        80,
                        "Carbohydrates (g) 39 23 7 %",
                        "Carbohydrates" at 10,
                        "(g)" at 165,
                        "39" at 260,
                        "23" at 430,
                        "7" at 560,
                        "%" at 590,
                    ),
                    line(
                        120,
                        "Protein (g) 44 6,3 40 %",
                        "Protein" at 10,
                        "(g)" at 165,
                        "44" at 260,
                        "6,3" at 430,
                        "40" at 560,
                        "%" at 590,
                    ),
                )
            )

        assertNull(result.fats)
        assertNull(result.carbohydrates)
        assertNull(result.proteins)
    }

    @Test
    fun doesNotUseSubNutrientRowsAsMainMacroRows() {
        val result =
            NutritionLabelParser.parse(
                listOf(
                    line(0, "Pro 100 g", "Pro" at 210, "100" at 240, "g" at 275),
                    line(
                        40,
                        "davon Zucker 3,9 g",
                        "davon" at 10,
                        "Zucker" at 70,
                        "3,9" at 225,
                        "g" at 265,
                    ),
                )
            )

        assertNull(result.carbohydrates)
    }

    @Test
    fun separateUnitSubNutrientRowsDoNotMatchMainMacroRows() {
        val result =
            NutritionLabelParser.parse(
                listOf(
                    line(
                        0,
                        "Per / 100 g Serving RI",
                        "Per" at 205,
                        "/" at 235,
                        "100" at 250,
                        "g" at 285,
                    ),
                    line(
                        40,
                        "saturated fat (g) 30,9 12,4 44 %",
                        "saturated" at 10,
                        "fat" at 105,
                        "(g)" at 165,
                        "30,9" at 250,
                        "12,4" at 430,
                        "44" at 560,
                        "%" at 590,
                    ),
                    line(
                        80,
                        "carbohydrates of which sugars (g) 57,5 23 7 %",
                        "carbohydrates" at 10,
                        "of" at 135,
                        "which" at 160,
                        "sugars" at 220,
                        "(g)" at 300,
                        "57,5" at 250,
                        "23" at 430,
                        "7" at 560,
                        "%" at 590,
                    ),
                )
            )

        assertNull(result.fats)
        assertNull(result.carbohydrates)
    }
}

private fun nutritionTable(): List<RecognizedTextLine> =
    listOf(
        line(
            0,
            "Pro 100 g Pro Portion % RI",
            "Pro" at 210,
            "100" at 240,
            "g" at 275,
            "Pro" at 370,
            "Portion" at 400,
            "%" at 515,
            "RI" at 540,
        ),
        line(
            40,
            "Brennwert 274 kJ 66 kcal 685 kJ 164 kcal 8 %",
            "Brennwert" at 10,
            "274" at 210,
            "kJ" at 250,
            "66" at 285,
            "kcal" at 315,
            "685" at 370,
            "kJ" at 410,
            "164" at 445,
            "kcal" at 485,
            "8" at 540,
            "%" at 565,
        ),
        line(
            80,
            "Fett 3,6 g 9 g 13 %",
            "Fett" at 10,
            "3,6" at 225,
            "g" at 265,
            "9" at 390,
            "g" at 420,
            "13" at 540,
            "%" at 570,
        ),
        line(
            120,
            "Kohlenhydrate 3,9 g 9,8 g 4 %",
            "Kohlenhydrate" at 10,
            "3,9" at 225,
            "g" at 265,
            "9,8" at 390,
            "g" at 430,
            "4" at 540,
            "%" at 570,
        ),
        line(
            160,
            "Eiweiss 4,4 g 11 g 22 %",
            "Eiweiss" at 10,
            "4,4" at 225,
            "g" at 265,
            "11" at 390,
            "g" at 425,
            "22" at 540,
            "%" at 570,
        ),
    )

private fun separateUnitColumnNutritionTable(): List<RecognizedTextLine> =
    listOf(
        line(
            0,
            "Per / 100 g Serving RI",
            "Per" at 205,
            "/" at 235,
            "100" at 250,
            "g" at 285,
            "Serving" at 420,
            "RI" at 560,
        ),
        line(
            40,
            "Energy (kJ / kcal) 2252 / 539 901 / 216 27 %",
            "Energy" at 10,
            "(kJ" at 140,
            "/" at 180,
            "kcal)" at 200,
            "2252" at 235,
            "/" at 285,
            "539" at 305,
            "901" at 430,
            "/" at 470,
            "216" at 490,
            "27" at 560,
            "%" at 590,
        ),
        line(
            80,
            "Fat (g) 30,9 12,4 44 %",
            "Fat" at 10,
            "(g)" at 165,
            "30,9" at 250,
            "12,4" at 430,
            "44" at 560,
            "%" at 590,
        ),
        line(
            120,
            "of which saturates (g) 11,1 4,4 56 %",
            "of" at 10,
            "which" at 35,
            "saturates" at 90,
            "(g)" at 165,
            "11,1" at 250,
            "4,4" at 430,
            "56" at 560,
            "%" at 590,
        ),
        line(
            160,
            "Carbohydrates (g) 57,5 23 7 %",
            "Carbohydrates" at 10,
            "(g)" at 165,
            "57,5" at 250,
            "23" at 430,
            "7" at 560,
            "%" at 590,
        ),
        line(
            200,
            "of which sugars (g) 0,107 0,043 0 %",
            "of" at 10,
            "which" at 35,
            "sugars" at 90,
            "(g)" at 165,
            "0,107" at 250,
            "0,043" at 430,
            "0" at 560,
            "%" at 590,
        ),
        line(
            240,
            "Protein (g) 6,3 2,5 40 %",
            "Protein" at 10,
            "(g)" at 165,
            "6,3" at 250,
            "2,5" at 430,
            "40" at 560,
            "%" at 590,
        ),
    )

private infix fun String.at(left: Int): RecognizedTextElement {
    val width = length * 10
    return RecognizedTextElement(this, TextBounds(left, 0, left + width, 20))
}

private fun line(
    top: Int,
    text: String,
    vararg elements: RecognizedTextElement,
): RecognizedTextLine {
    val shiftedElements =
        elements.map { element ->
            element.copy(
                bounds =
                    TextBounds(
                        left = element.bounds.left,
                        top = top,
                        right = element.bounds.right,
                        bottom = top + 20,
                    )
            )
        }
    val bounds =
        TextBounds(
            left = shiftedElements.minOf { it.bounds.left },
            top = top,
            right = shiftedElements.maxOf { it.bounds.right },
            bottom = top + 20,
        )
    return RecognizedTextLine(text = text, bounds = bounds, elements = shiftedElements)
}

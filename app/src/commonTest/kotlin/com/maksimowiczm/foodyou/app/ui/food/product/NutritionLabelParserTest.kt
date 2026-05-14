package com.maksimowiczm.foodyou.app.ui.food.product

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NutritionLabelParserTest {
    @Test
    fun parsesGermanLabelWithCommaValues() {
        val result =
            NutritionLabelParser.parse(
                listOf(
                    "Nahrwerte pro 100 g",
                    "Brennwert 850 kJ / 203 kcal",
                    "Fett 10,5 g",
                    "Kohlenhydrate 20,2 g",
                    "Eiweiß 3,5 g",
                )
            )

        assertTrue(result.hasPer100Basis)
        assertEquals(203f, result.energy?.value)
        assertEquals(NutritionLabelUnit.Kcal, result.energy?.unit)
        assertEquals(3.5f, result.proteins?.value)
        assertEquals(10.5f, result.fats?.value)
        assertEquals(20.2f, result.carbohydrates?.value)
    }

    @Test
    fun parsesEnglishLabelWithPointValues() {
        val result =
            NutritionLabelParser.parse(
                listOf(
                    "Nutrition facts per 100 ml",
                    "Energy 42 kcal",
                    "Fat 0.8 g",
                    "Carbohydrates 9.1 g",
                    "Proteins 0.3 g",
                )
            )

        assertTrue(result.hasPer100Basis)
        assertEquals(42f, result.energy?.value)
        assertEquals(0.8f, result.fats?.value)
        assertEquals(9.1f, result.carbohydrates?.value)
        assertEquals(0.3f, result.proteins?.value)
    }

    @Test
    fun prefersKcalWhenEnergyLineContainsKjAndKcal() {
        val result =
            NutritionLabelParser.parse(listOf("per 100 g", "Energy 1700 kJ / 406 kcal"))

        assertEquals(406f, result.energy?.value)
        assertEquals(NutritionLabelUnit.Kcal, result.energy?.unit)
    }

    @Test
    fun acceptsKjEnergyAsFallback() {
        val result = NutritionLabelParser.parse(listOf("pro 100 g", "Energie 418 kJ"))

        assertEquals(418f, result.energy?.value)
        assertEquals(NutritionLabelUnit.Kj, result.energy?.unit)
    }

    @Test
    fun doesNotParseEnergyWithoutUnit() {
        val result = NutritionLabelParser.parse(listOf("pro 100 g", "Energy 123"))

        assertNull(result.energy)
    }

    @Test
    fun marksResultUncertainWithoutPer100Column() {
        val result =
            NutritionLabelParser.parse(
                listOf("per serving", "Energy 42 kcal", "Fat 0.8 g", "Carbs 9.1 g")
            )

        assertFalse(result.hasPer100Basis)
        assertFalse(result.fats?.isPer100Basis ?: true)
    }

    @Test
    fun handlesCommonOcrSplitsAndConfusions() {
        val result =
            NutritionLabelParser.parse(
                listOf(
                    "pro 100 g",
                    "Eiwei0ß 4,4 g",
                    "Kohlen hydrate 12,0 g",
                    "Energie kcal/kJ 99 kcal 414 kJ",
                )
            )

        assertEquals(4.4f, result.proteins?.value)
        assertEquals(12f, result.carbohydrates?.value)
        assertEquals(99f, result.energy?.value)
    }
}

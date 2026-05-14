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

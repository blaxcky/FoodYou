package com.maksimowiczm.foodyou.app.ui.food.product

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NutritionLabelScanAccumulatorTest {
    @Test
    fun singleMatchDoesNotStabilizeField() {
        val accumulator = NutritionLabelScanAccumulator()

        val state = accumulator.add(result(proteins = field(ScannedNutrient.Proteins, 4.4f)))

        assertNull(state.proteins.stable)
        assertEquals(4.4f, state.proteins.candidate?.value)
    }

    @Test
    fun twoEqualMatchesStabilizeField() {
        val accumulator = NutritionLabelScanAccumulator()

        accumulator.add(result(proteins = field(ScannedNutrient.Proteins, 4.4f)))
        val state = accumulator.add(result(proteins = field(ScannedNutrient.Proteins, 4.4f)))

        assertEquals(4.4f, state.proteins.stable?.value)
    }

    @Test
    fun singleDifferentMatchDoesNotReplaceStableField() {
        val accumulator = NutritionLabelScanAccumulator()

        accumulator.add(result(proteins = field(ScannedNutrient.Proteins, 4.4f)))
        accumulator.add(result(proteins = field(ScannedNutrient.Proteins, 4.4f)))
        val state = accumulator.add(result(proteins = field(ScannedNutrient.Proteins, 44f)))

        assertEquals(4.4f, state.proteins.stable?.value)
    }

    @Test
    fun threeDifferentMatchesReplaceStableField() {
        val accumulator = NutritionLabelScanAccumulator()

        accumulator.add(result(proteins = field(ScannedNutrient.Proteins, 4.4f)))
        accumulator.add(result(proteins = field(ScannedNutrient.Proteins, 4.4f)))
        accumulator.add(result(proteins = field(ScannedNutrient.Proteins, 5.1f)))
        accumulator.add(result(proteins = field(ScannedNutrient.Proteins, 5.1f)))
        val state = accumulator.add(result(proteins = field(ScannedNutrient.Proteins, 5.1f)))

        assertEquals(5.1f, state.proteins.stable?.value)
    }
}

private fun result(
    energy: NutritionLabelField? = null,
    proteins: NutritionLabelField? = null,
    fats: NutritionLabelField? = null,
    carbohydrates: NutritionLabelField? = null,
): NutritionLabelScanResult =
    NutritionLabelScanResult(
        energy = energy,
        proteins = proteins,
        fats = fats,
        carbohydrates = carbohydrates,
        hasPer100Basis = true,
    )

private fun field(
    nutrient: ScannedNutrient,
    value: Float,
    unit: NutritionLabelUnit = NutritionLabelUnit.Gram,
): NutritionLabelField =
    NutritionLabelField(
        nutrient = nutrient,
        value = value,
        unit = unit,
        sourceText = "$value",
        isPer100Basis = true,
    )

package com.maksimowiczm.foodyou.app.ui.food.component

import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.measurement.MeasurementType
import kotlin.test.Test
import kotlin.test.assertEquals

class MeasurementPickerInputConversionTest {
    private val gram = MeasurementPickerOption.Standard(MeasurementType.Gram)
    private val piece = portion(label = "1 Stück (8 g)", measurement = Measurement.Gram(8.0))

    @Test
    fun gramsAreConvertedToPortionQuantity() {
        assertEquals("4", select(previous = gram, selected = piece, input = "32"))
    }

    @Test
    fun portionQuantityIsConvertedBackToGrams() {
        assertEquals("32", select(previous = piece, selected = gram, input = "4"))
    }

    @Test
    fun editedPortionQuantityDeterminesValueWhenSwitchingBack() {
        assertEquals("24", select(previous = piece, selected = gram, input = "3"))
    }

    @Test
    fun standardServingAndPackageWeightsAreUsed() {
        val serving =
            MeasurementPickerOption.Standard(
                type = MeasurementType.Serving,
                servingWeight = 25.0,
            )
        val pack =
            MeasurementPickerOption.Standard(
                type = MeasurementType.Package,
                totalWeight = 200.0,
            )

        assertEquals("4", select(previous = gram, selected = serving, input = "100"))
        assertEquals("100", select(previous = pack, selected = gram, input = "0.5"))
    }

    @Test
    fun solidAndLiquidPortionsUseTheirMetricUnitWeights() {
        val standardPiece =
            portion(label = "1 Scheibe (20 g)", measurement = Measurement.Gram(20.0))
        val glass =
            portion(label = "1 Glas (250 ml)", measurement = Measurement.Milliliter(250.0))
        val milliliter = MeasurementPickerOption.Standard(MeasurementType.Milliliter)

        assertEquals("3", select(previous = gram, selected = standardPiece, input = "60"))
        assertEquals("2", select(previous = milliliter, selected = glass, input = "500"))
    }

    @Test
    fun convertedValuesAreRoundedToFourDecimalPlacesWithoutTrailingZeros() {
        val threeGramPiece =
            portion(label = "1 Stück (3 g)", measurement = Measurement.Gram(3.0))

        assertEquals("3.3333", select(previous = gram, selected = threeGramPiece, input = "10"))
        assertEquals("2.5", select(previous = gram, selected = piece, input = "20"))
    }

    @Test
    fun explicitSuggestionValueOverridesConversion() {
        assertEquals(
            "1",
            measurementPickerInputForSelection(
                previousOption = gram,
                selectedOption = piece,
                currentInput = "32",
                inputTextOverride = "1",
            ),
        )
    }

    @Test
    fun invalidAndUnconvertibleInputsUseSafeFallbacks() {
        val servingWithoutWeight =
            MeasurementPickerOption.Standard(type = MeasurementType.Serving)

        assertEquals("1", select(previous = gram, selected = piece, input = ""))
        assertEquals("1", select(previous = gram, selected = piece, input = "invalid"))
        assertEquals("2", select(previous = servingWithoutWeight, selected = gram, input = "2"))
    }

    private fun select(
        previous: MeasurementPickerOption,
        selected: MeasurementPickerOption,
        input: String,
    ): String =
        measurementPickerInputForSelection(
            previousOption = previous,
            selectedOption = selected,
            currentInput = input,
        )

    private fun portion(
        label: String,
        measurement: Measurement.ImmutableMeasurement,
    ): MeasurementPickerOption.Portion =
        MeasurementPickerOption.Portion(
            displayLabel = label,
            unitMeasurement = measurement,
        )
}

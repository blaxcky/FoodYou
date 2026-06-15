package com.maksimowiczm.foodyou.app.ui.food.component

import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.measurement.MeasurementType
import kotlin.test.Test
import kotlin.test.assertEquals

class MeasurementPickerInputMemoryTest {
    private val gram = MeasurementPickerOption.Standard(MeasurementType.Gram)
    private val milliliter = MeasurementPickerOption.Standard(MeasurementType.Milliliter)
    private val piece =
        MeasurementPickerOption.Portion(
            displayLabel = "1 Stück (50 g)",
            unitMeasurement = Measurement.Gram(50.0),
        )

    @Test
    fun firstPortionSelectionUsesOneThenStandardRestoresPreviousInput() {
        val memory = MeasurementPickerInputMemory()

        assertEquals(
            "1",
            memory.select(previousOption = gram, selectedOption = piece, currentInput = "200"),
        )
        assertEquals(
            "200",
            memory.select(previousOption = piece, selectedOption = gram, currentInput = "1"),
        )
    }

    @Test
    fun changedPortionInputIsRememberedWhenReturningToPortion() {
        val memory = MeasurementPickerInputMemory()

        memory.select(previousOption = gram, selectedOption = piece, currentInput = "200")
        memory.select(previousOption = piece, selectedOption = gram, currentInput = "2")

        assertEquals(
            "2",
            memory.select(previousOption = gram, selectedOption = piece, currentInput = "200"),
        )
    }

    @Test
    fun firstStandardSelectionKeepsCurrentInput() {
        val memory = MeasurementPickerInputMemory()

        assertEquals(
            "200",
            memory.select(previousOption = gram, selectedOption = milliliter, currentInput = "200"),
        )
    }

    @Test
    fun explicitInputOverrideWinsOverRememberedInput() {
        val memory = MeasurementPickerInputMemory()

        memory.select(previousOption = gram, selectedOption = piece, currentInput = "200")
        memory.select(previousOption = piece, selectedOption = gram, currentInput = "2")

        assertEquals(
            "1",
            memory.select(
                previousOption = gram,
                selectedOption = piece,
                currentInput = "200",
                inputTextOverride = "1",
            ),
        )
    }

    @Test
    fun resetDiscardsRememberedInputs() {
        val memory = MeasurementPickerInputMemory()

        memory.select(previousOption = gram, selectedOption = piece, currentInput = "200")
        memory.select(previousOption = piece, selectedOption = gram, currentInput = "2")
        memory.reset()

        assertEquals(
            "1",
            memory.select(previousOption = gram, selectedOption = piece, currentInput = "50"),
        )
    }
}

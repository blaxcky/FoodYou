package com.maksimowiczm.foodyou.app.ui.food.component

import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.measurement.MeasurementType
import com.maksimowiczm.foodyou.food.domain.entity.FddbPortion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MeasurementPickerOptionTest {
    @Test
    fun gramPortionMultipliesInputByUnitMeasurement() {
        val option =
            FddbPortion("Stück", 12.0, FddbPortion.Unit.Gram)
                .toMeasurementPickerOption(isLiquid = false)

        assertEquals(Measurement.Gram(24.0), option?.measurementForInput(2.0))
    }

    @Test
    fun milliliterPortionMultipliesInputByUnitMeasurement() {
        val option =
            FddbPortion("Glas", 200.0, FddbPortion.Unit.Milliliter)
                .toMeasurementPickerOption(isLiquid = true)

        assertEquals(Measurement.Milliliter(300.0), option?.measurementForInput(1.5))
    }

    @Test
    fun portionSelectionUsesUnitQuantityInput() {
        val option =
            FddbPortion("Scheibe", 20.0, FddbPortion.Unit.Gram)
                .toMeasurementPickerOption(isLiquid = false)

        assertTrue(option?.selectsUnitQuantity == true)
    }

    @Test
    fun standardGramOptionUsesInputAsMeasurementValue() {
        val option = MeasurementPickerOption.Standard(MeasurementType.Gram)

        assertFalse(option.selectsUnitQuantity)
        assertEquals(Measurement.Gram(100.0), option.measurementForInput(100.0))
    }

    @Test
    fun incompatiblePortionUnitsAreFilteredForProductType() {
        assertNull(
            FddbPortion("Glas", 200.0, FddbPortion.Unit.Milliliter)
                .toMeasurementPickerOption(isLiquid = false)
        )
        assertNull(
            FddbPortion("Stück", 12.0, FddbPortion.Unit.Gram)
                .toMeasurementPickerOption(isLiquid = true)
        )
    }
}

package com.maksimowiczm.foodyou.app.ui.food.component

import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.measurement.MeasurementType
import com.maksimowiczm.foodyou.food.domain.entity.ProductPortion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MeasurementPickerOptionTest {
    @Test
    fun gramPortionMultipliesInputByUnitMeasurement() {
        val option =
            ProductPortion("Stück", 12.0, ProductPortion.Unit.Gram)
                .toMeasurementPickerOption(isLiquid = false)

        assertEquals(Measurement.Gram(24.0), option?.measurementForInput(2.0))
    }

    @Test
    fun milliliterPortionMultipliesInputByUnitMeasurement() {
        val option =
            ProductPortion("Glas", 200.0, ProductPortion.Unit.Milliliter)
                .toMeasurementPickerOption(isLiquid = true)

        assertEquals(Measurement.Milliliter(300.0), option?.measurementForInput(1.5))
    }

    @Test
    fun portionSelectionUsesUnitQuantityInput() {
        val option =
            ProductPortion("Scheibe", 20.0, ProductPortion.Unit.Gram)
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
            ProductPortion("Glas", 200.0, ProductPortion.Unit.Milliliter)
                .toMeasurementPickerOption(isLiquid = false)
        )
        assertNull(
            ProductPortion("Stück", 12.0, ProductPortion.Unit.Gram)
                .toMeasurementPickerOption(isLiquid = true)
        )
    }

    @Test
    fun standardPackageLabelShowsSolidPackageWeight() {
        assertEquals(
            "Packung (100 g)",
            formatMeasurementPickerLabel(
                label = "Packung",
                type = MeasurementType.Package,
                totalWeight = 100.0,
                servingWeight = null,
                isLiquid = false,
            ),
        )
    }

    @Test
    fun standardPackageLabelShowsLiquidPackageVolume() {
        assertEquals(
            "Packung (500 ml)",
            formatMeasurementPickerLabel(
                label = "Packung",
                type = MeasurementType.Package,
                totalWeight = 500.0,
                servingWeight = null,
                isLiquid = true,
            ),
        )
    }

    @Test
    fun standardServingLabelShowsServingWeight() {
        assertEquals(
            "Stück (30 g)",
            formatMeasurementPickerLabel(
                label = "Stück",
                type = MeasurementType.Serving,
                totalWeight = null,
                servingWeight = 30.0,
                isLiquid = false,
            ),
        )
    }

    @Test
    fun standardPackageAndServingLabelsStayUnchangedWithoutReferenceWeight() {
        assertEquals(
            "Packung",
            formatMeasurementPickerLabel(
                label = "Packung",
                type = MeasurementType.Package,
                totalWeight = null,
                servingWeight = null,
                isLiquid = false,
            ),
        )
        assertEquals(
            "Stück",
            formatMeasurementPickerLabel(
                label = "Stück",
                type = MeasurementType.Serving,
                totalWeight = null,
                servingWeight = null,
                isLiquid = false,
            ),
        )
    }

    @Test
    fun fddbPortionLabelStaysUnchanged() {
        val option =
            ProductPortion("Packung", 400.0, ProductPortion.Unit.Gram)
                .toMeasurementPickerOption(isLiquid = false)

        assertEquals("1 Packung (400 g)", option?.displayLabel)
    }

    @Test
    fun portionEditAppendsNewPortionWithTrimmedLabel() {
        val portions = listOf(ProductPortion("halbe", 75.0, ProductPortion.Unit.Gram))

        val result =
            portions.withPortionEdit(
                original = null,
                edited = ProductPortion("  Scheibe ", 20.0, ProductPortion.Unit.Gram),
            )

        assertEquals(
            PortionEditResult.Success(
                portions + ProductPortion("Scheibe", 20.0, ProductPortion.Unit.Gram)
            ),
            result,
        )
    }

    @Test
    fun portionEditReplacesPortionInPlaceIncludingRename() {
        val medium = ProductPortion("mittelgroße", 150.0, ProductPortion.Unit.Gram)
        val half = ProductPortion("halbe", 75.0, ProductPortion.Unit.Gram)
        val edited = ProductPortion("groß", 180.0, ProductPortion.Unit.Gram)

        val result = listOf(medium, half).withPortionEdit(original = medium, edited = edited)

        assertEquals(PortionEditResult.Success(listOf(edited, half)), result)
    }

    @Test
    fun portionEditKeepsSameNameWithDifferentCaseAndAmount() {
        val medium = ProductPortion("mittelgroße", 150.0, ProductPortion.Unit.Gram)
        val edited = ProductPortion("Mittelgroße", 160.0, ProductPortion.Unit.Gram)

        val result = listOf(medium).withPortionEdit(original = medium, edited = edited)

        assertEquals(PortionEditResult.Success(listOf(edited)), result)
    }

    @Test
    fun portionEditDeletesPortion() {
        val medium = ProductPortion("mittelgroße", 150.0, ProductPortion.Unit.Gram)
        val half = ProductPortion("halbe", 75.0, ProductPortion.Unit.Gram)

        val result = listOf(medium, half).withPortionEdit(original = medium, edited = null)

        assertEquals(PortionEditResult.Success(listOf(half)), result)
    }

    @Test
    fun portionEditRejectsInvalidInput() {
        val medium = ProductPortion("mittelgroße", 150.0, ProductPortion.Unit.Gram)
        val half = ProductPortion("halbe", 75.0, ProductPortion.Unit.Gram)
        val portions = listOf(medium, half)

        assertEquals(
            PortionEditResult.EmptyName,
            portions.withPortionEdit(null, ProductPortion("  ", 10.0, ProductPortion.Unit.Gram)),
        )
        assertEquals(
            PortionEditResult.InvalidAmount,
            portions.withPortionEdit(null, ProductPortion("Stück", 0.0, ProductPortion.Unit.Gram)),
        )
        assertEquals(
            PortionEditResult.InvalidAmount,
            portions.withPortionEdit(
                null,
                ProductPortion("Stück", Double.NaN, ProductPortion.Unit.Gram),
            ),
        )
        assertEquals(
            PortionEditResult.DuplicateName,
            portions.withPortionEdit(medium, ProductPortion(" Halbe ", 80.0, ProductPortion.Unit.Gram)),
        )
    }

    @Test
    fun portionMatchingFindsPortionWithEqualUnitMeasurement() {
        val options =
            listOf(
                    ProductPortion("mittelgroße", 150.0, ProductPortion.Unit.Gram),
                    ProductPortion("halbe", 75.0, ProductPortion.Unit.Gram),
                )
                .toMeasurementPickerOptions(isLiquid = false)

        assertEquals(options[1], options.portionMatching(Measurement.Gram(75.0)))
        assertNull(options.portionMatching(Measurement.Gram(100.0)))
    }

    @Test
    fun findByPortionLabelUsesNormalizedLabel() {
        val options =
            listOf(ProductPortion("Mittelgroße", 160.0, ProductPortion.Unit.Gram))
                .toMeasurementPickerOptions(isLiquid = false)

        assertEquals(
            options.single(),
            options.findByPortionLabel(
                ProductPortion(" mittelgroße ", 150.0, ProductPortion.Unit.Gram)
            ),
        )
    }
}

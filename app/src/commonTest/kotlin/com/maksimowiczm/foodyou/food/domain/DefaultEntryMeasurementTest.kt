package com.maksimowiczm.foodyou.food.domain

import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.Product
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultEntryMeasurementTest {
    @Test
    fun returnsGramForSolidFood() {
        assertEquals(Measurement.Gram(100.0), defaultEntryMeasurement(isLiquid = false))
    }

    @Test
    fun returnsMilliliterForLiquidFood() {
        assertEquals(Measurement.Milliliter(100.0), defaultEntryMeasurement(isLiquid = true))
    }

    @Test
    fun returnsLatestPackageForProductWithPackageWeight() {
        assertEquals(
            Measurement.Package(1.0),
            defaultEntryMeasurement(
                food = product(packageWeight = 500.0),
                latestMeasurement = Measurement.Package(1.0),
            ),
        )
    }

    @Test
    fun fallsBackToGramWhenLatestPackageHasNoPackageWeight() {
        assertEquals(
            Measurement.Gram(100.0),
            defaultEntryMeasurement(
                food = product(packageWeight = null),
                latestMeasurement = Measurement.Package(1.0),
            ),
        )
    }

    @Test
    fun keepsSolidAndLiquidDefaultsWithoutLatestMeasurement() {
        assertEquals(
            Measurement.Gram(100.0),
            defaultEntryMeasurement(food = product(isLiquid = false), latestMeasurement = null),
        )
        assertEquals(
            Measurement.Milliliter(100.0),
            defaultEntryMeasurement(food = product(isLiquid = true), latestMeasurement = null),
        )
    }

    private fun product(
        isLiquid: Boolean = false,
        packageWeight: Double? = null,
        servingWeight: Double? = null,
    ) =
        Product(
            id = FoodId.Product(1),
            name = "Food",
            brand = null,
            barcode = null,
            note = null,
            isLiquid = isLiquid,
            packageWeight = packageWeight,
            servingWeight = servingWeight,
            source = FoodSource(FoodSource.Type.User),
            nutritionFacts = NutritionFacts(),
        )
}

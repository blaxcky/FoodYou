package com.maksimowiczm.foodyou.food.search.infrastructure.repository

import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.measurement.MeasurementType
import com.maksimowiczm.foodyou.food.search.domain.FoodSearch
import com.maksimowiczm.foodyou.food.search.infrastructure.room.FoodSearch as RoomFoodSearch
import kotlin.test.Test
import kotlin.test.assertEquals

class RoomFoodSearchRepositoryTest {

    @Test
    fun mapsLatestPackageMeasurementToSuggestedMeasurement() {
        val food =
            foodSearch(
                totalWeight = 500.0,
                measurementType = MeasurementType.Package,
                measurementValue = 1.0,
            )

        assertEquals(
            Measurement.Package(1.0),
            (food.toFoodSearchModel() as FoodSearch.Product).suggestedMeasurement,
        )
    }

    @Test
    fun missingMeasurementFallsBackToDefault() {
        val food = foodSearch(measurementType = null, measurementValue = null)

        assertEquals(
            Measurement.Gram(100.0),
            (food.toFoodSearchModel() as FoodSearch.Product).suggestedMeasurement,
        )
    }

    private fun foodSearch(
        totalWeight: Double? = null,
        measurementType: MeasurementType? = null,
        measurementValue: Double? = null,
    ) =
        RoomFoodSearch(
            productId = 1,
            recipeId = null,
            headline = "Food",
            isLiquid = false,
            nutrients = null,
            vitamins = null,
            minerals = null,
            totalWeight = totalWeight,
            servingWeight = null,
            measurementType = measurementType,
            measurementValue = measurementValue,
        )
}

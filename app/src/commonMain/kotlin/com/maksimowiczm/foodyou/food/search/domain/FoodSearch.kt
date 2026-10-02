package com.maksimowiczm.foodyou.food.search.domain

import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.domain.food.WeightCalculator
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.food.domain.entity.FoodId

sealed interface FoodSearch {
    val id: FoodId
    val headline: String
    val isLiquid: Boolean
    val suggestedMeasurement: Measurement

    /** Food name without the brand. */
    val name: String

    data class Product(
        override val id: FoodId.Product,
        override val headline: String,
        override val isLiquid: Boolean,
        val nutritionFacts: NutritionFacts,
        val totalWeight: Double?,
        val servingWeight: Double?,
        val isFavorite: Boolean,
        override val suggestedMeasurement: Measurement,
        override val name: String = headline,
        val brand: String? = null,
    ) : FoodSearch {
        fun weight(measurement: Measurement): Double? =
            WeightCalculator.calculateWeight(
                measurement = measurement,
                totalWeight = totalWeight,
                servingWeight = servingWeight,
            )
    }

    data class Recipe(
        override val id: FoodId.Recipe,
        override val headline: String,
        override val isLiquid: Boolean,
        override val suggestedMeasurement: Measurement,
        override val name: String = headline,
    ) : FoodSearch
}

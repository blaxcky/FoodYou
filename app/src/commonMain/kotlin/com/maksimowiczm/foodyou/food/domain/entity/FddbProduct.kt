package com.maksimowiczm.foodyou.food.domain.entity

import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts

data class FddbProduct(
    val name: String,
    val brand: String?,
    val barcode: String?,
    val isLiquid: Boolean,
    val packageWeight: Double?,
    val servingWeight: Double?,
    val portions: List<ProductPortion> = emptyList(),
    val nutritionFacts: NutritionFacts,
)

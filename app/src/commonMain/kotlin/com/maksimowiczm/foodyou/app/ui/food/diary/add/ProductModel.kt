package com.maksimowiczm.foodyou.app.ui.food.diary.add

import androidx.compose.runtime.*
import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.domain.food.WeightCalculator
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.food.domain.entity.ProductPortion
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.Product
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFoodProduct

@Immutable
internal data class ProductModel(
    override val foodId: FoodId.Product,
    override val name: String,
    override val nutritionFacts: NutritionFacts,
    override val isLiquid: Boolean,
    override val note: String?,
    val source: FoodSource,
    override val totalWeight: Double?,
    override val servingWeight: Double?,
    val portions: List<ProductPortion> = emptyList(),
) : FoodModel {
    constructor(
        product: Product
    ) : this(
        foodId = product.id,
        name = product.headline,
        nutritionFacts = product.nutritionFacts,
        isLiquid = product.isLiquid,
        note = product.note,
        source = product.source,
        totalWeight = product.totalWeight,
        servingWeight = product.servingWeight,
        portions = product.portions,
    )

    constructor(
        product: DiaryFoodProduct
    ) : this(
        foodId = product.id,
        name = product.name,
        nutritionFacts = product.nutritionFacts,
        isLiquid = product.isLiquid,
        note = product.note,
        source = product.source,
        totalWeight = product.totalWeight,
        servingWeight = product.servingWeight,
    )

    override fun weight(measurement: Measurement): Double? =
        WeightCalculator.calculateWeight(
            measurement = measurement,
            servingWeight = servingWeight,
            totalWeight = totalWeight,
        )
}

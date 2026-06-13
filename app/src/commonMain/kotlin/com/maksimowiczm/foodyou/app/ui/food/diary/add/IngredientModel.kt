package com.maksimowiczm.foodyou.app.ui.food.diary.add

import androidx.compose.runtime.*
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.Recipe
import com.maksimowiczm.foodyou.food.domain.entity.RecipeIngredient
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFoodProduct
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFoodRecipe
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFoodRecipeIngredient

@Immutable
internal data class IngredientModel(
    val foodId: FoodId,
    val name: String,
    val nutritionFacts: NutritionFacts?,
    val measurement: Measurement,
    val isRecipe: Boolean,
    val totalWeight: Double?,
    val servingWeight: Double?,
    val isLiquid: Boolean,
) {
    val isValid: Boolean
        get() =
            when (measurement) {
                is Measurement.FluidOunce,
                is Measurement.Gram,
                is Measurement.Milliliter,
                is Measurement.Ounce -> true

                is Measurement.Package -> totalWeight != null
                is Measurement.Serving -> servingWeight != null
            }

    constructor(
        ingredient: RecipeIngredient
    ) : this(
        foodId = ingredient.food.id,
        name = ingredient.food.headline,
        nutritionFacts = ingredient.nutritionFacts,
        measurement = ingredient.measurement,
        isRecipe = ingredient.food is Recipe,
        totalWeight = ingredient.food.totalWeight,
        servingWeight = ingredient.food.servingWeight,
        isLiquid = ingredient.food.isLiquid,
    )

    constructor(
        ingredient: DiaryFoodRecipeIngredient
    ) : this(
        foodId =
            when (val food = ingredient.food) {
                is DiaryFoodProduct -> food.id
                is DiaryFoodRecipe -> food.id
            },
        name = ingredient.food.name,
        nutritionFacts = ingredient.nutritionFacts,
        measurement = ingredient.measurement,
        isRecipe = ingredient.food is DiaryFoodRecipe,
        totalWeight = ingredient.food.totalWeight,
        servingWeight = ingredient.food.servingWeight,
        isLiquid = ingredient.food.isLiquid,
    )
}

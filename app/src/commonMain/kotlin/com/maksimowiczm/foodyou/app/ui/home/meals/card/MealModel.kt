package com.maksimowiczm.foodyou.app.ui.home.meals.card

import androidx.compose.runtime.*
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.ProductPortion
import com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntryId
import com.maksimowiczm.foodyou.fooddiary.domain.entity.ManualDiaryEntryId
import kotlinx.datetime.LocalTime

@Immutable
internal sealed interface MealEntrySelectionKey {
    @Immutable data class Food(val id: FoodDiaryEntryId) : MealEntrySelectionKey

    @Immutable data class Manual(val id: ManualDiaryEntryId) : MealEntrySelectionKey
}

@Immutable
internal data class MealModel(
    val id: Long,
    val name: String,
    val from: LocalTime,
    val to: LocalTime,
    val isAllDay: Boolean,
    val foods: List<MealEntryModel>,
    val energy: Int,
    val proteins: Double,
    val carbohydrates: Double,
    val fats: Double,
)

@Immutable
internal sealed interface MealEntryModel {
    val mealId: Long
    val selectionKey: MealEntrySelectionKey
    val name: String
    val energy: Int?
    val proteins: Double?
    val carbohydrates: Double?
    val fats: Double?
}

@Immutable
internal data class FoodMealEntryModel(
    val id: FoodDiaryEntryId,
    override val mealId: Long,
    val editableProductId: FoodId.Product?,
    override val name: String,
    override val energy: Int?,
    override val proteins: Double?,
    override val carbohydrates: Double?,
    override val fats: Double?,
    val measurement: Measurement,
    val weight: Double?,
    val isLiquid: Boolean,
    val isRecipe: Boolean,
    val servingWeight: Double?,
    val totalWeight: Double?,
    val portions: List<ProductPortion> = emptyList(),
) : MealEntryModel {
    override val selectionKey: MealEntrySelectionKey = MealEntrySelectionKey.Food(id)
}

@Immutable
internal data class ManualMealEntryModel(
    val id: ManualDiaryEntryId,
    override val mealId: Long,
    override val name: String,
    override val energy: Int?,
    override val proteins: Double?,
    override val carbohydrates: Double?,
    override val fats: Double?,
) : MealEntryModel {
    override val selectionKey: MealEntrySelectionKey = MealEntrySelectionKey.Manual(id)
}

internal sealed interface EntryAddition {
    data class Food(val measurement: Measurement) : EntryAddition
    data class Manual(val factor: Double) : EntryAddition
}

package com.maksimowiczm.foodyou.app.ui.food.diary.update

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.food.component.toMeasurementPickerOptions
import com.maksimowiczm.foodyou.app.ui.food.diary.add.FoodEntryForm
import com.maksimowiczm.foodyou.app.ui.food.diary.add.FoodModel
import com.maksimowiczm.foodyou.app.ui.food.diary.add.ProductModel
import com.maksimowiczm.foodyou.app.ui.food.diary.add.RecipeModel
import com.maksimowiczm.foodyou.app.ui.food.diary.component.rememberFoodMeasurementFormState
import com.maksimowiczm.foodyou.common.compose.extension.LaunchedCollectWithLifecycle
import com.maksimowiczm.foodyou.common.domain.measurement.type
import com.maksimowiczm.foodyou.common.extension.minus
import com.maksimowiczm.foodyou.common.extension.plus
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFood
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFoodProduct
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFoodRecipe
import com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntryId
import kotlin.time.Duration.Companion.days
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun UpdateEntryScreen(
    entryId: Long,
    onBack: () -> Unit,
    onEditFood: (FoodId) -> Unit,
    onSave: () -> Unit,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
) {
    val viewModel: UpdateFoodDiaryEntryViewModel = koinViewModel {
        parametersOf(FoodDiaryEntryId(entryId))
    }

    LaunchedCollectWithLifecycle(viewModel.uiEvents) {
        when (it) {
            is UpdateEntryEvent.Saved -> onSave()
        }
    }

    val meals by viewModel.meals.collectAsStateWithLifecycle()
    val entry = viewModel.entry.collectAsStateWithLifecycle().value
    val possibleTypes = viewModel.possibleMeasurementTypes.collectAsStateWithLifecycle().value
    val suggestions = viewModel.suggestions.collectAsStateWithLifecycle().value
    val portions = viewModel.portions.collectAsStateWithLifecycle().value
    val today by viewModel.today.collectAsStateWithLifecycle()

    if (entry == null || suggestions == null || possibleTypes == null || portions == null) {
        // TODO loading state
    } else {

        val selectedMeasurement =
            remember(entry.measurement, suggestions, possibleTypes) {
                if (entry.measurement.type in possibleTypes) {
                    entry.measurement
                } else {
                    suggestions.firstOrNull { it.type in possibleTypes }
                }
            }

        if (selectedMeasurement == null) {
            return
        }

        val state =
            rememberFoodMeasurementFormState(
                today = today,
                possibleDates =
                    listOf(today.minus(1.days), today, today.plus(1.days), entry.date)
                        .distinct()
                        .sorted(),
                selectedDate = entry.date,
                meals = remember(meals) { meals.map { it.name } },
                selectedMeal =
                    remember(meals, entry) { meals.firstOrNull { it.id == entry.mealId }?.name },
                suggestions = suggestions,
                portionOptions =
                    remember(portions, entry.food) {
                        portions.toMeasurementPickerOptions(entry.food.isLiquid)
                    },
                totalWeight = entry.food.totalWeight,
                servingWeight = entry.food.servingWeight,
                isLiquid = entry.food.isLiquid,
                possibleTypes = possibleTypes,
                selectedMeasurement = selectedMeasurement,
            )

        FoodEntryForm(
            onBack = onBack,
            onUnpack = {
                val selectedMealId =
                    state.mealsState.selectedMeal?.let { mealName ->
                        meals.firstOrNull { it.name == mealName }?.id
                    }

                if (selectedMealId != null) {
                    viewModel.unpack(
                        measurement = state.measurementState.measurement,
                        mealId = selectedMealId,
                        date = state.dateState.selectedDate,
                    )
                }
            },
            onEditFood = onEditFood,
            onDelete = null,
            onIngredient = null,
            onSave = {
                val selectedMealId =
                    state.mealsState.selectedMeal?.let { mealName ->
                        meals.firstOrNull { it.name == mealName }?.id
                    }

                if (selectedMealId != null) {
                    viewModel.save(
                        measurement = state.measurementState.measurement,
                        mealId = selectedMealId,
                        date = state.dateState.selectedDate,
                    )
                }
            },
            food = entry.food.toFoodModel(),
            history = emptyList(),
            state = state,
            animatedVisibilityScope = animatedVisibilityScope,
            modifier = modifier,
        )
    }
}

private fun DiaryFood.toFoodModel(): FoodModel =
    when (this) {
        is DiaryFoodProduct -> ProductModel(this)
        is DiaryFoodRecipe -> RecipeModel(this)
    }

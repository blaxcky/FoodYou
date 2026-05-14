package com.maksimowiczm.foodyou.app.ui.home.meals.card

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.home.shared.HomeState
import com.maksimowiczm.foodyou.fooddiary.domain.entity.MealsCardsLayout
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun rememberMealsCardsState(
    homeState: HomeState,
    onAdd: (epochDay: Long, mealId: Long) -> Unit,
    onQuickAdd: (epochDay: Long, mealId: Long) -> Unit,
    onEditEntry: (foodEntryId: Long?, manualEntryId: Long?) -> Unit,
    onLongClick: (mealId: Long) -> Unit,
): MealsCardsState {
    val viewModel: MealsCardsViewModel = koinViewModel()
    val diaryMeals = viewModel.diaryMeals.collectAsStateWithLifecycle().value
    val layout by viewModel.layout.collectAsStateWithLifecycle()

    LaunchedEffect(homeState.selectedDate, viewModel) { viewModel.setDate(homeState.selectedDate) }

    return MealsCardsState(
        meals = diaryMeals,
        layout = layout,
        onAdd = { mealId -> onAdd(homeState.selectedDate.toEpochDays(), mealId) },
        onQuickAdd = { mealId -> onQuickAdd(homeState.selectedDate.toEpochDays(), mealId) },
        onEditEntry = { model ->
            val foodEntry = model as? FoodMealEntryModel
            val manualEntry = model as? ManualMealEntryModel
            onEditEntry(foodEntry?.id?.value, manualEntry?.id?.value)
        },
        onDeleteEntry = viewModel::onDeleteEntry,
        onLongClick = onLongClick,
    )
}

internal class MealsCardsState(
    val meals: List<MealModel>?,
    val layout: MealsCardsLayout,
    val onAdd: (mealId: Long) -> Unit,
    val onQuickAdd: (mealId: Long) -> Unit,
    val onEditEntry: (MealEntryModel) -> Unit,
    val onDeleteEntry: (MealEntryModel) -> Unit,
    val onLongClick: (mealId: Long) -> Unit,
)

internal fun LazyListScope.mealsCards(
    state: MealsCardsState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    when (state.layout) {
        MealsCardsLayout.Horizontal ->
            item(key = "meals-horizontal", contentType = "meals-horizontal") {
                HorizontalMealsCards(
                    meals = state.meals,
                    onAdd = state.onAdd,
                    onQuickAdd = state.onQuickAdd,
                    onEditEntry = state.onEditEntry,
                    onDeleteEntry = state.onDeleteEntry,
                    onLongClick = state.onLongClick,
                    contentPadding = contentPadding,
                    modifier = modifier,
                )
            }

        MealsCardsLayout.Vertical ->
            if (state.meals == null) {
                items(
                    count = 4,
                    key = { index -> "meal-skeleton-$index" },
                    contentType = { "meal-skeleton" },
                ) {
                    MealCardSkeleton(modifier = modifier.padding(contentPadding))
                }
            } else {
                items(
                    items = state.meals,
                    key = { meal -> "meal-${meal.id}" },
                    contentType = { meal ->
                        if (meal.foods.isEmpty()) {
                            "meal-empty"
                        } else {
                            "meal-foods"
                        }
                    },
                ) { meal ->
                    MealCard(
                        meal = meal,
                        onAddFood = { state.onAdd(meal.id) },
                        onQuickAdd = { state.onQuickAdd(meal.id) },
                        onEditEntry = state.onEditEntry,
                        onDeleteEntry = state.onDeleteEntry,
                        onLongClick = { state.onLongClick(meal.id) },
                        modifier = modifier.padding(contentPadding),
                    )
                }
            }
    }
}

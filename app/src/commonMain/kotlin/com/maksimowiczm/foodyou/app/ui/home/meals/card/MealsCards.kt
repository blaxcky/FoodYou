package com.maksimowiczm.foodyou.app.ui.home.meals.card

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.home.shared.HomeState
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.fooddiary.domain.entity.MealsCardsLayout
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun rememberMealsCardsState(
    homeState: HomeState,
    onAdd: (epochDay: Long, mealId: Long) -> Unit,
    onQuickAdd: (epochDay: Long, mealId: Long) -> Unit,
    onBarcodeScan: (epochDay: Long, mealId: Long) -> Unit,
    onEditEntry: (foodEntryId: Long?, manualEntryId: Long?) -> Unit,
    onEditFood: (FoodId.Product) -> Unit,
    onLongClick: (mealId: Long) -> Unit,
): MealsCardsState {
    val viewModel: MealsCardsViewModel = koinViewModel()
    val diaryMeals = viewModel.diaryMeals.collectAsStateWithLifecycle().value
    val layout by viewModel.layout.collectAsStateWithLifecycle()
    val selectedEntries by viewModel.selectedEntries.collectAsStateWithLifecycle()

    LaunchedEffect(homeState.selectedDate, viewModel) { viewModel.setDate(homeState.selectedDate) }

    return MealsCardsState(
        meals = diaryMeals,
        layout = layout,
        selectedEntries = selectedEntries,
        onAdd = { mealId -> onAdd(homeState.selectedDate.toEpochDays(), mealId) },
        onQuickAdd = { mealId -> onQuickAdd(homeState.selectedDate.toEpochDays(), mealId) },
        onBarcodeScan = { mealId -> onBarcodeScan(homeState.selectedDate.toEpochDays(), mealId) },
        onEditEntry = { model ->
            val foodEntry = model as? FoodMealEntryModel
            val manualEntry = model as? ManualMealEntryModel
            onEditEntry(foodEntry?.id?.value, manualEntry?.id?.value)
        },
        onEditFood = onEditFood,
        onAddToEntry = viewModel::onAddToEntry,
        onDeleteEntry = viewModel::onDeleteEntry,
        onEnterSelection = viewModel::enterSelection,
        onToggleSelection = viewModel::toggleSelection,
        onClearSelection = viewModel::clearSelection,
        onDeleteSelectedEntries = viewModel::deleteSelectedEntries,
        onMoveSelectedEntries = viewModel::moveSelectedEntries,
        onLongClick = onLongClick,
    )
}

internal class MealsCardsState(
    val meals: List<MealModel>?,
    val layout: MealsCardsLayout,
    val selectedEntries: Set<MealEntrySelectionKey>,
    val onAdd: (mealId: Long) -> Unit,
    val onQuickAdd: (mealId: Long) -> Unit,
    val onBarcodeScan: (mealId: Long) -> Unit,
    val onEditEntry: (MealEntryModel) -> Unit,
    val onEditFood: (FoodId.Product) -> Unit,
    val onAddToEntry: (MealEntryModel, Double) -> Unit,
    val onDeleteEntry: (MealEntryModel) -> Unit,
    val onEnterSelection: (MealEntryModel) -> Unit,
    val onToggleSelection: (MealEntryModel) -> Unit,
    val onClearSelection: () -> Unit,
    val onDeleteSelectedEntries: () -> Unit,
    val onMoveSelectedEntries: (mealId: Long) -> Unit,
    val onLongClick: (mealId: Long) -> Unit,
) {
    val isSelectionMode: Boolean
        get() = selectedEntries.isNotEmpty()
}

internal fun LazyListScope.mealsCards(
    state: MealsCardsState,
    contentPadding: PaddingValues,
    bottomSpacing: Dp,
    modifier: Modifier = Modifier,
) {
    when (state.layout) {
        MealsCardsLayout.Horizontal ->
            item(key = "meals-horizontal", contentType = "meals-horizontal") {
                HorizontalMealsCards(
                    meals = state.meals,
                    onAdd = state.onAdd,
                    onQuickAdd = state.onQuickAdd,
                    onBarcodeScan = state.onBarcodeScan,
                    onEditEntry = state.onEditEntry,
                    onEditFood = state.onEditFood,
                    onAddToEntry = state.onAddToEntry,
                    onDeleteEntry = state.onDeleteEntry,
                    selectedEntries = state.selectedEntries,
                    isSelectionMode = state.isSelectionMode,
                    onEnterSelection = state.onEnterSelection,
                    onToggleSelection = state.onToggleSelection,
                    onLongClick = state.onLongClick,
                    contentPadding = contentPadding,
                    modifier = modifier.padding(bottom = bottomSpacing),
                )
            }

        MealsCardsLayout.Vertical ->
            if (state.meals == null) {
                items(
                    count = 4,
                    key = { index -> "meal-skeleton-$index" },
                    contentType = { "meal-skeleton" },
                ) { index ->
                    MealCardSkeleton(
                        modifier =
                            modifier.padding(contentPadding).padding(
                                bottom = if (index == 3) bottomSpacing else DefaultMealCardSpacing
                            )
                    )
                }
            } else {
                val meals = requireNotNull(state.meals)
                items(
                    items = meals,
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
                        onBarcodeScan = { state.onBarcodeScan(meal.id) },
                        onEditEntry = state.onEditEntry,
                        onEditFood = state.onEditFood,
                        onAddToEntry = state.onAddToEntry,
                        onDeleteEntry = state.onDeleteEntry,
                        selectedEntries = state.selectedEntries,
                        isSelectionMode = state.isSelectionMode,
                        onEnterSelection = state.onEnterSelection,
                        onToggleSelection = state.onToggleSelection,
                        onLongClick = { state.onLongClick(meal.id) },
                        modifier =
                            modifier.padding(contentPadding).padding(
                                bottom =
                                    if (meal == meals.last()) bottomSpacing
                                    else DefaultMealCardSpacing
                            ),
                    )
                }
            }
    }
}

private val DefaultMealCardSpacing = 12.dp

package com.maksimowiczm.foodyou.app.ui.home.meals.card

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.maksimowiczm.foodyou.food.domain.entity.FoodId

@Composable
internal fun HorizontalMealsCards(
    meals: List<MealModel>?,
    onAdd: (mealId: Long) -> Unit,
    onQuickAdd: (mealId: Long) -> Unit,
    onEditEntry: (MealEntryModel) -> Unit,
    onEditFood: (FoodId.Product) -> Unit,
    onAddToEntry: (MealEntryModel, Double) -> Unit,
    onDeleteEntry: (MealEntryModel) -> Unit,
    onLongClick: (mealId: Long) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    // Must be same as meals count or more but since we don't have meals count yet set it to some
    // extreme value. If it is less than actual meals count pager will scroll back to the
    // last item which is annoying for the user.
    // Let's assume that user won't use more than 20 meals
    val pagerState = rememberPagerState(pageCount = { meals?.size ?: 20 })

    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        verticalAlignment = Alignment.Top,
        contentPadding =
            PaddingValues(
                start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
                end = 24.dp,
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding(),
            ),
    ) { page ->
        val meal = meals?.getOrNull(page)

        if (meal != null) {
            MealCard(
                meal = meal,
                onAddFood = { onAdd(meal.id) },
                onQuickAdd = { onQuickAdd(meal.id) },
                onEditEntry = onEditEntry,
                onEditFood = onEditFood,
                onAddToEntry = onAddToEntry,
                onDeleteEntry = onDeleteEntry,
                onLongClick = { onLongClick(meal.id) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            )
        } else {
            MealCardSkeleton(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            )
        }
    }
}

package com.maksimowiczm.foodyou.fooddiary.domain.entity

import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferences
import kotlinx.datetime.LocalDate

data class MealsPreferences(
    val layout: MealsCardsLayout,
    val useTimeBasedSorting: Boolean,
    val ignoreAllDayMeals: Boolean,
    val collapsedMealCards: Set<CollapsedMealCard> = emptySet(),
) : UserPreferences

data class CollapsedMealCard(val date: LocalDate, val mealId: Long)

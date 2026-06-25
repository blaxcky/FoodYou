package com.maksimowiczm.foodyou.fooddiary.domain.usecase

import com.maksimowiczm.foodyou.fooddiary.domain.entity.Meal
import kotlinx.datetime.LocalTime

fun selectMealForBarcodeShortcut(meals: List<Meal>, time: LocalTime): Meal? {
    val mealsByRank = meals.sortedBy(Meal::rank)

    return mealsByRank.firstOrNull { meal -> !meal.isAllDay && meal.contains(time) }
        ?: mealsByRank.firstOrNull(Meal::isAllDay)
        ?: mealsByRank.firstOrNull()
}

private val Meal.isAllDay: Boolean
    get() = from == to

private fun Meal.contains(time: LocalTime): Boolean =
    if (to < from) {
        from <= time || time <= to
    } else {
        from <= time && time <= to
    }

package com.maksimowiczm.foodyou.fooddiary.domain.entity

import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferences
import kotlinx.datetime.LocalDate

data class MealsPreferences(
    val layout: MealsCardsLayout,
    val useTimeBasedSorting: Boolean,
    val ignoreAllDayMeals: Boolean,
    val collapsedMealCards: Set<CollapsedMealCard> = emptySet(),
    val displayedMacros: Set<MealCardMacro> = MealCardMacro.default,
    val showMacrosInFoodEntries: Boolean = true,
    val macroStyle: MealCardMacroStyle = MealCardMacroStyle.default,
) : UserPreferences

data class CollapsedMealCard(val date: LocalDate, val mealId: Long)

enum class MealCardMacro {
    Fats,
    Carbohydrates,
    Proteins;

    companion object {
        val default: Set<MealCardMacro>
            get() = entries.toSet()
    }
}

enum class MealCardMacroStyle {
    Letters,
    Icons;

    companion object {
        val default: MealCardMacroStyle
            get() = Letters
    }
}

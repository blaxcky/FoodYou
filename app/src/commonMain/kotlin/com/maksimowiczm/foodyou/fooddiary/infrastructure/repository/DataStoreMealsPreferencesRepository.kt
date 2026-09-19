package com.maksimowiczm.foodyou.fooddiary.infrastructure.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.maksimowiczm.foodyou.common.infrastructure.datastore.AbstractDataStoreUserPreferencesRepository
import com.maksimowiczm.foodyou.fooddiary.domain.entity.CollapsedMealCard
import com.maksimowiczm.foodyou.fooddiary.domain.entity.MealCardMacro
import com.maksimowiczm.foodyou.fooddiary.domain.entity.MealsCardsLayout
import com.maksimowiczm.foodyou.fooddiary.domain.entity.MealsPreferences
import kotlinx.datetime.LocalDate

internal class DataStoreMealsPreferencesRepository(dataStore: DataStore<Preferences>) :
    AbstractDataStoreUserPreferencesRepository<MealsPreferences>(dataStore) {
    override fun Preferences.toUserPreferences(): MealsPreferences =
        MealsPreferences(
            layout = getLayout(),
            useTimeBasedSorting = this[MealsPreferencesDataStoreKeys.useTimeBasedSorting] ?: false,
            ignoreAllDayMeals = this[MealsPreferencesDataStoreKeys.ignoreAllDayMeals] ?: false,
            collapsedMealCards =
                decodeCollapsedMealCards(
                    this[MealsPreferencesDataStoreKeys.collapsedMealCards].orEmpty()
                ),
            displayedMacros =
                decodeDisplayedMacros(this[MealsPreferencesDataStoreKeys.displayedMacros]),
            showMacrosInFoodEntries =
                decodeShowMacrosInFoodEntries(
                    this[MealsPreferencesDataStoreKeys.showMacrosInFoodEntries]
                ),
        )

    override fun MutablePreferences.applyUserPreferences(updated: MealsPreferences) {
        this[MealsPreferencesDataStoreKeys.layout] = updated.layout.ordinal
        this[MealsPreferencesDataStoreKeys.useTimeBasedSorting] = updated.useTimeBasedSorting
        this[MealsPreferencesDataStoreKeys.ignoreAllDayMeals] = updated.ignoreAllDayMeals
        this[MealsPreferencesDataStoreKeys.collapsedMealCards] =
            encodeCollapsedMealCards(updated.collapsedMealCards)
        this[MealsPreferencesDataStoreKeys.displayedMacros] =
            encodeDisplayedMacros(updated.displayedMacros)
        this[MealsPreferencesDataStoreKeys.showMacrosInFoodEntries] =
            updated.showMacrosInFoodEntries
    }
}

internal fun encodeCollapsedMealCards(cards: Set<CollapsedMealCard>): Set<String> =
    cards.mapTo(mutableSetOf()) { card -> "${card.date}|${card.mealId}" }

internal fun decodeCollapsedMealCards(values: Set<String>): Set<CollapsedMealCard> =
    values.mapNotNullTo(mutableSetOf()) { value ->
        val parts = value.split('|')
        if (parts.size != 2) return@mapNotNullTo null

        runCatching {
                CollapsedMealCard(
                    date = LocalDate.parse(parts[0]),
                    mealId = parts[1].toLong().also { require(it > 0) },
                )
            }
            .getOrNull()
    }

internal fun encodeDisplayedMacros(macros: Set<MealCardMacro>): Set<String> =
    macros.mapTo(mutableSetOf(), MealCardMacro::name)

internal fun decodeDisplayedMacros(values: Set<String>?): Set<MealCardMacro> =
    values?.mapNotNullTo(mutableSetOf()) { value ->
        runCatching { MealCardMacro.valueOf(value) }.getOrNull()
    } ?: MealCardMacro.default

internal fun decodeShowMacrosInFoodEntries(value: Boolean?): Boolean = value ?: true

private fun Preferences.getLayout(): MealsCardsLayout =
    runCatching { this[MealsPreferencesDataStoreKeys.layout]?.let { MealsCardsLayout.entries[it] } }
        .getOrNull() ?: MealsCardsLayout.default

private object MealsPreferencesDataStoreKeys {
    val layout = intPreferencesKey("fooddiary:meals_preferences:layout")
    val useTimeBasedSorting =
        booleanPreferencesKey("fooddiary:meals_preferences:use_time_based_sorting")
    val ignoreAllDayMeals =
        booleanPreferencesKey("fooddiary:meals_preferences:ignore_all_day_meals")
    val collapsedMealCards =
        stringSetPreferencesKey("fooddiary:meals_preferences:collapsed_meal_cards")
    val displayedMacros =
        stringSetPreferencesKey("fooddiary:meals_preferences:displayed_macros")
    val showMacrosInFoodEntries =
        booleanPreferencesKey("fooddiary:meals_preferences:show_macros_in_food_entries")
}

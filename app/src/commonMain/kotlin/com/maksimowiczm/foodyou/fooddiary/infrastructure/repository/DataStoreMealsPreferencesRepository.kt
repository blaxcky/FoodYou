package com.maksimowiczm.foodyou.fooddiary.infrastructure.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.maksimowiczm.foodyou.common.infrastructure.datastore.AbstractDataStoreUserPreferencesRepository
import com.maksimowiczm.foodyou.fooddiary.domain.entity.CollapsedMealCard
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
        )

    override fun MutablePreferences.applyUserPreferences(updated: MealsPreferences) {
        this[MealsPreferencesDataStoreKeys.layout] = updated.layout.ordinal
        this[MealsPreferencesDataStoreKeys.useTimeBasedSorting] = updated.useTimeBasedSorting
        this[MealsPreferencesDataStoreKeys.ignoreAllDayMeals] = updated.ignoreAllDayMeals
        this[MealsPreferencesDataStoreKeys.collapsedMealCards] =
            encodeCollapsedMealCards(updated.collapsedMealCards)
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
}

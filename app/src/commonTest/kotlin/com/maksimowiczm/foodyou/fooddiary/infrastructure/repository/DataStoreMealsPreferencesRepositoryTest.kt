package com.maksimowiczm.foodyou.fooddiary.infrastructure.repository

import com.maksimowiczm.foodyou.fooddiary.domain.entity.CollapsedMealCard
import com.maksimowiczm.foodyou.fooddiary.domain.entity.MealCardMacro
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class DataStoreMealsPreferencesRepositoryTest {
    @Test
    fun displayedMacrosCodecRoundTripsFullPartialAndEmptySelections() {
        val selections =
            listOf(
                MealCardMacro.default,
                setOf(MealCardMacro.Proteins),
                setOf(MealCardMacro.Fats, MealCardMacro.Carbohydrates),
                emptySet(),
            )

        selections.forEach { macros ->
            assertEquals(macros, decodeDisplayedMacros(encodeDisplayedMacros(macros)))
        }
    }

    @Test
    fun displayedMacrosCodecUsesDefaultWhenPreferenceIsMissing() {
        assertEquals(MealCardMacro.default, decodeDisplayedMacros(null))
    }

    @Test
    fun displayedMacrosCodecIgnoresUnknownValues() {
        assertEquals(
            setOf(MealCardMacro.Proteins),
            decodeDisplayedMacros(setOf("Proteins", "Fiber", "not-a-macro")),
        )
    }

    @Test
    fun foodEntryMacrosDefaultToVisibleAndPreserveExplicitChoice() {
        assertTrue(decodeShowMacrosInFoodEntries(null))
        assertEquals(false, decodeShowMacrosInFoodEntries(false))
    }

    @Test
    fun collapsedMealCardsCodecRoundTrips() {
        val cards =
            setOf(
                CollapsedMealCard(LocalDate(2026, 6, 17), mealId = 1),
                CollapsedMealCard(LocalDate(2025, 12, 31), mealId = 42),
            )

        assertEquals(cards, decodeCollapsedMealCards(encodeCollapsedMealCards(cards)))
    }

    @Test
    fun collapsedMealCardsCodecIgnoresInvalidValues() {
        val valid = CollapsedMealCard(LocalDate(2026, 6, 17), mealId = 7)

        assertEquals(
            setOf(valid),
            decodeCollapsedMealCards(
                setOf(
                    "2026-06-17|7",
                    "not-a-date|1",
                    "2026-06-17|not-an-id",
                    "2026-06-17|0",
                    "2026-06-17|-1",
                    "2026-06-17",
                    "2026-06-17|1|extra",
                )
            ),
        )
    }
}

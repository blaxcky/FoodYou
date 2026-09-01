package com.maksimowiczm.foodyou.fooddiary.infrastructure.repository

import com.maksimowiczm.foodyou.fooddiary.domain.entity.CollapsedMealCard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate

class DataStoreMealsPreferencesRepositoryTest {
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

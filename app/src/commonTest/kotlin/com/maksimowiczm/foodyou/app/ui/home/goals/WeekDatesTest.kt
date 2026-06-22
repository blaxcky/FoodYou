package com.maksimowiczm.foodyou.app.ui.home.goals

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate

class WeekDatesTest {
    @Test
    fun currentWeekIncludesDaysThroughToday() {
        val dates = LocalDate(2026, 5, 20).weekDatesUntil(today = LocalDate(2026, 5, 20))

        assertEquals(
            listOf(
                LocalDate(2026, 5, 18),
                LocalDate(2026, 5, 19),
                LocalDate(2026, 5, 20),
            ),
            dates,
        )
    }

    @Test
    fun pastWeekIncludesAllDays() {
        val dates = LocalDate(2026, 5, 13).weekDatesUntil(today = LocalDate(2026, 5, 20))

        assertEquals(
            listOf(
                LocalDate(2026, 5, 11),
                LocalDate(2026, 5, 12),
                LocalDate(2026, 5, 13),
                LocalDate(2026, 5, 14),
                LocalDate(2026, 5, 15),
                LocalDate(2026, 5, 16),
                LocalDate(2026, 5, 17),
            ),
            dates,
        )
    }

    @Test
    fun futureWeekIsEmpty() {
        val dates = LocalDate(2026, 5, 25).weekDatesUntil(today = LocalDate(2026, 5, 20))

        assertEquals(emptyList(), dates)
    }
}

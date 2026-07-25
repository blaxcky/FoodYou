package com.maksimowiczm.foodyou.app.ui.home.goals

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate

class WeekDatesTest {
    @Test
    fun historicalSelectionUsesItsOwnDateAndOnlyEarlierWeekDays() {
        val dates =
            goalCalculationDates(
                selectedDate = LocalDate(2026, 5, 19),
                today = LocalDate(2026, 5, 24),
            )

        assertEquals(LocalDate(2026, 5, 19), dates.calculationDate)
        assertEquals(listOf(LocalDate(2026, 5, 18)), dates.previousDays)
        assertEquals(emptyList(), dates.plannedFutureDays)
    }

    @Test
    fun todayUsesEarlierDaysAndPlansTheRemainingWeek() {
        val today = LocalDate(2026, 5, 20)
        val dates = goalCalculationDates(selectedDate = today, today = today)

        assertEquals(today, dates.calculationDate)
        assertEquals(
            listOf(LocalDate(2026, 5, 18), LocalDate(2026, 5, 19)),
            dates.previousDays,
        )
        assertEquals(
            listOf(
                LocalDate(2026, 5, 21),
                LocalDate(2026, 5, 22),
                LocalDate(2026, 5, 23),
                LocalDate(2026, 5, 24),
            ),
            dates.plannedFutureDays,
        )
    }

    @Test
    fun futureSelectionUsesAllEarlierWeekDaysAndPlansFromSelectedDay() {
        val dates =
            goalCalculationDates(
                selectedDate = LocalDate(2026, 5, 23),
                today = LocalDate(2026, 5, 20),
            )

        assertEquals(LocalDate(2026, 5, 23), dates.calculationDate)
        assertEquals(
            listOf(
                LocalDate(2026, 5, 18),
                LocalDate(2026, 5, 19),
                LocalDate(2026, 5, 20),
                LocalDate(2026, 5, 21),
                LocalDate(2026, 5, 22),
            ),
            dates.previousDays,
        )
        assertEquals(
            listOf(LocalDate(2026, 5, 23), LocalDate(2026, 5, 24)),
            dates.plannedFutureDays,
        )
    }

    @Test
    fun selectionWithoutSharedWeekHasNoOptimizationDates() {
        val dates =
            goalCalculationDates(
                selectedDate = LocalDate(2026, 5, 25),
                today = LocalDate(2026, 5, 20),
            )

        assertEquals(LocalDate(2026, 5, 20), dates.calculationDate)
        assertEquals(emptyList(), dates.previousDays)
        assertEquals(emptyList(), dates.plannedFutureDays)
    }

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

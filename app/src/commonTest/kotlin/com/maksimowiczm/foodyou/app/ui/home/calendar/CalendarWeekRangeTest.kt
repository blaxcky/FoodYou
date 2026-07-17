package com.maksimowiczm.foodyou.app.ui.home.calendar

import androidx.compose.foundation.pager.PagerState
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

class CalendarWeekRangeTest {
    @Test
    fun everyDayMapsToItsCompleteMondayToSundayWeek() {
        val range = CalendarWeekRange(LocalDate(2024, 1, 1), selectableDayCount = 366)
        val monday = LocalDate(2024, 5, 13)

        (0L..6L).forEach { offset ->
            val page = range.pageFor(monday.plus(offset, DateTimeUnit.DAY))
            assertEquals(monday, range.firstDateForPage(page))
            assertEquals(
                monday.plus(6, DateTimeUnit.DAY),
                range.firstDateForPage(page).plus(6, DateTimeUnit.DAY),
            )
        }
    }

    @Test
    fun adjacentPagesMoveByExactlySevenDaysAcrossMonthAndYearBoundaries() {
        val range = CalendarWeekRange(LocalDate(2024, 1, 1), selectableDayCount = 800)
        val dates = listOf(LocalDate(2024, 12, 30), LocalDate(2025, 1, 6))

        assertEquals(1, range.pageFor(dates[1]) - range.pageFor(dates[0]))
        assertEquals(
            dates[0].plus(7, DateTimeUnit.DAY),
            range.firstDateForPage(range.pageFor(dates[0]) + 1),
        )
    }

    @Test
    fun firstAndLastWeeksIncludeDisabledOverflowDays() {
        val range = CalendarWeekRange(LocalDate(1970, 1, 1), selectableDayCount = 10)

        assertEquals(LocalDate(1969, 12, 29), range.firstDateForPage(0))
        assertEquals(LocalDate(1970, 1, 5), range.firstDateForPage(range.pageCount - 1))
        assertFalse(range.isSelectable(LocalDate(1969, 12, 31)))
        assertTrue(range.isSelectable(LocalDate(1970, 1, 1)))
        assertTrue(range.isSelectable(LocalDate(1970, 1, 10)))
        assertFalse(range.isSelectable(LocalDate(1970, 1, 18)))
    }

    @Test
    fun dateSelectionTargetsItsWeekAcrossMonthAndYearBoundaries() {
        val range = CalendarWeekRange(LocalDate(2023, 1, 1), selectableDayCount = 1_100)

        assertEquals(
            LocalDate(2024, 12, 30),
            range.firstDateForPage(range.pageFor(LocalDate(2025, 1, 1))),
        )
        assertEquals(
            LocalDate(2025, 3, 31),
            range.firstDateForPage(range.pageFor(LocalDate(2025, 4, 6))),
        )
    }

    @Test
    fun selectingADayDoesNotMoveTheVisibleWeek() {
        val range = CalendarWeekRange(LocalDate(2024, 1, 1), selectableDayCount = 366)
        val initialDate = LocalDate(2024, 5, 13)
        val initialPage = range.pageFor(initialDate)
        val pagerState =
            object : PagerState(initialPage) {
                override val pageCount = range.pageCount
            }
        val state =
            CalendarState(
                coroutineScope = CoroutineScope(EmptyCoroutineContext),
                namesOfDayOfWeek = emptyList(),
                pagerState = pagerState,
                weekRange = range,
                initialSelectedDate = initialDate,
                initialReferenceDate = initialDate,
            )

        state.onDateSelect(LocalDate(2024, 5, 19), scroll = false)

        assertEquals(LocalDate(2024, 5, 19), state.selectedDate)
        assertEquals(initialPage, pagerState.currentPage)
    }
}

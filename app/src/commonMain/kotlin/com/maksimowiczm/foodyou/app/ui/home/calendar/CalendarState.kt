package com.maksimowiczm.foodyou.app.ui.home.calendar

import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.SelectableDates
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.maksimowiczm.foodyou.common.extension.now
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.until

// 2106 seems reasonable for now
private const val DIARY_DAYS_COUNT = 50_000

@Composable
internal fun rememberCalendarState(
    namesOfDayOfWeek: List<String>,
    zeroDay: LocalDate = LocalDate.fromEpochDays(0),
    referenceDate: LocalDate = LocalDate.now(),
    selectedDate: LocalDate = referenceDate,
): CalendarState {
    val coroutineScope = rememberCoroutineScope()
    val weekRange = remember(zeroDay) { CalendarWeekRange(zeroDay, DIARY_DAYS_COUNT) }
    val pagerState =
        rememberPagerState(initialPage = weekRange.pageFor(selectedDate)) { weekRange.pageCount }

    return remember(namesOfDayOfWeek, referenceDate, selectedDate, pagerState, weekRange) {
        CalendarState(
            coroutineScope = coroutineScope,
            namesOfDayOfWeek = namesOfDayOfWeek,
            pagerState = pagerState,
            weekRange = weekRange,
            initialSelectedDate = selectedDate,
            initialReferenceDate = referenceDate,
        )
    }
}

@Stable
internal class CalendarState(
    private val coroutineScope: CoroutineScope,
    val namesOfDayOfWeek: List<String>,
    val pagerState: PagerState,
    val weekRange: CalendarWeekRange,
    initialSelectedDate: LocalDate = LocalDate.now(),
    initialReferenceDate: LocalDate = initialSelectedDate,
) {
    val referenceDate: LocalDate = initialReferenceDate
    val firstVisibleDate by derivedStateOf { weekRange.firstDateForPage(pagerState.currentPage) }

    private val visibleDates
        get() = firstVisibleDate..firstVisibleDate.plus(6, DateTimeUnit.DAY)

    var selectedDate by mutableStateOf(initialSelectedDate)
        private set

    fun onDateSelect(date: LocalDate, scroll: Boolean) {
        if (!weekRange.isSelectable(date)) return

        selectedDate = date

        if (scroll) {
            coroutineScope.launch { pagerState.scrollToPage(weekRange.pageFor(date)) }
        }
    }

    @Composable
    fun rememberDatePickerState(): DatePickerState {
        val yearRange = weekRange.firstSelectableDate.year..weekRange.lastSelectableDate.year

        val initialSelectedDateMillis =
            selectedDate.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds().takeIf { it >= 0 } ?: 0

        // Prefer a selected or reference date in the displayed week. Otherwise open the month of
        // the week's Monday, which also drives the card title.
        val initialDisplayedDate =
            when {
                selectedDate in visibleDates -> selectedDate
                referenceDate in visibleDates -> referenceDate
                else -> firstVisibleDate
            }
        val initialDisplayedMonthMillis =
            initialDisplayedDate.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds().takeIf {
                it >= 0
            } ?: 0

        return androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = initialSelectedDateMillis,
            initialDisplayedMonthMillis = initialDisplayedMonthMillis,
            yearRange = yearRange,
            selectableDates =
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                        val date =
                            Instant.fromEpochMilliseconds(utcTimeMillis)
                                .toLocalDateTime(TimeZone.UTC)
                                .date
                        return weekRange.isSelectable(date)
                    }

                    override fun isSelectableYear(year: Int) = year in yearRange
                },
        )
    }
}

internal class CalendarWeekRange(
    val firstSelectableDate: LocalDate,
    selectableDayCount: Int,
) {
    init {
        require(selectableDayCount > 0)
    }

    val lastSelectableDate =
        firstSelectableDate.plus(selectableDayCount.toLong() - 1, DateTimeUnit.DAY)
    val firstWeekStart = firstSelectableDate.startOfWeek()
    val pageCount =
        firstWeekStart.until(lastSelectableDate.startOfWeek(), DateTimeUnit.WEEK).toInt() + 1

    fun firstDateForPage(page: Int): LocalDate {
        require(page in 0 until pageCount)
        return firstWeekStart.plus(page.toLong(), DateTimeUnit.WEEK)
    }

    fun pageFor(date: LocalDate): Int {
        require(isSelectable(date))
        return firstWeekStart.until(date.startOfWeek(), DateTimeUnit.WEEK).toInt()
    }

    fun isSelectable(date: LocalDate) = date in firstSelectableDate..lastSelectableDate
}

internal fun LocalDate.startOfWeek(): LocalDate =
    plus((1 - dayOfWeek.isoDayNumber).toLong(), DateTimeUnit.DAY)

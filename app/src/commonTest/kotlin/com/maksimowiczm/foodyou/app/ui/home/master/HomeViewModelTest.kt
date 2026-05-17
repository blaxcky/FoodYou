package com.maksimowiczm.foodyou.app.ui.home.master

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate

class HomeViewModelTest {
    @Test
    fun healthConnectStepsSyncDatesIncludesLookbackWindowThroughToday() {
        val today = LocalDate(2026, 5, 17)

        val dates =
            healthConnectStepsSyncDates(
                selectedDate = today,
                today = today,
                lookbackDays = 3,
            )

        assertEquals(
            listOf(
                LocalDate(2026, 5, 14),
                LocalDate(2026, 5, 15),
                LocalDate(2026, 5, 16),
                LocalDate(2026, 5, 17),
            ),
            dates,
        )
    }

    @Test
    fun healthConnectStepsSyncDatesIncludesSelectedDateOutsideLookbackWindow() {
        val today = LocalDate(2026, 5, 17)
        val selectedDate = LocalDate(2026, 4, 1)

        val dates =
            healthConnectStepsSyncDates(
                selectedDate = selectedDate,
                today = today,
                lookbackDays = 3,
            )

        assertEquals(
            listOf(
                selectedDate,
                LocalDate(2026, 5, 14),
                LocalDate(2026, 5, 15),
                LocalDate(2026, 5, 16),
                LocalDate(2026, 5, 17),
            ),
            dates,
        )
    }
}

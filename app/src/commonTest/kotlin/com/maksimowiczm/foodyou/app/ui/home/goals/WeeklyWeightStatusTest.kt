package com.maksimowiczm.foodyou.app.ui.home.goals

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate

class WeeklyWeightStatusTest {
    private val today = LocalDate(2026, 10, 4)

    @Test
    fun loadingShowsNoWeightDetails() {
        assertNull(weeklyWeightStatus(LatestWeightState.Loading, today))
    }

    @Test
    fun missingMeasurementAsksToWeighIn() {
        assertEquals(
            WeeklyWeightStatus.Stale(daysAgo = null),
            weeklyWeightStatus(LatestWeightState.None, today),
        )
    }

    @Test
    fun measurementWithinTheLastWeekIsShown() {
        assertEquals(
            WeeklyWeightStatus.Recent(weightKg = 78.4, daysAgo = 6),
            weeklyWeightStatus(LatestWeightState.Measured(78.4, LocalDate(2026, 9, 28)), today),
        )
    }

    @Test
    fun measurementAWeekOldAsksToWeighIn() {
        assertEquals(
            WeeklyWeightStatus.Stale(daysAgo = 7),
            weeklyWeightStatus(LatestWeightState.Measured(78.4, LocalDate(2026, 9, 27)), today),
        )
    }

    @Test
    fun measurementDatedAfterTodayCountsAsToday() {
        assertEquals(
            WeeklyWeightStatus.Recent(weightKg = 78.4, daysAgo = 0),
            weeklyWeightStatus(LatestWeightState.Measured(78.4, LocalDate(2026, 10, 5)), today),
        )
    }
}

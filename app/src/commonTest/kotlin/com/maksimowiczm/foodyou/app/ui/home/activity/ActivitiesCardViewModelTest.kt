package com.maksimowiczm.foodyou.app.ui.home.activity

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class ActivitiesCardViewModelTest {
    @Test
    fun roundedActivityEnergyRoundsStepCaloriesLikeGoalsCard() {
        assertEquals(217, roundedActivityEnergyKcal(216.9))
    }

    @Test
    fun stepGoalDayComparesSelectedDateWithToday() {
        val today = LocalDate(2026, 10, 17)

        assertEquals(StepGoalDay.Past, stepGoalDay(LocalDate(2026, 10, 16), today))
        assertEquals(StepGoalDay.Today, stepGoalDay(today, today))
        assertEquals(StepGoalDay.Future, stepGoalDay(LocalDate(2026, 10, 18), today))
    }
}

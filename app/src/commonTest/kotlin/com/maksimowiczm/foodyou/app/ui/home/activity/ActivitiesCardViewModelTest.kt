package com.maksimowiczm.foodyou.app.ui.home.activity

import kotlin.test.Test
import kotlin.test.assertEquals

class ActivitiesCardViewModelTest {
    @Test
    fun roundedActivityEnergyRoundsStepCaloriesLikeGoalsCard() {
        assertEquals(217, roundedActivityEnergyKcal(216.9))
    }
}

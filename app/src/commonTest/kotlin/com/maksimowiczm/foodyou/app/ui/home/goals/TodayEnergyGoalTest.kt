package com.maksimowiczm.foodyou.app.ui.home.goals

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TodayEnergyGoalTest {
    @Test
    fun calculatesTodayGoalAndRemainingWithoutChangingBaseValues() {
        val values = todayEnergyGoalValues(2100.0, netEnergyKcal = 1200, reductionKcal = 500.0)

        assertEquals(1600, values?.goalKcal)
        assertEquals(400, values?.remainingKcal)
        assertEquals(900, 2100 - 1200)
    }

    @Test
    fun reportsTodayOverflowWhileBaseGoalStillHasEnergyLeft() {
        val values = todayEnergyGoalValues(2100.0, netEnergyKcal = 1700, reductionKcal = 500.0)

        assertEquals(-100, values?.remainingKcal)
        assertEquals(400, 2100 - 1700)
    }

    @Test
    fun validatesKcalAndKilojouleInput() {
        assertEquals(
            500.0,
            validatedTodayEnergyGoalReductionKcal("500", 2100.0) { it },
        )
        assertEquals(
            500.0,
            validatedTodayEnergyGoalReductionKcal("2092", 2100.0) { it / 4.184 },
        )
        assertNull(validatedTodayEnergyGoalReductionKcal("0", 2100.0) { it })
        assertNull(validatedTodayEnergyGoalReductionKcal("2101", 2100.0) { it })
        assertNull(validatedTodayEnergyGoalReductionKcal("abc", 2100.0) { it })
    }
}

package com.maksimowiczm.foodyou.app.ui.home.goals

import com.maksimowiczm.foodyou.settings.domain.entity.GoalDisplayMode as SettingsGoalDisplayMode
import kotlin.test.Test
import kotlin.test.assertEquals

class WeeklyEnergyGoalTest {

    @Test
    fun dietModeSubtractsDeficitAndAddsActivityEnergy() {
        assertEquals(
            1725,
            weeklyEnergyGoalKcal(
                goalDisplayMode = GoalDisplayMode.Diet,
                baseEnergyGoalKcal = 2000.0,
                burnedEnergyKcal = 225.0,
                dietEnergyDeficitKcal = 500.0,
            ),
        )
    }

    @Test
    fun dietModeUsesEachDatesEffectiveDeficitAndNormalGoalWhenMissing() {
        val deficits = listOf(500.0, 300.0, null)

        assertEquals(
            listOf(1600, 1800, 2100),
            deficits.map { deficit ->
                weeklyEnergyGoalKcal(
                    goalDisplayMode = GoalDisplayMode.Diet,
                    baseEnergyGoalKcal = 2000.0,
                    burnedEnergyKcal = 100.0,
                    dietEnergyDeficitKcal = deficit,
                )
            },
        )
    }

    @Test
    fun normalAndOptimizedModesKeepBaseGoal() {
        listOf(GoalDisplayMode.Normal, GoalDisplayMode.Optimized).forEach { mode ->
            assertEquals(
                2200,
                weeklyEnergyGoalKcal(
                    goalDisplayMode = mode,
                    baseEnergyGoalKcal = 2000.0,
                    burnedEnergyKcal = 200.0,
                    dietEnergyDeficitKcal = 500.0,
                ),
            )
        }
    }

    @Test
    fun unavailableDietModeFallsBackToNormalLikeDailyView() {
        assertEquals(
            GoalDisplayMode.Normal,
            SettingsGoalDisplayMode.Diet.selectedGoalDisplayMode(
                currentWeek = true,
                dietEnergyDeficitKcal = null,
            ),
        )
    }

    @Test
    fun roundingMatchesDailyGoalAndActivityRules() {
        assertEquals(
            1377,
            weeklyEnergyGoalKcal(
                goalDisplayMode = GoalDisplayMode.Diet,
                baseEnergyGoalKcal = 1200.4,
                burnedEnergyKcal = 176.5,
                dietEnergyDeficitKcal = 0.4,
            ),
        )
    }
}

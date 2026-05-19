package com.maksimowiczm.foodyou.app.ui.home.goals

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate

class GoalEnergyOptimizationTest {
    @Test
    fun mondaySurplusReducesTuesdayGoalAcrossRemainingWeek() {
        val goal =
            optimizedEnergyGoalKcal(
                selectedDate = LocalDate(2026, 5, 19),
                today = LocalDate(2026, 5, 19),
                baseEnergyGoalKcal = 2000.0,
                previousDays =
                    listOf(
                        GoalEnergyOptimizationDay(
                            consumedEnergyKcal = 2600.0,
                            baseEnergyGoalKcal = 2000.0,
                            burnedEnergyKcal = 0.0,
                        )
                    ),
            )

        assertEquals(1900.0, goal)
    }

    @Test
    fun noPreviousSurplusKeepsBaseGoal() {
        val goal =
            optimizedEnergyGoalKcal(
                selectedDate = LocalDate(2026, 5, 20),
                today = LocalDate(2026, 5, 20),
                baseEnergyGoalKcal = 2000.0,
                previousDays =
                    listOf(
                        GoalEnergyOptimizationDay(
                            consumedEnergyKcal = 1800.0,
                            baseEnergyGoalKcal = 2000.0,
                            burnedEnergyKcal = 0.0,
                        )
                    ),
            )

        assertEquals(2000.0, goal)
    }

    @Test
    fun previousBurnedActivityIncreasesSurplusBasis() {
        val goal =
            optimizedEnergyGoalKcal(
                selectedDate = LocalDate(2026, 5, 19),
                today = LocalDate(2026, 5, 19),
                baseEnergyGoalKcal = 2000.0,
                previousDays =
                    listOf(
                        GoalEnergyOptimizationDay(
                            consumedEnergyKcal = 2600.0,
                            baseEnergyGoalKcal = 2000.0,
                            burnedEnergyKcal = 300.0,
                        )
                    ),
            )

        assertEquals(1950.0, goal)
    }

    @Test
    fun sundayUsesFullPreviousWeekSurplus() {
        val goal =
            optimizedEnergyGoalKcal(
                selectedDate = LocalDate(2026, 5, 24),
                today = LocalDate(2026, 5, 24),
                baseEnergyGoalKcal = 2000.0,
                previousDays =
                    listOf(
                        GoalEnergyOptimizationDay(
                            consumedEnergyKcal = 2600.0,
                            baseEnergyGoalKcal = 2000.0,
                            burnedEnergyKcal = 0.0,
                        )
                    ),
            )

        assertEquals(1400.0, goal)
    }

    @Test
    fun optimizedGoalCanGoNegative() {
        val goal =
            optimizedEnergyGoalKcal(
                selectedDate = LocalDate(2026, 5, 24),
                today = LocalDate(2026, 5, 24),
                baseEnergyGoalKcal = 2000.0,
                previousDays =
                    listOf(
                        GoalEnergyOptimizationDay(
                            consumedEnergyKcal = 6000.0,
                            baseEnergyGoalKcal = 2000.0,
                            burnedEnergyKcal = 0.0,
                        )
                    ),
            )

        assertEquals(-2000.0, goal)
    }

    @Test
    fun nonCurrentWeekKeepsBaseGoal() {
        val goal =
            optimizedEnergyGoalKcal(
                selectedDate = LocalDate(2026, 5, 19),
                today = LocalDate(2026, 5, 26),
                baseEnergyGoalKcal = 2000.0,
                previousDays =
                    listOf(
                        GoalEnergyOptimizationDay(
                            consumedEnergyKcal = 2600.0,
                            baseEnergyGoalKcal = 2000.0,
                            burnedEnergyKcal = 0.0,
                        )
                    ),
            )

        assertEquals(2000.0, goal)
    }
}

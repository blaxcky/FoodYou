package com.maksimowiczm.foodyou.app.ui.home.goals

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate

class GoalEnergyOptimizationTest {
    @Test
    fun weeklyRemainingMatchesDailyRemainingWithFractionalActivityCalories() {
        val goal = 2000
        val netEnergy = roundedNetEnergyKcal(consumedEnergy = 2185.7, burnedEnergy = 216.9)
        val weeklyDay =
            WeekDaySummaryModel(
                date = LocalDate(2026, 5, 25),
                energy = netEnergy,
                goal = goal,
            )

        assertEquals(31, goal - netEnergy)
        assertEquals(goal - netEnergy, weeklyDay.goal - weeklyDay.energy)
    }

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
    fun adjustedGoalDoesNotDifferWhenRoundedValueMatchesBaseGoal() {
        assertEquals(
            false,
            energyGoalDiffersFromBase(
                baseEnergyGoalKcal = 2000.0,
                adjustedEnergyGoalKcal = 1999.6,
            ),
        )
    }

    @Test
    fun adjustedGoalDiffersWhenRoundedValueChangesFromBaseGoal() {
        assertEquals(
            true,
            energyGoalDiffersFromBase(
                baseEnergyGoalKcal = 2000.0,
                adjustedEnergyGoalKcal = 1999.4,
            ),
        )
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
    fun previousDeficitOffsetsPreviousSurplus() {
        val goal =
            optimizedEnergyGoalKcal(
                selectedDate = LocalDate(2026, 5, 20),
                today = LocalDate(2026, 5, 20),
                baseEnergyGoalKcal = 2000.0,
                previousDays =
                    listOf(
                        GoalEnergyOptimizationDay(
                            consumedEnergyKcal = 3812.0,
                            baseEnergyGoalKcal = 2000.0,
                            burnedEnergyKcal = 0.0,
                        ),
                        GoalEnergyOptimizationDay(
                            consumedEnergyKcal = -355.0,
                            baseEnergyGoalKcal = 2000.0,
                            burnedEnergyKcal = 0.0,
                        ),
                    ),
            )

        assertEquals(2000.0, goal)
    }

    @Test
    fun previousWeekDeficitDoesNotIncreaseOptimizedGoal() {
        val previousDays =
            buildList {
                repeat(5) {
                    add(
                        GoalEnergyOptimizationDay(
                            consumedEnergyKcal = 1000.0,
                            baseEnergyGoalKcal = 2000.0,
                            burnedEnergyKcal = 0.0,
                        )
                    )
                }
                add(
                    GoalEnergyOptimizationDay(
                        consumedEnergyKcal = 4000.0,
                        baseEnergyGoalKcal = 2000.0,
                        burnedEnergyKcal = 0.0,
                    )
                )
            }

        val goal =
            optimizedEnergyGoalKcal(
                selectedDate = LocalDate(2026, 5, 24),
                today = LocalDate(2026, 5, 24),
                baseEnergyGoalKcal = 2000.0,
                previousDays = previousDays,
            )

        assertEquals(2000.0, goal)
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
    fun negativeAdjustedGoalUsesBaseGoalForReachedPercentage() {
        val percentageGoal =
            percentageEnergyGoalKcal(energyGoalKcal = -500.0, baseEnergyGoalKcal = 2000.0)

        assertEquals(2000, percentageGoal)
        assertEquals(122, goalReachedPercentage(value = 2436, goal = percentageGoal))
    }

    @Test
    fun reachedPercentageCanExceedOneHundredForPositiveGoal() {
        val percentageGoal =
            percentageEnergyGoalKcal(energyGoalKcal = 2000.0, baseEnergyGoalKcal = 2000.0)

        assertEquals(2000, percentageGoal)
        assertEquals(115, goalReachedPercentage(value = 2300, goal = percentageGoal))
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

    @Test
    fun dietWithoutPreviousDaysUsesBaseGoalMinusDeficit() {
        val goal =
            adjustedEnergyGoalKcal(
                selectedDate = LocalDate(2026, 5, 18),
                today = LocalDate(2026, 5, 18),
                baseEnergyGoalKcal = 2000.0,
                dailyEnergyDeficitKcal = 500.0,
                previousDays = emptyList(),
            )

        assertEquals(1500.0, goal)
    }

    @Test
    fun dietMissedDeficitReducesRemainingDays() {
        val goal =
            adjustedEnergyGoalKcal(
                selectedDate = LocalDate(2026, 5, 19),
                today = LocalDate(2026, 5, 19),
                baseEnergyGoalKcal = 2000.0,
                dailyEnergyDeficitKcal = 500.0,
                previousDays =
                    listOf(
                        GoalEnergyOptimizationDay(
                            consumedEnergyKcal = 1800.0,
                            baseEnergyGoalKcal = 2000.0,
                            burnedEnergyKcal = 0.0,
                        )
                    ),
            )

        assertEquals(1450.0, goal)
    }

    @Test
    fun dietExtraSavingsIncreaseRemainingDays() {
        val goal =
            adjustedEnergyGoalKcal(
                selectedDate = LocalDate(2026, 5, 19),
                today = LocalDate(2026, 5, 19),
                baseEnergyGoalKcal = 2000.0,
                dailyEnergyDeficitKcal = 500.0,
                previousDays =
                    listOf(
                        GoalEnergyOptimizationDay(
                            consumedEnergyKcal = 1400.0,
                            baseEnergyGoalKcal = 2000.0,
                            burnedEnergyKcal = 0.0,
                        )
                    ),
            )

        assertEquals(1516.666666, goal, 0.000001)
    }

    @Test
    fun dietExtraSavingsCarryForwardAfterNextPlannedDeficit() {
        val goal =
            adjustedEnergyGoalKcal(
                selectedDate = LocalDate(2026, 5, 20),
                today = LocalDate(2026, 5, 20),
                baseEnergyGoalKcal = 2000.0,
                dailyEnergyDeficitKcal = 500.0,
                previousDays =
                    listOf(
                        GoalEnergyOptimizationDay(
                            consumedEnergyKcal = 1400.0,
                            baseEnergyGoalKcal = 2000.0,
                            burnedEnergyKcal = 0.0,
                        ),
                        GoalEnergyOptimizationDay(
                            consumedEnergyKcal = 1500.0,
                            baseEnergyGoalKcal = 2000.0,
                            burnedEnergyKcal = 0.0,
                        ),
                    ),
            )

        assertEquals(1520.0, goal)
    }

    @Test
    fun dietExtraSavingsAndLaterMissesUseWeeklyBalance() {
        val goal =
            adjustedEnergyGoalKcal(
                selectedDate = LocalDate(2026, 5, 20),
                today = LocalDate(2026, 5, 20),
                baseEnergyGoalKcal = 2000.0,
                dailyEnergyDeficitKcal = 500.0,
                previousDays =
                    listOf(
                        GoalEnergyOptimizationDay(
                            consumedEnergyKcal = 1400.0,
                            baseEnergyGoalKcal = 2000.0,
                            burnedEnergyKcal = 0.0,
                        ),
                        GoalEnergyOptimizationDay(
                            consumedEnergyKcal = 1800.0,
                            baseEnergyGoalKcal = 2000.0,
                            burnedEnergyKcal = 0.0,
                        ),
                    ),
            )

        assertEquals(1460.0, goal)
    }

    @Test
    fun dietExtraSavingsDoNotIncreaseGoalAboveBaseGoal() {
        val goal =
            adjustedEnergyGoalKcal(
                selectedDate = LocalDate(2026, 5, 19),
                today = LocalDate(2026, 5, 19),
                baseEnergyGoalKcal = 2000.0,
                dailyEnergyDeficitKcal = 500.0,
                previousDays =
                    listOf(
                        GoalEnergyOptimizationDay(
                            consumedEnergyKcal = 0.0,
                            baseEnergyGoalKcal = 2000.0,
                            burnedEnergyKcal = 3000.0,
                        )
                    ),
            )

        assertEquals(2000.0, goal)
    }

    @Test
    fun dietPreviousBurnedActivityIncreasesDeficitBasis() {
        val goal =
            adjustedEnergyGoalKcal(
                selectedDate = LocalDate(2026, 5, 19),
                today = LocalDate(2026, 5, 19),
                baseEnergyGoalKcal = 2000.0,
                dailyEnergyDeficitKcal = 500.0,
                previousDays =
                    listOf(
                        GoalEnergyOptimizationDay(
                            consumedEnergyKcal = 1800.0,
                            baseEnergyGoalKcal = 2000.0,
                            burnedEnergyKcal = 300.0,
                        )
                    ),
            )

        assertEquals(1500.0, goal)
    }

    @Test
    fun dietNonCurrentWeekKeepsBaseGoal() {
        val goal =
            adjustedEnergyGoalKcal(
                selectedDate = LocalDate(2026, 5, 19),
                today = LocalDate(2026, 5, 26),
                baseEnergyGoalKcal = 2000.0,
                dailyEnergyDeficitKcal = 500.0,
                previousDays = emptyList(),
            )

        assertEquals(2000.0, goal)
    }
}

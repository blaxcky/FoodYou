package com.maksimowiczm.foodyou.app.ui.home.goals

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate

class GoalEnergyOptimizationTest {
    @Test
    fun weeklyRemainingUsesConsumedEnergyAndActivityAdjustedGoal() {
        val eaten = roundedEnergyKcal(2185.7)
        val goal = roundedEnergyKcal(1999.6) + roundedEnergyKcal(216.9)
        val weeklyDay =
            WeekDaySummaryModel(
                date = LocalDate(2026, 5, 25),
                energy = eaten,
                goal = goal,
            )

        assertEquals(31, goal - eaten)
        assertEquals(goal - eaten, weeklyDay.goal - weeklyDay.energy)
    }

    @Test
    fun weeklyActivityIncreasesGoalWithoutReducingConsumedEnergy() {
        val weeklyDay =
            WeekDaySummaryModel(
                date = LocalDate(2026, 5, 25),
                energy = roundedEnergyKcal(0.0),
                goal = roundedEnergyKcal(2100.0) + roundedEnergyKcal(300.4),
            )

        assertEquals(0, weeklyDay.energy)
        assertEquals(2400, weeklyDay.goal)
        assertEquals(2400, weeklyDay.goal - weeklyDay.energy)
    }

    @Test
    fun roundedRemainingEnergyUsesDisplayRoundedGoalAndNetEnergy() {
        val netEnergy = roundedNetEnergyKcal(consumedEnergy = 824.2, burnedEnergy = 0.0)

        assertEquals(1176, roundedRemainingEnergyKcal(energyGoalKcal = 1999.6, netEnergy))
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
    fun calorieProgressForPositiveGoalShowsReachedPercentageWithoutOverflow() {
        val progress =
            calorieGoalProgress(netEnergy = 322, energyGoal = 2100, percentageEnergyGoal = 2100)

        assertEquals(0.153f, progress.progress, absoluteTolerance = 0.001f)
        assertEquals(0f, progress.overflowProgress)
        assertEquals(15, progress.reachedPercentage)
    }

    @Test
    fun calorieProgressForPositiveGoalShowsOverflow() {
        val progress =
            calorieGoalProgress(netEnergy = 2300, energyGoal = 2100, percentageEnergyGoal = 2100)

        assertEquals(1f, progress.progress)
        assertEquals(0.095f, progress.overflowProgress, absoluteTolerance = 0.001f)
        assertEquals(110, progress.reachedPercentage)
    }

    @Test
    fun calorieProgressForNegativeGoalShowsFullOverflowForPositiveNetEnergy() {
        val progress =
            calorieGoalProgress(netEnergy = 322, energyGoal = -2100, percentageEnergyGoal = 2100)

        assertEquals(0f, progress.progress)
        assertEquals(1f, progress.overflowProgress)
        assertEquals(0, progress.reachedPercentage)
    }

    @Test
    fun calorieProgressForNegativeGoalShowsProgressTowardDeficit() {
        val progress =
            calorieGoalProgress(netEnergy = -1050, energyGoal = -2100, percentageEnergyGoal = 2100)

        assertEquals(0.5f, progress.progress)
        assertEquals(0.5f, progress.overflowProgress)
        assertEquals(50, progress.reachedPercentage)
    }

    @Test
    fun calorieProgressForNegativeGoalIsCompleteAtTarget() {
        val progress =
            calorieGoalProgress(netEnergy = -2100, energyGoal = -2100, percentageEnergyGoal = 2100)

        assertEquals(1f, progress.progress)
        assertEquals(0f, progress.overflowProgress)
        assertEquals(100, progress.reachedPercentage)
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

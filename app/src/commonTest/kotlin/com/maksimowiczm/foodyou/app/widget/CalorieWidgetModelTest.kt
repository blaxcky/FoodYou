package com.maksimowiczm.foodyou.app.widget

import com.maksimowiczm.foodyou.app.ui.home.goals.GoalEnergyOptimizationDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate

class CalorieWidgetModelTest {
    @Test
    fun plannedLockedDayUsesFixedSurplusForOptimizedAndDietGoals() {
        val model =
            calorieWidgetModel(
                today = LocalDate(2026, 7, 27),
                eatenKcal = 1_000.0,
                burnedKcal = 0.0,
                baseGoalKcal = 2_000.0,
                dietEnergyDeficitKcal = 500.0,
                previousDays = emptyList(),
                plannedFutureDays =
                    listOf(
                        GoalEnergyOptimizationDay(
                            date = LocalDate(2026, 7, 28),
                            consumedEnergyKcal = 0.0,
                            baseEnergyGoalKcal = 2_000.0,
                            burnedEnergyKcal = 700.0,
                            lockedSurplusKcal = 500.0,
                        )
                    ),
            )

        assertEquals(917, model.optimizedLeftKcal)
        assertEquals(333, model.dietLeftKcal)
    }


    @Test
    fun todayAdjustmentChangesOnlyNormalRemainingEnergy() {
        val baseline =
            calorieWidgetModel(
                today = LocalDate(2026, 7, 17),
                eatenKcal = 1200.0,
                burnedKcal = 0.0,
                baseGoalKcal = 2100.0,
                dietEnergyDeficitKcal = 300.0,
                previousDays = emptyList(),
            )
        val adjusted =
            calorieWidgetModel(
                today = LocalDate(2026, 7, 17),
                eatenKcal = 1200.0,
                burnedKcal = 0.0,
                baseGoalKcal = 2100.0,
                dietEnergyDeficitKcal = 300.0,
                todayEnergyGoalReductionKcal = 500.0,
                previousDays = emptyList(),
            )

        assertEquals(900, baseline.normalLeftKcal)
        assertEquals(400, adjusted.normalLeftKcal)
        assertEquals(baseline.optimizedLeftKcal, adjusted.optimizedLeftKcal)
        assertEquals(baseline.dietLeftKcal, adjusted.dietLeftKcal)
    }

    @Test
    fun netEnergyUsesTheIndividuallyRoundedDisplayedValues() {
        val model =
            calorieWidgetModel(
                today = LocalDate(2026, 5, 20),
                eatenKcal = 1999.49,
                burnedKcal = 0.51,
                baseGoalKcal = 1809.0,
                dietEnergyDeficitKcal = null,
                previousDays = emptyList(),
            )

        assertEquals(1999, model.eatenKcal)
        assertEquals(1, model.burnedKcal)
        assertEquals(-189, model.normalLeftKcal)
    }

    @Test
    fun calculatesAllRemainingEnergyModesFromSameNetEnergy() {
        val model =
            calorieWidgetModel(
                today = LocalDate(2026, 5, 20),
                eatenKcal = 1520.6,
                burnedKcal = 120.2,
                baseGoalKcal = 2000.0,
                dietEnergyDeficitKcal = 500.0,
                previousDays =
                    listOf(
                        GoalEnergyOptimizationDay(
                            consumedEnergyKcal = 2600.0,
                            baseEnergyGoalKcal = 2000.0,
                            burnedEnergyKcal = 0.0,
                        ),
                        GoalEnergyOptimizationDay(
                            consumedEnergyKcal = 1800.0,
                            baseEnergyGoalKcal = 2000.0,
                            burnedEnergyKcal = 100.0,
                        ),
                    ),
            )

        assertEquals(599, model.normalLeftKcal)
        assertEquals(539, model.optimizedLeftKcal)
        assertEquals(-161, model.dietLeftKcal)
    }

    @Test
    fun normalRemainingEnergyUsesRoundedGoalAndRoundedNetEnergy() {
        val model =
            calorieWidgetModel(
                today = LocalDate(2026, 5, 20),
                eatenKcal = 1520.6,
                burnedKcal = 120.2,
                baseGoalKcal = 2000.6,
                dietEnergyDeficitKcal = null,
                previousDays = emptyList(),
            )

        assertEquals(600, model.normalLeftKcal)
    }

    @Test
    fun hidesOptimizedRemainingEnergyWhenRoundedAdjustedGoalMatchesNormalGoal() {
        val model =
            calorieWidgetModel(
                today = LocalDate(2026, 5, 20),
                eatenKcal = 824.2,
                burnedKcal = 0.0,
                baseGoalKcal = 2000.0,
                dietEnergyDeficitKcal = null,
                previousDays =
                    listOf(
                        GoalEnergyOptimizationDay(
                            consumedEnergyKcal = 2002.0,
                            baseEnergyGoalKcal = 2000.0,
                            burnedEnergyKcal = 0.0,
                        )
                    ),
            )

        assertEquals(1176, model.normalLeftKcal)
        assertNull(model.optimizedLeftKcal)
    }

    @Test
    fun dietRemainingEnergyUsesRoundedAdjustedGoalAndRoundedNetEnergy() {
        val model =
            calorieWidgetModel(
                today = LocalDate(2026, 5, 18),
                eatenKcal = 824.2,
                burnedKcal = 0.0,
                baseGoalKcal = 2000.0,
                dietEnergyDeficitKcal = 0.4,
                previousDays = emptyList(),
            )

        assertEquals(1176, model.dietLeftKcal)
    }

    @Test
    fun hidesOptimizedRemainingEnergyWhenItMatchesNormalRemainingEnergy() {
        val model =
            calorieWidgetModel(
                today = LocalDate(2026, 5, 20),
                eatenKcal = 1200.0,
                burnedKcal = 100.0,
                baseGoalKcal = 2000.0,
                dietEnergyDeficitKcal = null,
                previousDays = emptyList(),
            )

        assertEquals(900, model.normalLeftKcal)
        assertNull(model.optimizedLeftKcal)
    }

    @Test
    fun roundsEatenAndBurnedOnceForDisplay() {
        val model =
            calorieWidgetModel(
                today = LocalDate(2026, 5, 20),
                eatenKcal = 1520.6,
                burnedKcal = 120.2,
                baseGoalKcal = 2000.0,
                dietEnergyDeficitKcal = 500.0,
                previousDays = emptyList(),
            )

        assertEquals(1521, model.eatenKcal)
        assertEquals(120, model.burnedKcal)
    }

    @Test
    fun missingDietDeficitProducesNoDietValue() {
        val model =
            calorieWidgetModel(
                today = LocalDate(2026, 5, 20),
                eatenKcal = 1200.0,
                burnedKcal = 100.0,
                baseGoalKcal = 2000.0,
                dietEnergyDeficitKcal = null,
                previousDays = emptyList(),
            )

        assertNull(model.dietLeftKcal)
    }

    @Test
    fun keepsTodayAsWidgetDate() {
        val today = LocalDate(2026, 5, 25)

        val model =
            calorieWidgetModel(
                today = today,
                eatenKcal = 0.0,
                burnedKcal = 0.0,
                baseGoalKcal = 2000.0,
                dietEnergyDeficitKcal = null,
                previousDays = emptyList(),
            )

        assertEquals(today, model.date)
    }

    @Test
    fun plannedSurplusUsesTheSameOptimizedAndDietBudgetAsTheHomeScreen() {
        val model =
            calorieWidgetModel(
                today = LocalDate(2026, 5, 18),
                eatenKcal = 0.0,
                burnedKcal = 0.0,
                baseGoalKcal = 2000.0,
                dietEnergyDeficitKcal = 500.0,
                previousDays = emptyList(),
                plannedFutureDays =
                    listOf(
                        GoalEnergyOptimizationDay(
                            consumedEnergyKcal = 3000.0,
                            baseEnergyGoalKcal = 2000.0,
                            burnedEnergyKcal = 0.0,
                        )
                    ),
            )

        assertEquals(1833, model.optimizedLeftKcal)
        assertEquals(1250, model.dietLeftKcal)
    }
}

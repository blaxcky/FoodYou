package com.maksimowiczm.foodyou.activity.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StepGoalProgressTest {
    @Test
    fun noGoalMeansNoProgress() {
        assertNull(stepGoalProgress(countedSteps = 1_000, dailyStepGoal = null, kcalPerStep = 0.04))
        assertNull(stepGoalProgress(countedSteps = 1_000, dailyStepGoal = 0, kcalPerStep = 0.04))
    }

    @Test
    fun remainingStepsEarnEnergy() {
        val progress = stepGoalProgress(countedSteps = 1_528, dailyStepGoal = 8_000, kcalPerStep = 0.04)!!

        assertEquals(6_472, progress.remainingSteps)
        assertEquals(258.88, progress.remainingEnergyKcal!!, 0.0001)
        assertEquals(0.191f, progress.fraction, 0.001f)
        assertFalse(progress.reached)
    }

    @Test
    fun reachedGoalCapsProgress() {
        val progress = stepGoalProgress(countedSteps = 9_500, dailyStepGoal = 8_000, kcalPerStep = 0.04)!!

        assertEquals(0, progress.remainingSteps)
        assertEquals(0.0, progress.remainingEnergyKcal)
        assertEquals(1f, progress.fraction)
        assertTrue(progress.reached)
    }

    @Test
    fun missingKcalPerStepOmitsEnergy() {
        assertNull(stepGoalProgress(1_000, 8_000, kcalPerStep = null)!!.remainingEnergyKcal)
        assertNull(stepGoalProgress(1_000, 8_000, kcalPerStep = 0.0)!!.remainingEnergyKcal)
    }

    @Test
    fun weeklySurplusConvertsToStepsRoundedUp() {
        val steps = surplusOffsetSteps(surplusKcal = 7_054, kcalPerStep = 0.04, remainingDays = 2)!!

        assertEquals(176_400, steps.totalSteps)
        assertEquals(88_200, steps.stepsPerDay)
    }

    @Test
    fun weeklySurplusOnLastDayHasNoDailySplit() {
        assertNull(surplusOffsetSteps(7_054, 0.04, remainingDays = 1)!!.stepsPerDay)
        assertNull(surplusOffsetSteps(7_054, 0.04, remainingDays = 0)!!.stepsPerDay)
    }

    @Test
    fun noSurplusOrKcalPerStepMeansNoSteps() {
        assertNull(surplusOffsetSteps(surplusKcal = 0, kcalPerStep = 0.04, remainingDays = 3))
        assertNull(surplusOffsetSteps(surplusKcal = -500, kcalPerStep = 0.04, remainingDays = 3))
        assertNull(surplusOffsetSteps(surplusKcal = 500, kcalPerStep = null, remainingDays = 3))
    }
}

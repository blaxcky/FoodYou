package com.maksimowiczm.foodyou.weight.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals

class WeightProgressTest {
    @Test
    fun calculatesLossProgress() {
        assertEquals(0.5f, calculateWeightGoalProgress(90.0, 85.0, 80.0))
    }

    @Test
    fun calculatesGainProgress() {
        assertEquals(0.5f, calculateWeightGoalProgress(60.0, 65.0, 70.0))
    }

    @Test
    fun missingGoalHasNoProgress() {
        assertEquals(null, calculateWeightGoalProgress(90.0, 85.0, null))
    }

    @Test
    fun positionIsNegativeWhenMovingAwayFromTarget() {
        assertEquals(-0.5, calculateWeightGoalPosition(90.0, 95.0, 80.0))
    }

    @Test
    fun positionOvershootsReachedTarget() {
        assertEquals(1.25, calculateWeightGoalPosition(60.0, 65.0, 64.0))
    }

    @Test
    fun positionWithEqualStartAndTargetIsOnlyDefinedWhenReached() {
        assertEquals(1.0, calculateWeightGoalPosition(80.0, 80.0, 80.0))
        assertEquals(null, calculateWeightGoalPosition(80.0, 82.0, 80.0))
    }
}

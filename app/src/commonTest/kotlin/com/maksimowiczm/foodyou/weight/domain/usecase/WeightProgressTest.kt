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
}

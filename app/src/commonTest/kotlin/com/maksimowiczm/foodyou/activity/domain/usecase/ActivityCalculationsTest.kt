package com.maksimowiczm.foodyou.activity.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals

class ActivityCalculationsTest {
    @Test
    fun netEnergySubtractsBurnedEnergy() {
        assertEquals(1550.0, calculateNetEnergyKcal(consumedKcal = 2000.0, burnedKcal = 450.0))
    }

    @Test
    fun stepEnergyUsesConfiguredKcalPerStep() {
        assertEquals(40.0, calculateStepEnergyKcal(steps = 10_000, kcalPerStep = 0.004))
    }

    @Test
    fun stepEnergyIsZeroUntilUserConfiguresFactor() {
        assertEquals(0.0, calculateStepEnergyKcal(steps = 10_000, kcalPerStep = null))
    }
}

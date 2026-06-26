package com.maksimowiczm.foodyou.activity.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

    @Test
    fun discountedActivityEnergyAppliesPercentDiscount() {
        assertEquals(
            400.0,
            calculateDiscountedActivityEnergyKcal(energyKcal = 500.0, discountPercent = 20.0),
        )
    }

    @Test
    fun discountedActivityEnergyWithZeroDiscountKeepsEnergy() {
        assertEquals(
            500.0,
            calculateDiscountedActivityEnergyKcal(energyKcal = 500.0, discountPercent = 0.0),
        )
    }

    @Test
    fun completeActivityDiscountPercentAcceptsCommaDecimalNumber() {
        assertEquals(20.5, "20,5".toCompleteActivityDiscountPercentOrNull())
    }

    @Test
    fun completeActivityDiscountPercentRejectsIncompleteInput() {
        assertNull("20,".toCompleteActivityDiscountPercentOrNull())
    }

    @Test
    fun completeActivityDiscountPercentRejectsValuesOutsidePercentRange() {
        assertNull("-1".toCompleteActivityDiscountPercentOrNull())
        assertNull("100.1".toCompleteActivityDiscountPercentOrNull())
    }
}

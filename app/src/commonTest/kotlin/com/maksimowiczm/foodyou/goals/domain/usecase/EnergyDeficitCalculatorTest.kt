package com.maksimowiczm.foodyou.goals.domain.usecase

import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals

class EnergyDeficitCalculatorTest {
    @Test
    fun halfAKilogramPerWeekNeeds550KcalPerDay() {
        assertEquals(550, dailyEnergyDeficitKcal(0.5).roundToInt())
    }

    @Test
    fun oneKilogramPerWeekNeeds1100KcalPerDay() {
        assertEquals(1100, dailyEnergyDeficitKcal(1.0).roundToInt())
    }

    @Test
    fun dailyDeficitConvertsBackToWeeklyWeightLoss() {
        assertEquals(0.5, weeklyWeightLossKg(550.0), absoluteTolerance = 1e-9)
    }
}

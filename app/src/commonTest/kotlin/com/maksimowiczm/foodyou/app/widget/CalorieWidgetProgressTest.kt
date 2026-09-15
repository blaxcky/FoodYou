package com.maksimowiczm.foodyou.app.widget

import kotlin.test.Test
import kotlin.test.assertEquals

class CalorieWidgetProgressTest {
    @Test
    fun underGoal() = assertEquals(CalorieWidgetProgress(0.5f, 0f), calorieWidgetProgress(1000, 2000))

    @Test
    fun exactlyAtGoal() = assertEquals(CalorieWidgetProgress(1f, 0f), calorieWidgetProgress(2000, 2000))

    @Test
    fun overGoal() = assertEquals(CalorieWidgetProgress(1f, 0.25f), calorieWidgetProgress(2500, 2000))

    @Test
    fun overflowIsClamped() =
        assertEquals(CalorieWidgetProgress(1f, 1f), calorieWidgetProgress(9000, 2000))

    @Test
    fun negativeNetShowsNoProgress() =
        assertEquals(CalorieWidgetProgress(0f, 0f), calorieWidgetProgress(-100, 2000))

    @Test
    fun zeroGoalDoesNotDivideByZero() =
        assertEquals(CalorieWidgetProgress(1f, 1f), calorieWidgetProgress(500, 0))
}

package com.maksimowiczm.foodyou.app.widget.ring

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals

class CalorieRingLayoutSpecTest {
    @Test
    fun minimumSizeShowsOnlyRingAndSideBySideMetrics() {
        val spec = calorieRingLayoutSpec(DpSize(250.dp, 110.dp))

        assertEquals(GoalRowsMode.None, spec.goalRows)
        assertEquals(10.dp, spec.padding)
        assertEquals(14.dp, spec.horizontalPadding)
        assertEquals(66.dp, spec.ring)
        assertEquals(16.sp, spec.metricValueTextSize)
    }

    @Test
    fun typicalCellShowsGoalBarsWithMediumMetrics() {
        val spec = calorieRingLayoutSpec(DpSize(320.dp, 170.dp))

        assertEquals(GoalRowsMode.Bars, spec.goalRows)
        assertEquals(12.dp, spec.padding)
        assertEquals(16.dp, spec.horizontalPadding)
        assertEquals(70.dp, spec.ring)
        assertEquals(20.sp, spec.metricValueTextSize)
    }

    @Test
    fun lowCellShowsInlineGoals() {
        assertEquals(GoalRowsMode.Inline, calorieRingLayoutSpec(DpSize(320.dp, 140.dp)).goalRows)
    }

    @Test
    fun goalRowModeThresholds() {
        assertEquals(GoalRowsMode.None, calorieRingLayoutSpec(DpSize(320.dp, 133.dp)).goalRows)
        assertEquals(GoalRowsMode.Inline, calorieRingLayoutSpec(DpSize(320.dp, 134.dp)).goalRows)
        assertEquals(GoalRowsMode.Inline, calorieRingLayoutSpec(DpSize(320.dp, 157.dp)).goalRows)
        assertEquals(GoalRowsMode.Bars, calorieRingLayoutSpec(DpSize(320.dp, 158.dp)).goalRows)
    }

    /** At the mode thresholds the minimum ring must still fit into the hero area. */
    @Test
    fun ringFitsHeroAtThresholds() {
        assertEquals(58.dp, calorieRingLayoutSpec(DpSize(320.dp, 134.dp)).ring)
        assertEquals(58.dp, calorieRingLayoutSpec(DpSize(320.dp, 158.dp)).ring)
    }

    @Test
    fun tallCellShowsGoalBars() {
        val spec = calorieRingLayoutSpec(DpSize(320.dp, 240.dp))

        assertEquals(GoalRowsMode.Bars, spec.goalRows)
        assertEquals(134.4f, spec.ring.value, 0.01f)
        assertEquals(22.sp, spec.metricValueTextSize)
    }

    @Test
    fun ringIsLimitedByWidthAndCappedAtMaximum() {
        assertEquals(105.dp, calorieRingLayoutSpec(DpSize(250.dp, 400.dp)).ring)
        assertEquals(140.dp, calorieRingLayoutSpec(DpSize(500.dp, 400.dp)).ring)
    }
}

package com.maksimowiczm.foodyou.app.widget.ring

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CalorieRingLayoutSpecTest {
    @Test
    fun minimumSizeShowsOnlyRingAndSideBySideMetrics() {
        val spec = calorieRingLayoutSpec(DpSize(250.dp, 110.dp))

        assertEquals(GoalRowsMode.None, spec.goalRows)
        assertFalse(spec.metricsStacked)
        assertEquals(66.dp, spec.ring)
    }

    @Test
    fun typicalCellShowsGoalBarsWithStackedMetrics() {
        val spec = calorieRingLayoutSpec(DpSize(320.dp, 170.dp))

        assertEquals(GoalRowsMode.Bars, spec.goalRows)
        assertTrue(spec.metricsStacked)
        assertEquals(78.dp, spec.ring)
        assertEquals(16.sp, spec.metricValueTextSize)
    }

    @Test
    fun lowCellShowsInlineGoals() {
        assertEquals(GoalRowsMode.Inline, calorieRingLayoutSpec(DpSize(320.dp, 140.dp)).goalRows)
    }

    @Test
    fun tallCellShowsGoalBars() {
        val spec = calorieRingLayoutSpec(DpSize(320.dp, 240.dp))

        assertEquals(GoalRowsMode.Bars, spec.goalRows)
        assertTrue(spec.metricsStacked)
        assertEquals(134.4f, spec.ring.value, 0.01f)
        assertEquals(20.sp, spec.metricValueTextSize)
    }

    @Test
    fun ringIsLimitedByWidthAndCappedAtMaximum() {
        assertEquals(105.dp, calorieRingLayoutSpec(DpSize(250.dp, 400.dp)).ring)
        assertEquals(140.dp, calorieRingLayoutSpec(DpSize(500.dp, 400.dp)).ring)
    }
}

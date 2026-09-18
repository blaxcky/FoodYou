package com.maksimowiczm.foodyou.app.widget.ring

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RingArcsTest {
    @Test
    fun progressWithoutOverflowRunsClockwiseFromBottomLeft() {
        val arcs = ringArcs(progress = 0.5f, overflow = 0f)

        assertEquals(135f, arcs.progressStartDeg)
        assertEquals(135f, arcs.progressSweepDeg)
        assertFalse(arcs.hasGap)
        assertFalse(arcs.hasOverflow)
    }

    @Test
    fun overflowShareTakesTheEndOfTheRingWithAGap() {
        val arcs = ringArcs(progress = 1f, overflow = 0.25f)

        assertTrue(arcs.hasOverflow)
        assertEquals(135f, arcs.progressStartDeg)
        assertEquals(202.5f - 2.5f, arcs.progressSweepDeg)
        assertTrue(arcs.hasGap)
        assertEquals(335f, arcs.gapStartDeg)
        assertEquals(2.5f, arcs.gapSweepDeg)
        assertEquals(337.5f, arcs.overflowStartDeg)
        assertEquals(67.5f, arcs.overflowSweepDeg)
    }

    @Test
    fun fullOverflowFillsTheRingWithoutGap() {
        val arcs = ringArcs(progress = 1f, overflow = 1f)

        assertFalse(arcs.hasProgress)
        assertFalse(arcs.hasGap)
        assertEquals(135f, arcs.overflowStartDeg)
        assertEquals(270f, arcs.overflowSweepDeg)
    }
}

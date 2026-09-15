package com.maksimowiczm.foodyou.app.widget.ring

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RingArcsTest {
    @Test
    fun progressWithoutOverflowHasNoGap() {
        val arcs = ringArcs(progress = 0.5f, overflow = 0f)

        assertEquals(-90f, arcs.progressStartDeg)
        assertEquals(180f, arcs.progressSweepDeg)
        assertFalse(arcs.hasOverflow)
    }

    @Test
    fun overflowShrinksProgressAndLeavesGaps() {
        val arcs = ringArcs(progress = 1f, overflow = 0.25f)

        assertTrue(arcs.hasProgress)
        assertTrue(arcs.hasOverflow)
        assertEquals(270f - 2.5f, arcs.progressSweepDeg)
        assertEquals(-180f, arcs.overflowStartDeg)
        assertEquals(90f - 2.5f, arcs.overflowSweepDeg)
    }

    @Test
    fun fullOverflowFillsTheRingInErrorColor() {
        val arcs = ringArcs(progress = 1f, overflow = 1f)

        assertFalse(arcs.hasProgress)
        assertEquals(360f, arcs.overflowSweepDeg)
    }
}

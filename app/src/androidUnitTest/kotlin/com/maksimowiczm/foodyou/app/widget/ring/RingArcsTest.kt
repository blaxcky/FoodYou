package com.maksimowiczm.foodyou.app.widget.ring

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RingArcsTest {
    @Test
    fun progressWithoutOverflowRunsClockwiseFromTheTop() {
        val arcs = ringArcs(progress = 0.5f, overflow = 0f)

        assertEquals(-90f, arcs.progressStartDeg)
        assertEquals(180f, arcs.progressSweepDeg)
        assertFalse(arcs.hasOverflow)
    }

    @Test
    fun overflowShareStartsAtTheTop() {
        val arcs = ringArcs(progress = 1f, overflow = 0.25f)

        assertTrue(arcs.hasOverflow)
        assertEquals(-90f, arcs.overflowStartDeg)
        assertEquals(90f, arcs.overflowSweepDeg)
    }

    @Test
    fun fullOverflowFillsTheRing() {
        val arcs = ringArcs(progress = 1f, overflow = 1f)

        assertEquals(360f, arcs.overflowSweepDeg)
    }
}

package com.maksimowiczm.foodyou.app.widget.ring

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.After
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetRingRendererTest {
    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun trackCoversTheWholeRingButNotTheCenter() {
        val bitmap = WidgetRingRenderer.track(sizePx = 100, strokePx = 10f)

        assertEquals(100, bitmap.width)
        assertEquals(100, bitmap.height)
        assertEquals(Bitmap.Config.ARGB_8888, bitmap.config)
        assertTrue(bitmap.alphaAt(50, 5) > 200)
        assertTrue(bitmap.alphaAt(95, 50) > 200)
        assertTrue(bitmap.alphaAt(50, 95) > 200)
        assertTrue(bitmap.alphaAt(5, 50) > 200)
        assertEquals(0, bitmap.alphaAt(50, 50))
    }

    @Test
    fun progressArcRunsClockwiseFromTheTop() {
        val bitmap =
            WidgetRingRenderer.arc(
                sizePx = 100,
                strokePx = 10f,
                startDeg = -90f,
                sweepDeg = 180f,
                roundCaps = false,
            )

        assertTrue(bitmap.alphaAt(95, 50) > 200)
        assertEquals(0, bitmap.alphaAt(5, 50))
    }

    @Test
    fun overflowArcCoversTheOverflowShareFromTheTop() {
        val arcs = ringArcs(progress = 1f, overflow = 0.25f)
        val bitmap =
            WidgetRingRenderer.arc(
                sizePx = 100,
                strokePx = 10f,
                startDeg = arcs.overflowStartDeg,
                sweepDeg = arcs.overflowSweepDeg,
                roundCaps = false,
            )

        assertTrue(bitmap.alphaAt(85, 15) > 200)
        assertEquals(0, bitmap.alphaAt(15, 15))
    }

    @Test
    fun zeroSweepProducesEmptyBitmap() {
        val bitmap =
            WidgetRingRenderer.arc(
                sizePx = 40,
                strokePx = 4f,
                startDeg = -90f,
                sweepDeg = 0f,
                roundCaps = true,
            )

        assertEquals(0, bitmap.alphaAt(20, 2))
    }

    private fun Bitmap.alphaAt(x: Int, y: Int): Int = Color.alpha(getPixel(x, y))
}

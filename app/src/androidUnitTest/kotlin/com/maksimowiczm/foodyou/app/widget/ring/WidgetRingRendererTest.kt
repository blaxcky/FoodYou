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
    fun trackIsOpenAtTheBottom() {
        val bitmap = WidgetRingRenderer.track(sizePx = 100, strokePx = 10f)

        assertEquals(100, bitmap.width)
        assertEquals(100, bitmap.height)
        assertEquals(Bitmap.Config.ARGB_8888, bitmap.config)
        assertTrue(bitmap.alphaAt(50, 5) > 200)
        assertTrue(bitmap.alphaAt(95, 50) > 200)
        assertTrue(bitmap.alphaAt(5, 50) > 200)
        assertEquals(0, bitmap.alphaAt(50, 95))
        assertEquals(0, bitmap.alphaAt(50, 50))
    }

    @Test
    fun progressArcRunsClockwiseFromBottomLeft() {
        val bitmap =
            WidgetRingRenderer.arc(
                sizePx = 100,
                strokePx = 10f,
                startDeg = 135f,
                sweepDeg = 180f,
                roundStart = false,
            )

        assertTrue(bitmap.alphaAt(11, 72) > 200)
        assertTrue(bitmap.alphaAt(50, 5) > 200)
        assertEquals(0, bitmap.alphaAt(95, 50))
    }

    @Test
    fun overflowArcCoversTheEndOfTheRing() {
        val arcs = ringArcs(progress = 1f, overflow = 0.25f)
        val bitmap =
            WidgetRingRenderer.arc(
                sizePx = 100,
                strokePx = 10f,
                startDeg = arcs.overflowStartDeg,
                sweepDeg = arcs.overflowSweepDeg,
                roundStart = false,
            )

        assertTrue(bitmap.alphaAt(89, 72) > 200)
        assertEquals(0, bitmap.alphaAt(11, 72))
    }

    @Test
    fun roundCapsAreDrawnOnlyWhereRequested() {
        val square =
            WidgetRingRenderer.arc(
                sizePx = 100,
                strokePx = 10f,
                startDeg = -90f,
                sweepDeg = 90f,
                roundStart = false,
                roundEnd = false,
            )
        val roundEnd =
            WidgetRingRenderer.arc(
                sizePx = 100,
                strokePx = 10f,
                startDeg = -90f,
                sweepDeg = 90f,
                roundStart = false,
                roundEnd = true,
            )

        // Just past the end at 0° (right side): only the round end cap reaches y = 54.
        assertEquals(0, square.alphaAt(95, 54))
        assertTrue(roundEnd.alphaAt(95, 54) > 200)
        // Just before the start at -90° (top): neither has a cap there.
        assertEquals(0, square.alphaAt(46, 5))
        assertEquals(0, roundEnd.alphaAt(46, 5))
    }

    @Test
    fun zeroSweepProducesEmptyBitmap() {
        val bitmap =
            WidgetRingRenderer.arc(
                sizePx = 40,
                strokePx = 4f,
                startDeg = 135f,
                sweepDeg = 0f,
                roundStart = true,
            )

        assertEquals(0, bitmap.alphaAt(20, 2))
    }

    private fun Bitmap.alphaAt(x: Int, y: Int): Int = Color.alpha(getPixel(x, y))
}

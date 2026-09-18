package com.maksimowiczm.foodyou.app.widget.ring

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renders ring segments as white alpha masks. Colors are applied by the widget host through
 * [androidx.glance.ColorFilter.tint], which keeps dynamic and day/night colors launcher-resolved.
 */
internal object WidgetRingRenderer {
    const val MAX_PIXELS = 240

    fun track(sizePx: Int, strokePx: Float): Bitmap =
        arc(
            sizePx = sizePx,
            strokePx = strokePx,
            startDeg = RingGauge.START_DEG,
            sweepDeg = RingGauge.SWEEP_DEG,
            roundStart = true,
            roundEnd = true,
        )

    fun arc(
        sizePx: Int,
        strokePx: Float,
        startDeg: Float,
        sweepDeg: Float,
        roundStart: Boolean,
        roundEnd: Boolean = roundStart,
    ): Bitmap {
        val size = sizePx.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        if (sweepDeg <= 0f) return bitmap
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = strokePx
                strokeCap = Paint.Cap.BUTT
                color = Color.WHITE
            }
        val inset = strokePx / 2f
        val bounds = RectF(inset, inset, size - inset, size - inset)
        val sweep = sweepDeg.coerceAtMost(360f)
        val canvas = Canvas(bitmap)
        canvas.drawArc(bounds, startDeg, sweep, false, paint)

        // Round caps are drawn as separate circles so each end can be rounded independently.
        val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val radius = (size - strokePx) / 2f
        val center = size / 2f
        fun cap(angleDeg: Float) {
            val radians = angleDeg.toDouble() * PI / 180.0
            val x = center + radius * cos(radians).toFloat()
            val y = center + radius * sin(radians).toFloat()
            canvas.drawCircle(x, y, strokePx / 2f, capPaint)
        }
        if (roundStart) cap(startDeg)
        if (roundEnd) cap(startDeg + sweep)
        return bitmap
    }
}

/** Shared gauge geometry: an open ring from bottom-left clockwise to bottom-right. */
internal object RingGauge {
    const val START_DEG = 135f
    const val SWEEP_DEG = 270f
    const val END_DEG = START_DEG + SWEEP_DEG
    const val OVERFLOW_GAP_DEG = 2.5f
}

internal data class RingArcs(
    val progressStartDeg: Float,
    val progressSweepDeg: Float,
    val gapStartDeg: Float,
    val gapSweepDeg: Float,
    val overflowStartDeg: Float,
    val overflowSweepDeg: Float,
) {
    val hasProgress: Boolean
        get() = progressSweepDeg > 0f

    val hasGap: Boolean
        get() = gapSweepDeg > 0f

    val hasOverflow: Boolean
        get() = overflowSweepDeg > 0f
}

/**
 * Mirrors the in-app gauge: progress fills clockwise from the bottom-left end of the open ring.
 * Once the goal is exceeded the overflow share takes the end of the ring in the error color,
 * separated from the remaining progress share by a small gap.
 */
internal fun ringArcs(progress: Float, overflow: Float): RingArcs {
    val overflowShare = overflow.coerceIn(0f, 1f)
    val visibleProgress = if (overflowShare > 0f) 1f - overflowShare else progress.coerceIn(0f, 1f)
    val progressSweep = RingGauge.SWEEP_DEG * visibleProgress
    val gapSweep =
        if (overflowShare > 0f && progressSweep > 0f) {
            RingGauge.OVERFLOW_GAP_DEG.coerceAtMost(progressSweep)
        } else {
            0f
        }
    val progressDrawSweep = progressSweep - gapSweep
    val overflowSweep = RingGauge.SWEEP_DEG * overflowShare
    return RingArcs(
        progressStartDeg = RingGauge.START_DEG,
        progressSweepDeg = progressDrawSweep,
        gapStartDeg = RingGauge.START_DEG + progressDrawSweep,
        gapSweepDeg = gapSweep,
        overflowStartDeg = RingGauge.END_DEG - overflowSweep,
        overflowSweepDeg = overflowSweep,
    )
}

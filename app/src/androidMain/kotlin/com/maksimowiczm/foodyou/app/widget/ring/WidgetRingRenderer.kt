package com.maksimowiczm.foodyou.app.widget.ring

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/**
 * Renders ring segments as white alpha masks. Colors are applied by the widget host through
 * [androidx.glance.ColorFilter.tint], which keeps dynamic and day/night colors launcher-resolved.
 */
internal object WidgetRingRenderer {
    const val MAX_PIXELS = 240

    fun track(sizePx: Int, strokePx: Float): Bitmap =
        arc(sizePx, strokePx, startDeg = 0f, sweepDeg = 360f, roundCaps = false)

    fun arc(
        sizePx: Int,
        strokePx: Float,
        startDeg: Float,
        sweepDeg: Float,
        roundCaps: Boolean,
    ): Bitmap {
        val size = sizePx.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        if (sweepDeg <= 0f) return bitmap
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = strokePx
                strokeCap = if (roundCaps) Paint.Cap.ROUND else Paint.Cap.BUTT
                color = Color.WHITE
            }
        val inset = strokePx / 2f
        val bounds = RectF(inset, inset, size - inset, size - inset)
        Canvas(bitmap).drawArc(bounds, startDeg, sweepDeg.coerceAtMost(360f), false, paint)
        return bitmap
    }
}

internal data class RingArcs(
    val progressStartDeg: Float,
    val progressSweepDeg: Float,
    val overflowStartDeg: Float,
    val overflowSweepDeg: Float,
) {
    val hasProgress: Boolean
        get() = progressSweepDeg > 0f

    val hasOverflow: Boolean
        get() = overflowSweepDeg > 0f
}

/**
 * Full-circle variant of the in-app gauge geometry: progress runs clockwise from the top,
 * overflow runs backwards from the top in the error color, separated by small gaps.
 */
internal fun ringArcs(progress: Float, overflow: Float, gapDeg: Float = 2.5f): RingArcs {
    val overflowSweep = 360f * overflow.coerceIn(0f, 1f)
    val visibleProgress = if (overflow > 0f) 1f - overflow.coerceIn(0f, 1f) else progress
    val rawProgressSweep = 360f * visibleProgress.coerceIn(0f, 1f)
    if (overflowSweep <= 0f) {
        return RingArcs(TOP_DEG, rawProgressSweep, TOP_DEG, 0f)
    }
    if (overflowSweep >= 360f) {
        return RingArcs(TOP_DEG, 0f, TOP_DEG, 360f)
    }
    val progressSweep = (rawProgressSweep - gapDeg).coerceAtLeast(0f)
    val overflowSweepTrimmed = (overflowSweep - gapDeg).coerceAtLeast(0f)
    return RingArcs(
        progressStartDeg = TOP_DEG,
        progressSweepDeg = progressSweep,
        overflowStartDeg = TOP_DEG - overflowSweep,
        overflowSweepDeg = overflowSweepTrimmed,
    )
}

private const val TOP_DEG = -90f

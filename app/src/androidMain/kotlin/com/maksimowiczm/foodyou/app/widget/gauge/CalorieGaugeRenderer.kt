package com.maksimowiczm.foodyou.app.widget.gauge

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.maksimowiczm.foodyou.R
import com.maksimowiczm.foodyou.app.ui.home.goals.DietGoalAccentColor
import com.maksimowiczm.foodyou.app.ui.home.goals.GoalsCardColor
import com.maksimowiczm.foodyou.app.ui.home.goals.GoalsErrorColor
import com.maksimowiczm.foodyou.app.ui.home.goals.GoalsMutedTextColor
import com.maksimowiczm.foodyou.app.ui.home.goals.GoalsProgressColor
import com.maksimowiczm.foodyou.app.ui.home.goals.GoalsTextColor
import com.maksimowiczm.foodyou.app.ui.home.goals.GoalsTrackColor
import com.maksimowiczm.foodyou.app.ui.home.goals.OptimizedGoalZoneColor
import com.maksimowiczm.foodyou.app.ui.home.goals.OptimizedGoalZoneTrackColor
import com.maksimowiczm.foodyou.app.widget.formatWidgetNumber
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Draws the ring of the home screen's goals card, its goal markers and the values on it into one
 * bitmap. Glance cannot place text freely, so the values are drawn too; they are measured like the
 * in-app labels, which lets the remaining energy drop its unit only when it really does not fit.
 * The widget always uses the card's light palette, so the colors are drawn directly.
 */
internal object CalorieGaugeRenderer {
    /** Keeps the bitmap well within the RemoteViews limits on very large widgets. */
    const val MAX_PIXELS = 480

    private const val GAP_ANGLE = 2.5f
    private const val ZONE_TRACK_TINT = 0.22f

    fun render(context: Context, spec: CalorieGaugeLayoutSpec, gauge: CalorieGauge): Bitmap {
        val density = context.resources.displayMetrics.density
        val widthDp = spec.gaugeWidth.value
        val heightDp = spec.gaugeHeight.value
        val scale = min(density, MAX_PIXELS / max(widthDp, heightDp))
        val bitmap =
            Bitmap.createBitmap(
                ceil(widthDp * scale).toInt().coerceAtLeast(1),
                ceil(heightDp * scale).toInt().coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
        GaugeDrawer(context, Canvas(bitmap), spec, scale).draw(gauge)
        return bitmap
    }

    private class GaugeDrawer(
        private val context: Context,
        private val canvas: Canvas,
        private val spec: CalorieGaugeLayoutSpec,
        private val scale: Float,
    ) {
        private val radius = spec.diameter.px() / 2f
        private val centerX = spec.inset.px() + radius
        private val centerY = spec.inset.px() + radius
        private val bounds = RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
        private val strokeWidth = spec.stroke.px()

        fun draw(gauge: CalorieGauge) {
            if (gauge.zones.isEmpty()) drawPlainRing(gauge) else drawZonedRing(gauge)
            gauge.zones.forEach { drawMarker(it.fraction, it.kind.color()) }
            drawMarker(1f, GoalsProgressColor)
            drawZoneLabels(gauge.zones)
            drawEndLabel(gauge)
        }

        /** Above the goal the whole ring turns red instead of eating back into the progress. */
        private fun drawPlainRing(gauge: CalorieGauge) {
            val overGoal = gauge.overflow > 0f
            arc(if (overGoal) GoalsErrorColor else GoalsTrackColor, 0f, 1f, Paint.Cap.ROUND)
            if (!overGoal && gauge.progress > 0f) {
                arc(GoalsProgressColor, 0f, gauge.progress.coerceIn(0f, 1f), Paint.Cap.ROUND)
            }
        }

        /**
         * One section per goal zone and a last one up to the normal goal, each filled in its goal's
         * color and separated by a small gap, as `drawZonedGauge` does on the card.
         */
        private fun drawZonedRing(gauge: CalorieGauge) {
            val zones = gauge.zones
            val boundaries = zones.map { it.fraction.coerceIn(0f, 1f) }
            val starts = listOf(0f) + boundaries
            val ends = boundaries + 1f
            val lastIndex = ends.lastIndex
            val colors = zones.map { it.kind.color() } + GoalsProgressColor
            val trackColors =
                zones.map {
                    when (it.kind) {
                        CalorieGaugeZoneKind.Optimized -> OptimizedGoalZoneTrackColor
                        else -> lerp(GoalsCardColor, it.kind.color(), ZONE_TRACK_TINT)
                    }
                } + GoalsTrackColor
            val halfGap = GAP_ANGLE / 2f

            fun sectionStart(index: Int): Float =
                gaugeAngle(starts[index]) + if (index > 0) halfGap else 0f

            fun sectionEnd(index: Int): Float =
                gaugeAngle(ends[index]) - if (index < lastIndex) halfGap else 0f

            for (index in 0..lastIndex) {
                arcBetween(trackColors[index], sectionStart(index), sectionEnd(index))
            }
            cap(trackColors.first(), 0f)
            cap(trackColors.last(), 1f)

            if (gauge.overflow > 0f) {
                arc(GoalsErrorColor, 0f, 1f, Paint.Cap.ROUND)
                return
            }
            val visibleProgress = gauge.progress.coerceIn(0f, 1f)
            if (visibleProgress <= 0f) return
            cap(colors.first(), 0f)
            for (index in 0..lastIndex) {
                if (visibleProgress <= starts[index]) break
                val end =
                    if (visibleProgress >= ends[index]) sectionEnd(index)
                    else gaugeAngle(visibleProgress)
                arcBetween(colors[index], sectionStart(index), end)
            }
            val section = ends.indexOfFirst { visibleProgress <= it }.coerceAtLeast(0)
            cap(colors[section], visibleProgress)
        }

        private fun arc(color: Color, from: Float, to: Float, cap: Paint.Cap) {
            val sweep = 270f * (to - from)
            if (sweep <= 0f) return
            canvas.drawArc(bounds, gaugeAngle(from), sweep, false, strokePaint(color, cap))
        }

        private fun arcBetween(color: Color, startAngle: Float, endAngle: Float) {
            if (endAngle <= startAngle) return
            canvas.drawArc(
                bounds,
                startAngle,
                endAngle - startAngle,
                false,
                strokePaint(color, Paint.Cap.BUTT),
            )
        }

        private fun cap(color: Color, fraction: Float) {
            val (x, y) = point(fraction)
            canvas.drawCircle(x, y, strokeWidth / 2f, fillPaint(color))
        }

        /** A marker on the ring at [fraction] of its sweep, ringed in the card's color. */
        private fun drawMarker(fraction: Float, color: Color) {
            val (x, y) = point(fraction.coerceIn(0f, 1f))
            canvas.drawCircle(x, y, spec.markerRadius.px(), fillPaint(GoalsCardColor))
            canvas.drawCircle(x, y, spec.markerDotRadius.px(), fillPaint(color))
        }

        /**
         * Each zone's value sits inside the ring next to its marker. The edge facing the ring keeps
         * the same clearance whatever the label's size, so a wide value moves further in.
         */
        private fun drawZoneLabels(zones: List<CalorieGaugeZone>) {
            val clearance = spec.zoneLabelClearance.px()
            zones.forEach { zone ->
                val label =
                    Label(
                        value = context.formatWidgetNumber(abs(zone.remainingKcal)),
                        exceeded = zone.exceeded,
                        color = if (zone.exceeded) GoalsErrorColor else GoalsMutedTextColor,
                        weight = if (zone.exceeded) 600 else 500,
                        textSize = spec.zoneTextSize,
                        unit = null,
                    )
                val radians = gaugeAngle(zone.fraction).toDouble() * PI / 180.0
                val cos = cos(radians).toFloat()
                val sin = sin(radians).toFloat()
                val halfExtent = abs(cos) * label.width / 2f + abs(sin) * label.height / 2f
                val labelRadius = radius - clearance - halfExtent
                label.draw(centerX + labelRadius * cos, centerY + labelRadius * sin)
            }
        }

        /**
         * The normal goal's remaining energy, centered in the ring's open bottom a little below its
         * ends. Its unit is the first thing to go when it does not fit between the end markers.
         */
        private fun drawEndLabel(gauge: CalorieGauge) {
            val (_, endY) = point(1f)
            val room = 2f * radius * cos(PI / 4).toFloat() - 2f * CalorieGaugeWidgetSizes.EndLabelGap.px()

            fun label(unit: String?) =
                Label(
                    value = context.formatWidgetNumber(abs(gauge.remainingKcal)),
                    exceeded = gauge.exceeded,
                    color = if (gauge.exceeded) GoalsErrorColor else GoalsTextColor,
                    weight = 600,
                    textSize = spec.endTextSize,
                    unit = unit,
                )

            val withUnit = label(context.getString(R.string.widget_calories_unit_kcal))
            val label = if (withUnit.width <= room) withUnit else label(null)
            label.draw(centerX, endY + CalorieGaugeWidgetSizes.EndLabelDrop.px())
        }

        /**
         * A value with an optional unit on its baseline. Above its goal it turns red and gets a
         * warning sign, so the overshoot does not rely on color alone.
         */
        private inner class Label(
            val value: String,
            val exceeded: Boolean,
            color: Color,
            weight: Int,
            textSize: TextUnit,
            val unit: String?,
        ) {
            private val valuePaint = textPaint(color, weight, textSize)
            private val unitPaint =
                textPaint(
                    if (exceeded) GoalsErrorColor else GoalsMutedTextColor,
                    400,
                    spec.unitTextSize,
                )
            private val iconSize = valuePaint.textSize
            private val gap = 1.dp.px()
            private val unitGap = 3.dp.px()
            private val valueWidth = valuePaint.measureText(value)
            private val unitWidth = unit?.let { unitGap + unitPaint.measureText(it) } ?: 0f

            val width: Float = (if (exceeded) iconSize + gap else 0f) + valueWidth + unitWidth
            val height: Float = valuePaint.textSize * LineHeightFactor

            fun draw(centerX: Float, centerY: Float) {
                var x = centerX - width / 2f
                if (exceeded) {
                    val icon = ContextCompat.getDrawable(context, R.drawable.ic_widget_warning)
                    icon?.mutate()?.apply {
                        setTint(GoalsErrorColor.toArgb())
                        val top = centerY - iconSize / 2f
                        setBounds(
                            x.toInt(),
                            top.toInt(),
                            (x + iconSize).toInt(),
                            (top + iconSize).toInt(),
                        )
                        draw(canvas)
                    }
                    x += iconSize + gap
                }
                val metrics = valuePaint.fontMetrics
                val baseline = centerY - (metrics.ascent + metrics.descent) / 2f
                canvas.drawText(value, x, baseline, valuePaint)
                if (unit != null) {
                    canvas.drawText(unit, x + valueWidth + unitGap, baseline, unitPaint)
                }
            }
        }

        private fun point(fraction: Float): Pair<Float, Float> {
            val radians = gaugeAngle(fraction).toDouble() * PI / 180.0
            return (centerX + radius * cos(radians).toFloat()) to
                (centerY + radius * sin(radians).toFloat())
        }

        private fun strokePaint(color: Color, cap: Paint.Cap) =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = this@GaugeDrawer.strokeWidth
                strokeCap = cap
                this.color = color.toArgb()
            }

        private fun fillPaint(color: Color) =
            Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgb() }

        private fun textPaint(color: Color, weight: Int, textSize: TextUnit) =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color.toArgb()
                this.textSize = textSize.value * spec.fontScale * scale
                typeface = Typeface.create(Typeface.DEFAULT, weight, false)
                fontFeatureSettings = "tnum"
            }

        private fun Dp.px(): Float = value * scale
    }
}

/** The ring starts bottom left at 135° and sweeps 270° clockwise to the bottom right. */
private fun gaugeAngle(fraction: Float): Float = 135f + 270f * fraction

private fun CalorieGaugeZoneKind.color(): Color =
    when (this) {
        CalorieGaugeZoneKind.Optimized -> OptimizedGoalZoneColor
        CalorieGaugeZoneKind.Diet -> DietGoalAccentColor
    }

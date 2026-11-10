package com.maksimowiczm.foodyou.app.widget.gauge

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.maksimowiczm.foodyou.R
import com.maksimowiczm.foodyou.app.ui.home.goals.DietGoalAccentColor
import com.maksimowiczm.foodyou.app.ui.home.goals.GaugeOffRingTrackColor
import com.maksimowiczm.foodyou.app.ui.home.goals.GaugeOffRingTrackOvershootAngle
import com.maksimowiczm.foodyou.app.ui.home.goals.GaugeOffRingTrackStartAngle
import com.maksimowiczm.foodyou.app.ui.home.goals.GoalsCardColor
import com.maksimowiczm.foodyou.app.ui.home.goals.GoalsErrorColor
import com.maksimowiczm.foodyou.app.ui.home.goals.GoalsMutedTextColor
import com.maksimowiczm.foodyou.app.ui.home.goals.GoalsProgressColor
import com.maksimowiczm.foodyou.app.ui.home.goals.GoalsTextColor
import com.maksimowiczm.foodyou.app.ui.home.goals.GoalsTrackColor
import com.maksimowiczm.foodyou.app.ui.home.goals.OptimizedGoalZoneColor
import com.maksimowiczm.foodyou.app.widget.formatWidgetNumber
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Draws the ring of the home screen's goals card into one bitmap: its goal markers, the dotted
 * track with the dots of goals off the ring, and the normal goal's remaining energy in its center.
 * Glance cannot place text freely, so the center value is drawn too. The goal values themselves
 * are a Glance row under the ring. The widget always uses the card's light palette, so the colors
 * are drawn directly.
 */
internal object CalorieGaugeRenderer {
    /** Keeps the bitmap well within the RemoteViews limits on very large widgets. */
    const val MAX_PIXELS = 480

    private const val GAP_ANGLE = 2.5f

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
            val zones = gauge.onRingZones
            if (zones.isEmpty()) drawPlainRing(gauge) else drawZonedRing(gauge, zones)
            zones.forEach { drawMarker(gaugeAngle(it.fraction.coerceIn(0f, 1f)), it.kind.color()) }
            drawMarker(gaugeAngle(1f), GoalsProgressColor)
            drawOffRingGoals(gauge.offRingDots)
            drawCenterLabel(gauge)
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
         * One section per goal zone on the ring and a last one up to the normal goal, each filled
         * in its goal's color and separated by a small gap, as `drawZonedGauge` does on the card.
         */
        private fun drawZonedRing(gauge: CalorieGauge, zones: List<CalorieGaugeZone>) {
            val boundaries = zones.map { it.fraction.coerceIn(0f, 1f) }
            val starts = listOf(0f) + boundaries
            val ends = boundaries + 1f
            val lastIndex = ends.lastIndex
            val colors = zones.map { it.kind.color() } + GoalsProgressColor
            val halfGap = GAP_ANGLE / 2f

            fun sectionStart(index: Int): Float =
                gaugeAngle(starts[index]) + if (index > 0) halfGap else 0f

            fun sectionEnd(index: Int): Float =
                gaugeAngle(ends[index]) - if (index < lastIndex) halfGap else 0f

            // Every section's unfilled track is the normal ring's; only the fill shows the goal's color.
            for (index in 0..lastIndex) {
                arcBetween(GoalsTrackColor, sectionStart(index), sectionEnd(index))
            }
            cap(GoalsTrackColor, 0f)
            cap(GoalsTrackColor, 1f)

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

        /**
         * Goals off the ring: past the matching end the ring goes on as a fine dotted track, and
         * each goal's dot sits on it, as on the card.
         */
        private fun drawOffRingGoals(dots: List<Pair<CalorieGaugeZone, Float>>) {
            if (dots.isEmpty()) return
            // Zero-length dashes with round caps draw a row of dots.
            val dotted =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = spec.offRingTrackWidth.px()
                    strokeCap = Paint.Cap.ROUND
                    color = GaugeOffRingTrackColor.toArgb()
                    pathEffect = DashPathEffect(floatArrayOf(0f, spec.offRingDotSpacing.px()), 0f)
                }
            val start = gaugeAngle(0f)
            val end = gaugeAngle(1f)
            dots.filter { it.second < start }.minOfOrNull { it.second }?.let { farthest ->
                val from = farthest - GaugeOffRingTrackOvershootAngle
                canvas.drawArc(bounds, from, start - GaugeOffRingTrackStartAngle - from, false, dotted)
            }
            dots.filter { it.second > end }.maxOfOrNull { it.second }?.let { farthest ->
                val from = end + GaugeOffRingTrackStartAngle
                canvas.drawArc(bounds, from, farthest + GaugeOffRingTrackOvershootAngle - from, false, dotted)
            }
            dots.forEach { (zone, angle) -> drawMarker(angle, zone.kind.color()) }
        }

        /**
         * The normal goal's remaining energy in the ring's center with "übrig" or, in red,
         * "zu viel" under it. A value too wide for the ring's inside is drawn smaller.
         */
        private fun drawCenterLabel(gauge: CalorieGauge) {
            val color = if (gauge.exceeded) GoalsErrorColor else GoalsTextColor
            val value = context.formatWidgetNumber(abs(gauge.remainingKcal))
            val label =
                context.getString(
                    if (gauge.exceeded) R.string.widget_calories_over_lowercase
                    else R.string.widget_calories_left_lowercase
                )
            val valuePaint = textPaint(color, 600, spec.centerValueTextSize)
            val labelPaint =
                textPaint(
                    if (gauge.exceeded) GoalsErrorColor else GoalsMutedTextColor,
                    if (gauge.exceeded) 600 else 400,
                    spec.centerLabelTextSize,
                )
            val room = 2f * radius - strokeWidth - 2f * CenterLabelPadding.px()
            val widest = max(valuePaint.measureText(value), labelPaint.measureText(label))
            if (widest > room && widest > 0f) {
                val fit = room / widest
                valuePaint.textSize *= fit
                labelPaint.textSize *= fit
            }
            val valueHeight = valuePaint.textSize * LineHeightFactor
            val labelHeight = labelPaint.textSize * LineHeightFactor
            val top = centerY - (valueHeight + labelHeight) / 2f
            drawCentered(value, valuePaint, top + valueHeight / 2f)
            drawCentered(label, labelPaint, top + valueHeight + labelHeight / 2f)
        }

        private fun drawCentered(text: String, paint: Paint, middleY: Float) {
            val metrics = paint.fontMetrics
            val baseline = middleY - (metrics.ascent + metrics.descent) / 2f
            canvas.drawText(text, centerX - paint.measureText(text) / 2f, baseline, paint)
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
            val (x, y) = point(gaugeAngle(fraction))
            canvas.drawCircle(x, y, strokeWidth / 2f, fillPaint(color))
        }

        /** A marker on the ring's circle at [angle] degrees, ringed in the card's color. */
        private fun drawMarker(angle: Float, color: Color) {
            val (x, y) = point(angle)
            canvas.drawCircle(x, y, spec.markerRadius.px(), fillPaint(GoalsCardColor))
            canvas.drawCircle(x, y, spec.markerDotRadius.px(), fillPaint(color))
        }

        private fun point(angle: Float): Pair<Float, Float> {
            val radians = angle.toDouble() * PI / 180.0
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

/** Room kept between the ring's inner edge and the center value's widest line. */
private val CenterLabelPadding = 4.dp

/** The ring starts bottom left at 135° and sweeps 270° clockwise to the bottom right. */
private fun gaugeAngle(fraction: Float): Float = 135f + 270f * fraction

internal fun CalorieGaugeZoneKind.color(): Color =
    when (this) {
        CalorieGaugeZoneKind.Optimized -> OptimizedGoalZoneColor
        CalorieGaugeZoneKind.Diet -> DietGoalAccentColor
    }

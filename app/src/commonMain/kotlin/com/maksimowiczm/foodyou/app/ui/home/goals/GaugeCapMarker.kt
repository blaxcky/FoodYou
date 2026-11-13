package com.maksimowiczm.foodyou.app.ui.home.goals

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import com.maksimowiczm.foodyou.settings.domain.entity.GoalGaugeCapMarkerStyle
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

/** The cap marker style the goals card draws, set in the goals card settings. */
internal val LocalGoalGaugeCapMarkerStyle =
    staticCompositionLocalOf { GoalGaugeCapMarkerStyle.Teardrop }

/**
 * The direction, in degrees, that points past the ring's start cap (back along the ring, toward its
 * open bottom) and past its end cap. The ring runs clockwise from 135° to 405°.
 */
internal const val GaugeStartCapOutwardAngle = 45f
internal const val GaugeEndCapOutwardAngle = 135f

/**
 * The marker on one of the ring's end caps for the goals off the ring past that end, nearest goal
 * first in [goalColors]. On the end cap [normalColor] is the ring's own goal, whose marker the
 * goals share; the start cap has none. All lengths are in pixels; [unit] is one dp in pixels.
 *
 * Every style keeps the size of the ring's other markers, so both ends stay alike, and points or
 * reads past the end: a [GoalGaugeCapMarkerStyle.Teardrop] or [GoalGaugeCapMarkerStyle.Diamond]
 * with its tip outward, a [GoalGaugeCapMarkerStyle.Bracket] closing around the cap, or a
 * [GoalGaugeCapMarkerStyle.Sign] reading "−" below zero and "+" above the ring's goal.
 */
internal fun DrawScope.drawGaugeCapMarker(
    point: Offset,
    outwardAngle: Float,
    style: GoalGaugeCapMarkerStyle,
    normalColor: Color?,
    goalColors: List<Color>,
    markerRadius: Float,
    dotRadius: Float,
    ringStrokeWidth: Float,
    unit: Float,
    cardColor: Color,
) {
    if (goalColors.isEmpty()) return
    val colors = listOfNotNull(normalColor) + goalColors
    translate(point.x, point.y) {
        // The same card-colored disc as under the ring's other markers, so both caps look alike.
        if (style != GoalGaugeCapMarkerStyle.Bracket) {
            drawCircle(cardColor, radius = markerRadius, center = Offset.Zero)
        }
        when (style) {
            GoalGaugeCapMarkerStyle.Sign ->
                drawSignMarker(colors, isEnd = normalColor != null, markerRadius, unit, cardColor)
            else ->
                rotate(outwardAngle, pivot = Offset.Zero) {
                    when (style) {
                        GoalGaugeCapMarkerStyle.Teardrop ->
                            drawTeardropMarker(colors, dotRadius, unit, cardColor)
                        GoalGaugeCapMarkerStyle.Diamond ->
                            drawDiamondMarker(colors, dotRadius, unit, cardColor)
                        GoalGaugeCapMarkerStyle.Bracket ->
                            drawBracketMarker(
                                normalColor,
                                goalColors,
                                markerRadius,
                                dotRadius,
                                ringStrokeWidth,
                                unit,
                                cardColor,
                            )
                        GoalGaugeCapMarkerStyle.Sign -> Unit
                    }
                }
        }
    }
}

/** The card-colored outline around a marker's shape, as wide as the dots' rim reads. */
private const val OutlineWidthDp = 2.6f

/**
 * A dot drawn out into a tip along +x. The body is the nearest color (the ring's own goal on the
 * end cap), the tip the goal past the end; a third color takes the tip's lower half.
 */
private fun DrawScope.drawTeardropMarker(
    colors: List<Color>,
    dotRadius: Float,
    unit: Float,
    cardColor: Color,
) {
    val bodyRadius = dotRadius + 0.3f * unit
    val bodyCenter = -0.4f * bodyRadius
    val tip = 1.45f * bodyRadius
    val alpha = acos(bodyRadius / (tip - bodyCenter))
    val tangentX = bodyCenter + bodyRadius * cos(alpha)
    val tangentY = bodyRadius * sin(alpha)
    val alphaDegrees = alpha * 180f / PI.toFloat()
    val body = Rect(Offset(bodyCenter, 0f), bodyRadius)
    val outline =
        Path().apply {
            moveTo(tip, 0f)
            lineTo(tangentX, tangentY)
            arcTo(body, alphaDegrees, 360f - 2f * alphaDegrees, forceMoveTo = false)
            close()
        }
    drawPath(outline, cardColor, style = Stroke(OutlineWidthDp * unit, join = StrokeJoin.Round))
    if (colors.size == 1) {
        drawPath(outline, colors.single())
        return
    }
    drawPath(outline, cardColor)
    drawCircle(colors[0], radius = bodyRadius, center = Offset(bodyCenter, 0f))
    val tipColors = colors.drop(1)
    if (tipColors.size == 1) {
        drawPath(triangle(tip, 0f, tangentX, tangentY, tangentX, -tangentY), tipColors.single())
    } else {
        drawPath(triangle(tip, 0f, tangentX, -tangentY, tangentX, 0f), tipColors[0])
        drawPath(triangle(tip, 0f, tangentX, 0f, tangentX, tangentY), tipColors[1])
    }
}

/**
 * A diamond drawn out along +x, so its outer corner points past the end even at the ring's
 * diagonal caps. Its inner half is the nearest color, its outer half the goal past the end; a third
 * color takes the outer half's lower part.
 */
private fun DrawScope.drawDiamondMarker(
    colors: List<Color>,
    dotRadius: Float,
    unit: Float,
    cardColor: Color,
) {
    val along = dotRadius + 2.4f * unit
    val h = dotRadius + 0.6f * unit
    val diamond =
        Path().apply {
            moveTo(along, 0f)
            lineTo(0f, h)
            lineTo(-along, 0f)
            lineTo(0f, -h)
            close()
        }
    drawPath(diamond, cardColor, style = Stroke(OutlineWidthDp * unit, join = StrokeJoin.Round))
    if (colors.size == 1) {
        drawPath(diamond, colors.single())
        return
    }
    drawPath(triangle(0f, -h, -along, 0f, 0f, h), colors[0])
    val outer = colors.drop(1)
    if (outer.size == 1) {
        drawPath(triangle(0f, -h, along, 0f, 0f, h), outer.single())
    } else {
        drawPath(triangle(0f, -h, along, 0f, 0f, 0f), outer[0])
        drawPath(triangle(0f, 0f, along, 0f, 0f, h), outer[1])
    }
}

/**
 * Colored half rings closing around the cap on its outer side, one per goal, the nearest goal
 * innermost. On the end cap they close around the ring's own marker.
 */
private fun DrawScope.drawBracketMarker(
    normalColor: Color?,
    goalColors: List<Color>,
    markerRadius: Float,
    dotRadius: Float,
    ringStrokeWidth: Float,
    unit: Float,
    cardColor: Color,
) {
    val width = 2.4f * unit
    val firstRadius =
        if (normalColor != null) {
            drawCircle(cardColor, radius = markerRadius, center = Offset.Zero)
            drawCircle(normalColor, radius = dotRadius, center = Offset.Zero)
            markerRadius + 2.5f * unit
        } else {
            ringStrokeWidth / 2f + 3f * unit
        }
    goalColors.forEachIndexed { index, color ->
        val radius = firstRadius + index * 3.5f * unit
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(-radius, -radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width, cap = StrokeCap.Round),
        )
    }
}

/**
 * A dot as large as the ring's markers reading "−" on the start cap and "+" on the end cap. A dot
 * for several goals is split into equal slices, the ring's own goal first on the end cap.
 */
private fun DrawScope.drawSignMarker(
    colors: List<Color>,
    isEnd: Boolean,
    markerRadius: Float,
    unit: Float,
    cardColor: Color,
) {
    val outer = markerRadius + 0.5f * unit
    val disc = outer - 1.6f * unit
    drawCircle(cardColor, radius = outer, center = Offset.Zero)
    if (colors.size == 1) {
        drawCircle(colors.single(), radius = disc, center = Offset.Zero)
    } else {
        gaugeMarkerSlices(colors.size).zip(colors).forEach { (slice, color) ->
            drawArc(
                color = color,
                startAngle = slice.first,
                sweepAngle = slice.second,
                useCenter = true,
                topLeft = Offset(-disc, -disc),
                size = Size(disc * 2f, disc * 2f),
            )
        }
    }
    val arm = disc * 0.48f
    val stroke = 1.5f * unit
    drawLine(cardColor, Offset(-arm, 0f), Offset(arm, 0f), stroke, cap = StrokeCap.Round)
    if (isEnd) drawLine(cardColor, Offset(0f, -arm), Offset(0f, arm), stroke, cap = StrokeCap.Round)
}

private fun triangle(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) =
    Path().apply {
        moveTo(x1, y1)
        lineTo(x2, y2)
        lineTo(x3, y3)
        close()
    }

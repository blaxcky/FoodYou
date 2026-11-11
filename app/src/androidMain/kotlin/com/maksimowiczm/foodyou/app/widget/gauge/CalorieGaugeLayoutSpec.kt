package com.maksimowiczm.foodyou.app.widget.gauge

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maksimowiczm.foodyou.app.ui.home.goals.GaugeOffRingFirstAngle
import com.maksimowiczm.foodyou.app.ui.home.goals.GaugeOffRingStepAngle
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/** Layout parameters derived from the widget's actual size so the ring fills the cell. */
internal data class CalorieGaugeLayoutSpec(
    val verticalPadding: Dp,
    val horizontalPadding: Dp,
    val showHeader: Boolean,
    /** Diameter of the ring's center line. */
    val diameter: Dp,
    val stroke: Dp,
    val markerRadius: Dp,
    val markerDotRadius: Dp,
    /** The normal goal's remaining energy in the ring's center, and the word under it. */
    val centerValueTextSize: TextUnit,
    val centerLabelTextSize: TextUnit,
    /** The dotted continuation of the ring past its end, for goals off the ring. */
    val offRingTrackWidth: Dp,
    val offRingDotSpacing: Dp,
    /** How many off-ring dots sit past the ring's end on its fuller side. */
    val offRingDepth: Int,
    /** The row of goal values under the ring: a dot in the goal's color and the number. */
    val showLegend: Boolean,
    val legendTextSize: TextUnit,
    val legendDotSize: Dp,
    val legendGap: Dp,
    val legendItemGap: Dp,
    val metricValueTextSize: TextUnit,
    val metricLabelTextSize: TextUnit,
    /** Height of the row that holds eaten energy, the ring and burned energy. */
    val rowHeight: Dp,
    /**
     * Tap area of the action icon under eaten and under burned energy. The icons sit at the
     * widget's bottom edge, but a side column keeps their room so its value stays lifted.
     */
    val actionButtonSize: Dp,
    val actionIconSize: Dp,
    /** Gap between a side value's label and the room kept for its action button. */
    val actionGap: Dp,
    /**
     * Room above a side value, kept as well below the room for its button: the side column is
     * centered in the row, so this lifts the value without crowding the ring's ends.
     */
    val actionDrop: Dp,
    /** The system font scale; the ring's values are drawn into its bitmap and scale with it. */
    val fontScale: Float,
) {
    /** Room around the ring's center line for the stroke and the markers on it. */
    val inset: Dp
        get() = maxOf(stroke / 2, markerRadius)

    val gaugeWidth: Dp
        get() = diameter + inset * 2

    /** From the top of the ring down to its ends, or to the lowest dot past them. */
    val gaugeHeight: Dp
        get() = inset + diameter * ringBottomFactor(offRingDepth) + inset

    /** The legend row with the room above it, or nothing without supplemental goals. */
    val legendHeight: Dp
        get() = legendHeight(showLegend, legendGap, legendTextSize, fontScale)

    /** Full height of a side column: value, label, its button's room and the room around them. */
    val sideMetricHeight: Dp
        get() = sideMetricBlockHeight(metricValueTextSize, metricLabelTextSize, fontScale) +
            actionGap + actionButtonSize + actionDrop * 2
}

/** Value and label as Glance lays them out; its text views add font padding to each line. */
private fun sideMetricBlockHeight(value: TextUnit, label: TextUnit, fontScale: Float): Dp =
    ((value.value + label.value) * SideTextLineHeightFactor * fontScale).dp

private const val SideTextLineHeightFactor = 1.45f

private fun legendHeight(show: Boolean, gap: Dp, textSize: TextUnit, fontScale: Float): Dp =
    if (show) gap + (textSize.value * SideTextLineHeightFactor * fontScale).dp else 0.dp

internal object CalorieGaugeWidgetSizes {
    val MinSize = DpSize(250.dp, 130.dp)
    val HeaderMinHeight = 150.dp
    val HeaderHeight = 20.dp
    val HeaderSpacing = 2.dp

    /** Room kept free under the ring so it sits a little higher below the header. */
    val GaugeLift = 4.dp

    /** Extra room between eaten or burned energy and the ring, on the ring's side. */
    val MetricGaugeGap = 12.dp

    /** Below this row height the action buttons shrink so they stay inside the widget. */
    val CompactActionMaxRowHeight = 96.dp
    val HeaderDateTextSize = 12.sp
    val HeaderStepsTextSize = 14.sp
    val LargeGaugeMinHeight = 110.dp
    val MaxDiameter = 200.dp
    val MinDiameter = 48.dp
}

internal const val LineHeightFactor = 1.25f

/** The ring's ends sit this share of its diameter below its top: half plus sin 45° of a radius. */
private val RingBottomFactor = 0.5f + sqrt(2f) / 4f

/**
 * The share of the diameter from the ring's top down to its lowest point: its ends, or the
 * farthest of [offRingDepth] dots past them, which sits further round toward the bottom.
 */
private fun ringBottomFactor(offRingDepth: Int): Float {
    if (offRingDepth <= 0) return RingBottomFactor
    val degrees = 45f + GaugeOffRingFirstAngle + GaugeOffRingStepAngle * (offRingDepth - 1)
    return 0.5f + sin(degrees * PI / 180.0).toFloat() / 2f
}

/** Share of the content width the ring may take; the rest is for eaten and burned energy. */
private const val GaugeWidthShare = 0.6f

/**
 * The layout for a widget of [size]. With [showLegend] the goal values take a row under the ring,
 * and [offRingDepth] dots past the ring's end need room below it; both make the ring smaller.
 */
internal fun calorieGaugeLayoutSpec(
    size: DpSize,
    fontScale: Float = 1f,
    showLegend: Boolean = false,
    offRingDepth: Int = 0,
): CalorieGaugeLayoutSpec {
    val verticalPadding = 12.dp
    val horizontalPadding = 16.dp
    val showHeader = size.height >= CalorieGaugeWidgetSizes.HeaderMinHeight
    val header =
        if (showHeader) {
            CalorieGaugeWidgetSizes.HeaderHeight +
                CalorieGaugeWidgetSizes.HeaderSpacing +
                CalorieGaugeWidgetSizes.GaugeLift
        } else {
            0.dp
        }
    val legendTextSize = 13.sp
    val legendGap = 4.dp
    val contentHeight = size.height - verticalPadding * 2 - header
    val rowHeight =
        contentHeight - legendHeight(showLegend, legendGap, legendTextSize, fontScale)
    // Whether the legend shows does not change the ring's line and text sizes for one widget size.
    val large = contentHeight >= CalorieGaugeWidgetSizes.LargeGaugeMinHeight
    val stroke = if (large) 10.dp else 8.dp
    val markerRadius = if (large) 6.5.dp else 5.5.dp
    val inset = maxOf(stroke / 2, markerRadius)
    val byHeight = (rowHeight - inset * 2) / ringBottomFactor(offRingDepth)
    val byWidth = (size.width - horizontalPadding * 2) * GaugeWidthShare - inset * 2
    val diameter =
        minOf(byHeight, byWidth, CalorieGaugeWidgetSizes.MaxDiameter)
            .coerceAtLeast(CalorieGaugeWidgetSizes.MinDiameter)
    val metricValueTextSize = if (large) 18.sp else 16.sp
    val metricLabelTextSize = if (large) 11.sp else 10.sp
    val compact = rowHeight < CalorieGaugeWidgetSizes.CompactActionMaxRowHeight
    val actionButtonSize =
        when {
            large -> 36.dp
            compact -> 28.dp
            else -> 32.dp
        }
    val actionGap =
        when {
            large -> 10.dp
            compact -> 6.dp
            else -> 8.dp
        }
    val sideBlock =
        sideMetricBlockHeight(metricValueTextSize, metricLabelTextSize, fontScale) +
            actionGap +
            actionButtonSize
    val actionDrop =
        minOf(if (large) 16.dp else 12.dp, (rowHeight - sideBlock) / 2).coerceAtLeast(0.dp)
    return CalorieGaugeLayoutSpec(
        verticalPadding = verticalPadding,
        horizontalPadding = horizontalPadding,
        showHeader = showHeader,
        diameter = diameter,
        stroke = stroke,
        markerRadius = markerRadius,
        markerDotRadius = if (large) 3.5.dp else 3.dp,
        centerValueTextSize = if (large) 22.sp else 18.sp,
        centerLabelTextSize = if (large) 11.sp else 10.sp,
        offRingTrackWidth = if (large) 2.5.dp else 2.dp,
        offRingDotSpacing = if (large) 4.dp else 3.5.dp,
        offRingDepth = offRingDepth,
        showLegend = showLegend,
        legendTextSize = legendTextSize,
        legendDotSize = 7.dp,
        legendGap = legendGap,
        legendItemGap = 16.dp,
        metricValueTextSize = metricValueTextSize,
        metricLabelTextSize = metricLabelTextSize,
        rowHeight = rowHeight,
        actionButtonSize = actionButtonSize,
        actionIconSize =
            when {
                large -> 20.dp
                compact -> 16.dp
                else -> 18.dp
            },
        actionGap = actionGap,
        actionDrop = actionDrop,
        fontScale = fontScale,
    )
}

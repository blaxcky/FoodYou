package com.maksimowiczm.foodyou.app.widget.gauge

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    /** Gap between the ring's center line and the nearest edge of a goal's value. */
    val zoneLabelClearance: Dp,
    val zoneTextSize: TextUnit,
    val endTextSize: TextUnit,
    val unitTextSize: TextUnit,
    val metricValueTextSize: TextUnit,
    val metricLabelTextSize: TextUnit,
    /** The system font scale; the ring's values are drawn into its bitmap and scale with it. */
    val fontScale: Float,
) {
    /** Room around the ring's center line for the stroke and the markers on it. */
    val inset: Dp
        get() = maxOf(stroke / 2, markerRadius)

    val gaugeWidth: Dp
        get() = diameter + inset * 2

    /** From the top of the ring down to the remaining energy under its open bottom. */
    val gaugeHeight: Dp
        get() =
            inset + diameter * RingBottomFactor + CalorieGaugeWidgetSizes.EndLabelDrop +
                endLineHeight / 2

    val endLineHeight: Dp
        get() = (endTextSize.value * LineHeightFactor * fontScale).dp
}

internal object CalorieGaugeWidgetSizes {
    val MinSize = DpSize(250.dp, 110.dp)
    val HeaderMinHeight = 150.dp
    val HeaderHeight = 20.dp
    val HeaderSpacing = 2.dp

    /** Room kept free under the ring so it sits a little higher below the header. */
    val GaugeLift = 4.dp

    /** Extra room between eaten or burned energy and the ring, on the ring's side. */
    val MetricGaugeGap = 12.dp
    val HeaderDateTextSize = 12.sp
    val HeaderStepsTextSize = 14.sp
    val LargeGaugeMinHeight = 110.dp
    val MaxDiameter = 200.dp
    val MinDiameter = 48.dp

    /** Room kept between each end of the ring and the remaining energy centered between them. */
    val EndLabelGap = 10.dp

    /** How far the remaining energy's center sits below the ring's ends. */
    val EndLabelDrop = 3.dp
}

internal const val LineHeightFactor = 1.25f

/** The ring's ends sit this share of its diameter below its top: half plus sin 45° of a radius. */
private val RingBottomFactor = 0.5f + sqrt(2f) / 4f

/** Share of the content width the ring may take; the rest is for eaten and burned energy. */
private const val GaugeWidthShare = 0.6f

internal fun calorieGaugeLayoutSpec(size: DpSize, fontScale: Float = 1f): CalorieGaugeLayoutSpec {
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
    val rowHeight = size.height - verticalPadding * 2 - header
    val large = rowHeight >= CalorieGaugeWidgetSizes.LargeGaugeMinHeight
    val stroke = if (large) 10.dp else 8.dp
    val markerRadius = if (large) 6.5.dp else 5.5.dp
    val endTextSize = if (large) 16.sp else 14.sp
    val inset = maxOf(stroke / 2, markerRadius)
    val endHalfLine = (endTextSize.value * LineHeightFactor * fontScale / 2f).dp
    val byHeight =
        (rowHeight - inset - CalorieGaugeWidgetSizes.EndLabelDrop - endHalfLine) / RingBottomFactor
    val byWidth = (size.width - horizontalPadding * 2) * GaugeWidthShare - inset * 2
    val diameter =
        minOf(byHeight, byWidth, CalorieGaugeWidgetSizes.MaxDiameter)
            .coerceAtLeast(CalorieGaugeWidgetSizes.MinDiameter)
    return CalorieGaugeLayoutSpec(
        verticalPadding = verticalPadding,
        horizontalPadding = horizontalPadding,
        showHeader = showHeader,
        diameter = diameter,
        stroke = stroke,
        markerRadius = markerRadius,
        markerDotRadius = if (large) 3.5.dp else 3.dp,
        zoneLabelClearance = if (large) 10.dp else 8.dp,
        zoneTextSize = if (large) 11.sp else 10.sp,
        endTextSize = endTextSize,
        unitTextSize = if (large) 12.sp else 11.sp,
        metricValueTextSize = if (large) 18.sp else 16.sp,
        metricLabelTextSize = if (large) 11.sp else 10.sp,
        fontScale = fontScale,
    )
}

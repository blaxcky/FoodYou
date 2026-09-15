package com.maksimowiczm.foodyou.app.widget.ring

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

internal enum class GoalRowsMode {
    /** Not enough height: only ring and metrics. */
    None,
    /** Optimized and diet as one compact text line without bars. */
    Inline,
    /** Optimized and diet as two rows with progress bars. */
    Bars,
}

/** Layout parameters derived from the widget's actual size so the content fills the cell. */
internal data class CalorieRingLayoutSpec(
    val padding: Dp,
    val goalRows: GoalRowsMode,
    val metricsStacked: Boolean,
    val headerHeight: Dp,
    val headerTextSize: TextUnit,
    val ring: Dp,
    val stroke: Dp,
    val ringValueTextSize: TextUnit,
    val ringLabelTextSize: TextUnit,
    val metricLabelTextSize: TextUnit,
    val metricValueTextSize: TextUnit,
    val goalRowHeight: Dp,
    val goalTextSize: TextUnit,
    val goalValueTextSize: TextUnit,
)

internal object CalorieRingWidgetSizes {
    val MinSize = DpSize(250.dp, 110.dp)
    val InlineGoalsMinHeight = 150.dp
    val BarGoalsMinHeight = 200.dp
    val GoalRowHeight = 20.dp
    val HeaderSpacing = 4.dp
    val StackedMetricsMinHeight = 84.dp
    val LargeMetricsMinHeight = 100.dp
}

internal fun goalRowsMode(height: Dp): GoalRowsMode =
    when {
        height >= CalorieRingWidgetSizes.BarGoalsMinHeight -> GoalRowsMode.Bars
        height >= CalorieRingWidgetSizes.InlineGoalsMinHeight -> GoalRowsMode.Inline
        else -> GoalRowsMode.None
    }

internal fun goalBlockHeight(mode: GoalRowsMode): Dp =
    when (mode) {
        GoalRowsMode.None -> 0.dp
        GoalRowsMode.Inline -> 6.dp + CalorieRingWidgetSizes.GoalRowHeight
        GoalRowsMode.Bars ->
            8.dp + CalorieRingWidgetSizes.GoalRowHeight + 4.dp + CalorieRingWidgetSizes.GoalRowHeight
    }

internal fun calorieRingLayoutSpec(size: DpSize): CalorieRingLayoutSpec {
    val goalRows = goalRowsMode(size.height)
    val large = goalRows != GoalRowsMode.None
    val padding = if (large) 14.dp else 10.dp
    val headerHeight = if (large) 22.dp else 20.dp
    val heroHeight =
        size.height - padding * 2 - headerHeight - CalorieRingWidgetSizes.HeaderSpacing -
            goalBlockHeight(goalRows)
    val ring = minOf(heroHeight, size.width * 0.42f).coerceIn(56.dp, 140.dp)
    val stroke = (ring / 11f).coerceIn(6.dp, 12.dp)
    val largeMetrics = ring >= 90.dp && heroHeight >= CalorieRingWidgetSizes.LargeMetricsMinHeight
    return CalorieRingLayoutSpec(
        padding = padding,
        goalRows = goalRows,
        metricsStacked = heroHeight >= CalorieRingWidgetSizes.StackedMetricsMinHeight,
        headerHeight = headerHeight,
        headerTextSize = if (large) 14.sp else 13.sp,
        ring = ring,
        stroke = stroke,
        ringValueTextSize = (ring.value * 0.2f).roundToInt().coerceIn(14, 28).sp,
        ringLabelTextSize = (ring.value * 0.11f).roundToInt().coerceIn(9, 13).sp,
        metricLabelTextSize = if (largeMetrics) 12.sp else 11.sp,
        metricValueTextSize = if (largeMetrics) 20.sp else 16.sp,
        goalRowHeight = CalorieRingWidgetSizes.GoalRowHeight,
        goalTextSize = if (large) 12.sp else 11.sp,
        goalValueTextSize = if (large) 13.sp else 12.sp,
    )
}

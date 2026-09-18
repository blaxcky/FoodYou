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
    val horizontalPadding: Dp,
    val goalRows: GoalRowsMode,
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
    val InlineGoalsMinHeight = 134.dp
    val BarGoalsMinHeight = 158.dp
    val GoalRowHeight = 22.dp
    val HeaderSpacing = 4.dp
    val LargeMetricsMinHeight = 90.dp
    val MediumMetricsMinHeight = 70.dp
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
            6.dp + CalorieRingWidgetSizes.GoalRowHeight + 2.dp + CalorieRingWidgetSizes.GoalRowHeight
    }

internal fun calorieRingLayoutSpec(size: DpSize): CalorieRingLayoutSpec {
    val goalRows = goalRowsMode(size.height)
    val large = goalRows != GoalRowsMode.None
    val padding = if (large) 12.dp else 10.dp
    val horizontalPadding = padding + 4.dp
    val headerHeight = 20.dp
    val heroHeight =
        size.height - padding * 2 - headerHeight - CalorieRingWidgetSizes.HeaderSpacing -
            goalBlockHeight(goalRows)
    val ring = minOf(heroHeight, size.width * 0.42f).coerceIn(56.dp, 140.dp)
    val stroke = (ring / 11f).coerceIn(6.dp, 12.dp)
    return CalorieRingLayoutSpec(
        padding = padding,
        horizontalPadding = horizontalPadding,
        goalRows = goalRows,
        headerHeight = headerHeight,
        headerTextSize = if (large) 14.sp else 13.sp,
        ring = ring,
        stroke = stroke,
        ringValueTextSize = (ring.value * 0.2f).roundToInt().coerceIn(14, 28).sp,
        ringLabelTextSize = (ring.value * 0.11f).roundToInt().coerceIn(9, 13).sp,
        metricLabelTextSize =
            if (heroHeight >= CalorieRingWidgetSizes.MediumMetricsMinHeight) 12.sp else 11.sp,
        metricValueTextSize =
            when {
                heroHeight >= CalorieRingWidgetSizes.LargeMetricsMinHeight -> 22.sp
                heroHeight >= CalorieRingWidgetSizes.MediumMetricsMinHeight -> 20.sp
                else -> 16.sp
            },
        goalRowHeight = CalorieRingWidgetSizes.GoalRowHeight,
        goalTextSize = if (large) 12.sp else 11.sp,
        goalValueTextSize = if (large) 13.sp else 12.sp,
    )
}

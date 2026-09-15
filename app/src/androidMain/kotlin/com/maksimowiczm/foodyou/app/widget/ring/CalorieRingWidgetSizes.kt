package com.maksimowiczm.foodyou.app.widget.ring

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.LocalSize

internal object CalorieRingWidgetSizes {
    val Compact = DpSize(250.dp, 110.dp)
    val Tall = DpSize(250.dp, 180.dp)

    val CompactPadding = 10.dp
    val TallPadding = 12.dp
    val HeaderHeight = 20.dp
    val GoalRowHeight = 18.dp
    val GoalRowsBlock = 8.dp + GoalRowHeight + 4.dp + GoalRowHeight
}

@Composable
internal fun isTallWidget(): Boolean = LocalSize.current.height >= CalorieRingWidgetSizes.Tall.height

internal fun ringDiameter(widgetHeight: Dp, tall: Boolean): Dp {
    val padding = if (tall) CalorieRingWidgetSizes.TallPadding else CalorieRingWidgetSizes.CompactPadding
    val fixed =
        padding * 2 +
            CalorieRingWidgetSizes.HeaderHeight +
            4.dp +
            (if (tall) CalorieRingWidgetSizes.GoalRowsBlock else 0.dp)
    return (widgetHeight - fixed).coerceIn(56.dp, 104.dp)
}

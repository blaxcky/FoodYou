package com.maksimowiczm.foodyou.app.widget

import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.util.SizeF
import com.maksimowiczm.foodyou.R

internal object CalorieWidgetLayoutSelector {
    private const val MediumWidthDp = 250f
    private const val MediumHeightDp = 110f
    private const val LargeWidthDp = 250f
    private const val LargeMinHeightDp = 300

    val mediumSize = SizeF(MediumWidthDp, MediumHeightDp)
    val largeSize = SizeF(LargeWidthDp, LargeMinHeightDp.toFloat())

    fun layout(options: Bundle): Int =
        if (isLarge(options)) {
            R.layout.widget_calories_large
        } else {
            R.layout.widget_calories_medium
        }

    fun isLarge(options: Bundle): Boolean =
        options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) >= LargeMinHeightDp
}

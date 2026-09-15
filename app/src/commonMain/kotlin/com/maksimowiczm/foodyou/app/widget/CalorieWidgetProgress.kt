package com.maksimowiczm.foodyou.app.widget

import com.maksimowiczm.foodyou.app.ui.home.goals.calorieGoalProgress

internal data class CalorieWidgetProgress(val progress: Float, val overflow: Float)

internal fun calorieWidgetProgress(netKcal: Int, goalKcal: Int): CalorieWidgetProgress =
    calorieGoalProgress(netKcal, goalKcal, goalKcal).let {
        CalorieWidgetProgress(progress = it.progress, overflow = it.overflowProgress)
    }

package com.maksimowiczm.foodyou.app.widget

import com.maksimowiczm.foodyou.app.ui.home.goals.GoalEnergyOptimizationDay
import com.maksimowiczm.foodyou.app.ui.home.goals.adjustedEnergyGoalKcal
import com.maksimowiczm.foodyou.app.ui.home.goals.optimizedEnergyGoalKcal
import com.maksimowiczm.foodyou.app.ui.home.goals.roundedEnergyKcal
import com.maksimowiczm.foodyou.app.ui.home.goals.roundedNetEnergyKcal
import com.maksimowiczm.foodyou.app.ui.home.goals.roundedRemainingEnergyKcal
import kotlinx.datetime.LocalDate

internal data class CalorieWidgetModel(
    val date: LocalDate,
    val eatenKcal: Int,
    val burnedKcal: Int,
    val normalLeftKcal: Int,
    val optimizedLeftKcal: Int?,
    val dietLeftKcal: Int?,
)

internal fun calorieWidgetModel(
    today: LocalDate,
    eatenKcal: Double,
    burnedKcal: Double,
    baseGoalKcal: Double,
    dietEnergyDeficitKcal: Double?,
    previousDays: List<GoalEnergyOptimizationDay>,
): CalorieWidgetModel {
    val netEnergyKcal = roundedNetEnergyKcal(eatenKcal, burnedKcal)
    val optimizedGoal =
        optimizedEnergyGoalKcal(
            selectedDate = today,
            today = today,
            baseEnergyGoalKcal = baseGoalKcal,
            previousDays = previousDays,
        )
    val dietDeficit = dietEnergyDeficitKcal?.takeIf { it > 0.0 }
    val dietGoal =
        dietDeficit?.let {
            adjustedEnergyGoalKcal(
                selectedDate = today,
                today = today,
                baseEnergyGoalKcal = baseGoalKcal,
                dailyEnergyDeficitKcal = it,
                previousDays = previousDays,
            )
        }

    val normalLeftKcal = roundedRemainingEnergyKcal(baseGoalKcal, netEnergyKcal)
    val optimizedLeftKcal = roundedRemainingEnergyKcal(optimizedGoal, netEnergyKcal)

    return CalorieWidgetModel(
        date = today,
        eatenKcal = roundedEnergyKcal(eatenKcal),
        burnedKcal = roundedEnergyKcal(burnedKcal),
        normalLeftKcal = normalLeftKcal,
        optimizedLeftKcal = optimizedLeftKcal.takeIf { it != normalLeftKcal },
        dietLeftKcal = dietGoal?.let { roundedRemainingEnergyKcal(it, netEnergyKcal) },
    )
}

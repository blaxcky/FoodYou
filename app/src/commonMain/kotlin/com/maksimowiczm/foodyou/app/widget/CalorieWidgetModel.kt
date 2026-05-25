package com.maksimowiczm.foodyou.app.widget

import com.maksimowiczm.foodyou.app.ui.home.goals.GoalEnergyOptimizationDay
import com.maksimowiczm.foodyou.app.ui.home.goals.adjustedEnergyGoalKcal
import com.maksimowiczm.foodyou.app.ui.home.goals.optimizedEnergyGoalKcal
import kotlin.math.roundToInt
import kotlinx.datetime.LocalDate

internal data class CalorieWidgetModel(
    val date: LocalDate,
    val eatenKcal: Int,
    val burnedKcal: Int,
    val normalLeftKcal: Int,
    val optimizedLeftKcal: Int,
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
    val netEnergy = eatenKcal - burnedKcal
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

    return CalorieWidgetModel(
        date = today,
        eatenKcal = eatenKcal.roundToInt(),
        burnedKcal = burnedKcal.roundToInt(),
        normalLeftKcal = (baseGoalKcal - netEnergy).roundToInt(),
        optimizedLeftKcal = (optimizedGoal - netEnergy).roundToInt(),
        dietLeftKcal = dietGoal?.let { (it - netEnergy).roundToInt() },
    )
}

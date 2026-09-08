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
    val countedSteps: Long,
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
    countedSteps: Long = 0,
    baseGoalKcal: Double,
    dietEnergyDeficitKcal: Double?,
    todayEnergyGoalReductionKcal: Double? = null,
    previousDays: List<GoalEnergyOptimizationDay>,
    plannedFutureDays: List<GoalEnergyOptimizationDay> = emptyList(),
): CalorieWidgetModel {
    val netEnergyKcal = roundedNetEnergyKcal(eatenKcal, burnedKcal)
    val optimizedGoal =
        optimizedEnergyGoalKcal(
            selectedDate = today,
            calculationDate = today,
            baseEnergyGoalKcal = baseGoalKcal,
            previousDays = previousDays,
            plannedFutureDays = plannedFutureDays,
        )
    val dietDeficit = dietEnergyDeficitKcal?.takeIf { it > 0.0 }
    val dietGoal =
        dietDeficit?.let {
            adjustedEnergyGoalKcal(
                selectedDate = today,
                calculationDate = today,
                baseEnergyGoalKcal = baseGoalKcal,
                dailyEnergyDeficitKcal = it,
                previousDays = previousDays,
                plannedFutureDays = plannedFutureDays,
            )
        }

    val baseNormalLeftKcal = roundedRemainingEnergyKcal(baseGoalKcal, netEnergyKcal)
    val normalLeftKcal =
        todayEnergyGoalReductionKcal
            ?.takeIf { it > 0.0 && it <= baseGoalKcal }
            ?.let { roundedRemainingEnergyKcal(baseGoalKcal - it, netEnergyKcal) }
            ?: baseNormalLeftKcal
    val optimizedLeftKcal = roundedRemainingEnergyKcal(optimizedGoal, netEnergyKcal)

    return CalorieWidgetModel(
        date = today,
        countedSteps = countedSteps,
        eatenKcal = roundedEnergyKcal(eatenKcal),
        burnedKcal = roundedEnergyKcal(burnedKcal),
        normalLeftKcal = normalLeftKcal,
        optimizedLeftKcal = optimizedLeftKcal.takeIf { it != baseNormalLeftKcal },
        dietLeftKcal = dietGoal?.let { roundedRemainingEnergyKcal(it, netEnergyKcal) },
    )
}

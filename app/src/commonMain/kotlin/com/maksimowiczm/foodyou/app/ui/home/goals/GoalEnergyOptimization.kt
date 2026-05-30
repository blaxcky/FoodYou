package com.maksimowiczm.foodyou.app.ui.home.goals

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlin.math.roundToInt

internal data class GoalEnergyOptimizationDay(
    val consumedEnergyKcal: Double,
    val baseEnergyGoalKcal: Double,
    val burnedEnergyKcal: Double,
)

internal fun optimizedEnergyGoalKcal(
    selectedDate: LocalDate,
    today: LocalDate,
    baseEnergyGoalKcal: Double,
    previousDays: List<GoalEnergyOptimizationDay>,
): Double =
    adjustedEnergyGoalKcal(
        selectedDate = selectedDate,
        today = today,
        baseEnergyGoalKcal = baseEnergyGoalKcal,
        dailyEnergyDeficitKcal = 0.0,
        previousDays = previousDays,
        includeExtraSavings = false,
    )

internal fun adjustedEnergyGoalKcal(
    selectedDate: LocalDate,
    today: LocalDate,
    baseEnergyGoalKcal: Double,
    dailyEnergyDeficitKcal: Double,
    previousDays: List<GoalEnergyOptimizationDay>,
): Double =
    adjustedEnergyGoalKcal(
        selectedDate = selectedDate,
        today = today,
        baseEnergyGoalKcal = baseEnergyGoalKcal,
        dailyEnergyDeficitKcal = dailyEnergyDeficitKcal,
        previousDays = previousDays,
        includeExtraSavings = true,
    )

internal fun energyGoalDiffersFromBase(
    baseEnergyGoalKcal: Double,
    adjustedEnergyGoalKcal: Double,
): Boolean = baseEnergyGoalKcal.roundToInt() != adjustedEnergyGoalKcal.roundToInt()

private fun adjustedEnergyGoalKcal(
    selectedDate: LocalDate,
    today: LocalDate,
    baseEnergyGoalKcal: Double,
    dailyEnergyDeficitKcal: Double,
    previousDays: List<GoalEnergyOptimizationDay>,
    includeExtraSavings: Boolean,
): Double {
    if (selectedDate.startOfWeek() != today.startOfWeek()) return baseEnergyGoalKcal

    val dailyTarget = baseEnergyGoalKcal - dailyEnergyDeficitKcal
    val balance =
        previousDays.sumOf { day ->
            day.consumedEnergyKcal -
                (day.baseEnergyGoalKcal + day.burnedEnergyKcal - dailyEnergyDeficitKcal)
        }
    val adjustmentBalance =
        if (includeExtraSavings) {
            balance
        } else {
            balance.coerceAtLeast(0.0)
        }
    val remainingDays = (8 - selectedDate.dayOfWeek.isoDayNumber).coerceAtLeast(1)
    val dailyAdjustment = adjustmentBalance / remainingDays

    return (dailyTarget - dailyAdjustment).coerceAtMost(baseEnergyGoalKcal)
}

internal fun LocalDate.startOfWeek(): LocalDate =
    minus(dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)

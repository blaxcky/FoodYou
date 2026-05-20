package com.maksimowiczm.foodyou.app.ui.home.goals

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus

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
): Double {
    if (selectedDate.startOfWeek() != today.startOfWeek()) return baseEnergyGoalKcal

    val surplus =
        previousDays.sumOf { day ->
            day.consumedEnergyKcal - (day.baseEnergyGoalKcal + day.burnedEnergyKcal)
        }.coerceAtLeast(0.0)
    val remainingDays = (8 - selectedDate.dayOfWeek.isoDayNumber).coerceAtLeast(1)
    val dailyAdjustment = surplus / remainingDays

    return baseEnergyGoalKcal - dailyAdjustment
}

internal fun LocalDate.startOfWeek(): LocalDate =
    minus(dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)

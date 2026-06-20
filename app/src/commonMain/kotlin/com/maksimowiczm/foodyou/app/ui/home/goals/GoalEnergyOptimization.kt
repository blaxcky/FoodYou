package com.maksimowiczm.foodyou.app.ui.home.goals

import com.maksimowiczm.foodyou.activity.domain.usecase.calculateNetEnergyKcal
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
    plannedFutureDays: List<GoalEnergyOptimizationDay> = emptyList(),
): Double =
    adjustedEnergyGoalKcal(
        selectedDate = selectedDate,
        today = today,
        baseEnergyGoalKcal = baseEnergyGoalKcal,
        dailyEnergyDeficitKcal = 0.0,
        previousDays = previousDays,
        plannedFutureDays = plannedFutureDays,
        includeExtraSavings = false,
    )

internal fun adjustedEnergyGoalKcal(
    selectedDate: LocalDate,
    today: LocalDate,
    baseEnergyGoalKcal: Double,
    dailyEnergyDeficitKcal: Double,
    previousDays: List<GoalEnergyOptimizationDay>,
    plannedFutureDays: List<GoalEnergyOptimizationDay> = emptyList(),
): Double =
    adjustedEnergyGoalKcal(
        selectedDate = selectedDate,
        today = today,
        baseEnergyGoalKcal = baseEnergyGoalKcal,
        dailyEnergyDeficitKcal = dailyEnergyDeficitKcal,
        previousDays = previousDays,
        plannedFutureDays = plannedFutureDays,
        includeExtraSavings = true,
    )

internal fun energyGoalDiffersFromBase(
    baseEnergyGoalKcal: Double,
    adjustedEnergyGoalKcal: Double,
): Boolean = roundedEnergyKcal(baseEnergyGoalKcal) != roundedEnergyKcal(adjustedEnergyGoalKcal)

internal fun roundedEnergyKcal(energyKcal: Double): Int = energyKcal.roundToInt()

internal fun roundedNetEnergyKcal(consumedEnergy: Double, burnedEnergy: Double): Int =
    roundedEnergyKcal(calculateNetEnergyKcal(consumedEnergy, burnedEnergy))

internal fun roundedRemainingEnergyKcal(energyGoalKcal: Double, netEnergyKcal: Int): Int =
    roundedEnergyKcal(energyGoalKcal) - netEnergyKcal

private fun adjustedEnergyGoalKcal(
    selectedDate: LocalDate,
    today: LocalDate,
    baseEnergyGoalKcal: Double,
    dailyEnergyDeficitKcal: Double,
    previousDays: List<GoalEnergyOptimizationDay>,
    plannedFutureDays: List<GoalEnergyOptimizationDay>,
    includeExtraSavings: Boolean,
): Double {
    if (selectedDate.startOfWeek() != today.startOfWeek()) return baseEnergyGoalKcal

    val dailyTarget = baseEnergyGoalKcal - dailyEnergyDeficitKcal
    val actualBalance =
        previousDays.sumOf { day ->
            day.consumedEnergyKcal -
                (day.baseEnergyGoalKcal + day.burnedEnergyKcal - dailyEnergyDeficitKcal)
        }
    val plannedSurplus =
        plannedFutureDays.sumOf { day ->
            (day.consumedEnergyKcal -
                    (day.baseEnergyGoalKcal + day.burnedEnergyKcal - dailyEnergyDeficitKcal))
                .coerceAtLeast(0.0)
        }
    val adjustmentBalance =
        if (includeExtraSavings) {
            actualBalance + plannedSurplus
        } else {
            (actualBalance + plannedSurplus).coerceAtLeast(0.0)
        }
    val plannedSurplusDays =
        plannedFutureDays.count { day ->
            day.consumedEnergyKcal >
                day.baseEnergyGoalKcal + day.burnedEnergyKcal - dailyEnergyDeficitKcal
        }
    val remainingDays =
        (8 - selectedDate.dayOfWeek.isoDayNumber - plannedSurplusDays).coerceAtLeast(1)
    val dailyAdjustment = adjustmentBalance / remainingDays

    return (dailyTarget - dailyAdjustment).coerceAtMost(baseEnergyGoalKcal)
}

internal fun LocalDate.startOfWeek(): LocalDate =
    minus(dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)

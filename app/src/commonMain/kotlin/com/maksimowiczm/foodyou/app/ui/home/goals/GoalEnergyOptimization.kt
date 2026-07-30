package com.maksimowiczm.foodyou.app.ui.home.goals

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlin.math.roundToInt

internal data class GoalEnergyOptimizationDay(
    val date: LocalDate? = null,
    val consumedEnergyKcal: Double,
    val baseEnergyGoalKcal: Double,
    val burnedEnergyKcal: Double,
    val dietEnergyDeficitKcal: Double? = null,
    val lockedSurplusKcal: Double? = null,
)

internal fun optimizedEnergyGoalKcal(
    selectedDate: LocalDate,
    calculationDate: LocalDate,
    baseEnergyGoalKcal: Double,
    previousDays: List<GoalEnergyOptimizationDay>,
    plannedFutureDays: List<GoalEnergyOptimizationDay> = emptyList(),
): Double =
    adjustedEnergyGoalKcal(
        selectedDate = selectedDate,
        calculationDate = calculationDate,
        baseEnergyGoalKcal = baseEnergyGoalKcal,
        dailyEnergyDeficitKcal = 0.0,
        previousDays = previousDays,
        plannedFutureDays = plannedFutureDays,
        includeExtraSavings = false,
    )

internal fun adjustedEnergyGoalKcal(
    selectedDate: LocalDate,
    calculationDate: LocalDate,
    baseEnergyGoalKcal: Double,
    dailyEnergyDeficitKcal: Double,
    previousDays: List<GoalEnergyOptimizationDay>,
    plannedFutureDays: List<GoalEnergyOptimizationDay> = emptyList(),
): Double =
    adjustedEnergyGoalKcal(
        selectedDate = selectedDate,
        calculationDate = calculationDate,
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

// Net energy always derives from the individually rounded values shown in the UI.
internal fun roundedNetEnergyKcal(consumedEnergy: Double, burnedEnergy: Double): Int =
    roundedEnergyKcal(consumedEnergy) - roundedEnergyKcal(burnedEnergy)

internal fun roundedRemainingEnergyKcal(energyGoalKcal: Double, netEnergyKcal: Int): Int =
    roundedEnergyKcal(energyGoalKcal) - netEnergyKcal

private fun adjustedEnergyGoalKcal(
    selectedDate: LocalDate,
    calculationDate: LocalDate,
    baseEnergyGoalKcal: Double,
    dailyEnergyDeficitKcal: Double,
    previousDays: List<GoalEnergyOptimizationDay>,
    plannedFutureDays: List<GoalEnergyOptimizationDay>,
    includeExtraSavings: Boolean,
): Double {
    val dailyTarget = baseEnergyGoalKcal - dailyEnergyDeficitKcal
    if (selectedDate.startOfWeek() != calculationDate.startOfWeek()) return dailyTarget

    val dietMode = dailyEnergyDeficitKcal > 0.0
    val actualBalance =
        previousDays.sumOf { day ->
            val dayDeficit =
                if (dietMode) day.dietEnergyDeficitKcal ?: dailyEnergyDeficitKcal else 0.0
            day.simulatedConsumedEnergyKcal() -
                (day.baseEnergyGoalKcal + day.burnedEnergyKcal - dayDeficit)
        }
    val plannedSurplus =
        plannedFutureDays.sumOf { day ->
            val dayDeficit =
                if (dietMode) day.dietEnergyDeficitKcal ?: dailyEnergyDeficitKcal else 0.0
            (day.simulatedConsumedEnergyKcal() -
                    (day.baseEnergyGoalKcal + day.burnedEnergyKcal - dayDeficit))
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
            val dayDeficit =
                if (dietMode) day.dietEnergyDeficitKcal ?: dailyEnergyDeficitKcal else 0.0
            day.lockedSurplusKcal != null ||
                day.consumedEnergyKcal >
                    day.baseEnergyGoalKcal + day.burnedEnergyKcal - dayDeficit
        }
    val unplannedOpenDays =
        (8 - calculationDate.dayOfWeek.isoDayNumber - plannedSurplusDays).coerceAtLeast(1)
    val dailyAdjustment = adjustmentBalance / unplannedOpenDays

    val selectedDayIsOverplanned =
        plannedFutureDays.any { day ->
            val dayDeficit =
                if (dietMode) day.dietEnergyDeficitKcal ?: dailyEnergyDeficitKcal else 0.0
            day.date == selectedDate &&
                (day.lockedSurplusKcal != null ||
                    day.consumedEnergyKcal >
                        day.baseEnergyGoalKcal + day.burnedEnergyKcal - dayDeficit)
        }

    return if (selectedDayIsOverplanned) {
        dailyTarget
    } else {
        (dailyTarget - dailyAdjustment).coerceAtMost(baseEnergyGoalKcal)
    }
}

private fun GoalEnergyOptimizationDay.simulatedConsumedEnergyKcal(): Double =
    lockedSurplusKcal?.let { baseEnergyGoalKcal + burnedEnergyKcal + it }
        ?: consumedEnergyKcal

internal fun LocalDate.startOfWeek(): LocalDate =
    minus(dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)

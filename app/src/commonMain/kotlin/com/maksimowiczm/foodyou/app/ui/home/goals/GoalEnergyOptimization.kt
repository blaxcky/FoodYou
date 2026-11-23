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
    weeklyBalancingEnabled: Boolean = true,
): Double =
    balancedEnergyGoal(
        selectedDate = selectedDate,
        calculationDate = calculationDate,
        baseEnergyGoalKcal = baseEnergyGoalKcal,
        dailyEnergyDeficitKcal = 0.0,
        previousDays = previousDays,
        plannedFutureDays = plannedFutureDays,
        includeExtraSavings = false,
        weeklyBalancingEnabled = weeklyBalancingEnabled,
        maxEnergyDeficitKcal = null,
    )
        .energyGoalKcal

/**
 * A balanced energy goal and the part of the week's balance it no longer catches up.
 *
 * @param uncaughtBalanceKcal The energy the remaining open days of the week leave unbalanced because
 *   the diet goal is capped at the maximum daily energy deficit; 0 when the cap does not apply.
 */
internal data class AdjustedEnergyGoal(val energyGoalKcal: Double, val uncaughtBalanceKcal: Double)

internal fun adjustedEnergyGoalKcal(
    selectedDate: LocalDate,
    calculationDate: LocalDate,
    baseEnergyGoalKcal: Double,
    dailyEnergyDeficitKcal: Double,
    previousDays: List<GoalEnergyOptimizationDay>,
    plannedFutureDays: List<GoalEnergyOptimizationDay> = emptyList(),
    weeklyBalancingEnabled: Boolean = true,
    maxEnergyDeficitKcal: Double? = null,
): Double =
    adjustedEnergyGoal(
            selectedDate = selectedDate,
            calculationDate = calculationDate,
            baseEnergyGoalKcal = baseEnergyGoalKcal,
            dailyEnergyDeficitKcal = dailyEnergyDeficitKcal,
            previousDays = previousDays,
            plannedFutureDays = plannedFutureDays,
            weeklyBalancingEnabled = weeklyBalancingEnabled,
            maxEnergyDeficitKcal = maxEnergyDeficitKcal,
        )
        .energyGoalKcal

/**
 * The balanced goal. In diet mode (a positive [dailyEnergyDeficitKcal]) [maxEnergyDeficitKcal] caps
 * how far the catch-up lowers the goal: it never goes more than that below [baseEnergyGoalKcal], and
 * never above the plain diet target. What the cap leaves uncaught is not carried anywhere; the balance
 * starts over every Monday, so one excessive day cannot make the rest of the week unreachable.
 */
internal fun adjustedEnergyGoal(
    selectedDate: LocalDate,
    calculationDate: LocalDate,
    baseEnergyGoalKcal: Double,
    dailyEnergyDeficitKcal: Double,
    previousDays: List<GoalEnergyOptimizationDay>,
    plannedFutureDays: List<GoalEnergyOptimizationDay> = emptyList(),
    weeklyBalancingEnabled: Boolean = true,
    maxEnergyDeficitKcal: Double? = null,
): AdjustedEnergyGoal =
    balancedEnergyGoal(
        selectedDate = selectedDate,
        calculationDate = calculationDate,
        baseEnergyGoalKcal = baseEnergyGoalKcal,
        dailyEnergyDeficitKcal = dailyEnergyDeficitKcal,
        previousDays = previousDays,
        plannedFutureDays = plannedFutureDays,
        includeExtraSavings = true,
        weeklyBalancingEnabled = weeklyBalancingEnabled,
        maxEnergyDeficitKcal = maxEnergyDeficitKcal,
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

private fun balancedEnergyGoal(
    selectedDate: LocalDate,
    calculationDate: LocalDate,
    baseEnergyGoalKcal: Double,
    dailyEnergyDeficitKcal: Double,
    previousDays: List<GoalEnergyOptimizationDay>,
    plannedFutureDays: List<GoalEnergyOptimizationDay>,
    includeExtraSavings: Boolean,
    weeklyBalancingEnabled: Boolean,
    maxEnergyDeficitKcal: Double?,
): AdjustedEnergyGoal {
    val dailyTarget = baseEnergyGoalKcal - dailyEnergyDeficitKcal
    val unadjusted = AdjustedEnergyGoal(energyGoalKcal = dailyTarget, uncaughtBalanceKcal = 0.0)
    if (!weeklyBalancingEnabled) return unadjusted
    if (selectedDate.startOfWeek() != calculationDate.startOfWeek()) return unadjusted

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

    if (selectedDayIsOverplanned) return unadjusted

    val balancedGoal = (dailyTarget - dailyAdjustment).coerceAtMost(baseEnergyGoalKcal)
    val maxDeficit = maxEnergyDeficitKcal?.takeIf { dietMode && it > 0.0 }
    // The cap only limits the catch-up: a deficit above it keeps the plain diet target.
    val floor =
        maxDeficit?.let { baseEnergyGoalKcal - maxOf(it, dailyEnergyDeficitKcal) }
            ?: return AdjustedEnergyGoal(energyGoalKcal = balancedGoal, uncaughtBalanceKcal = 0.0)
    return if (balancedGoal < floor) {
        AdjustedEnergyGoal(
            energyGoalKcal = floor,
            uncaughtBalanceKcal = (floor - balancedGoal) * unplannedOpenDays,
        )
    } else {
        AdjustedEnergyGoal(energyGoalKcal = balancedGoal, uncaughtBalanceKcal = 0.0)
    }
}

private fun GoalEnergyOptimizationDay.simulatedConsumedEnergyKcal(): Double =
    lockedSurplusKcal?.let { baseEnergyGoalKcal + burnedEnergyKcal + it }
        ?: consumedEnergyKcal

/** The day's energy against its normal goal, burned energy included; negative below it. */
private fun GoalEnergyOptimizationDay.normalBalanceKcal(): Double =
    simulatedConsumedEnergyKcal() - (baseEnergyGoalKcal + burnedEnergyKcal)

/**
 * How the running week ends at best once the capped diet goal can no longer reach the week's diet
 * target; null while it still can.
 *
 * The best case keeps the days before [today] and every planned surplus day (locked, or already
 * logged above its diet target) as they are and lets every other remaining day end at most
 * [maxEnergyDeficitKcal] below its normal goal, like the capped diet goal. [today] counts the same
 * way, unless its intake is already above that, in which case it counts as it is. Until today
 * passes the capped goal, the amount above the diet target equals the goals card's uncaught
 * balance.
 *
 * @param weekDays The days of the week from Monday up to and including [today].
 * @param plannedFutureDays The days of the week after [today].
 */
internal fun weekDietOutlook(
    today: LocalDate,
    weekDays: List<GoalEnergyOptimizationDay>,
    plannedFutureDays: List<GoalEnergyOptimizationDay>,
    dietEnergyDeficitKcal: Double,
    maxEnergyDeficitKcal: Double?,
): WeekDietOutlook? {
    val cap = maxEnergyDeficitKcal?.takeIf { it > 0.0 } ?: return null
    if (dietEnergyDeficitKcal <= 0.0) return null
    val todayDay = weekDays.lastOrNull { it.date == today } ?: return null
    val previousDays = weekDays.filter { day -> day.date != null && day.date < today }
    fun GoalEnergyOptimizationDay.deficitKcal() =
        this.dietEnergyDeficitKcal ?: dietEnergyDeficitKcal

    val plannedSurplusDays =
        plannedFutureDays.filter { day ->
            day.lockedSurplusKcal != null || day.normalBalanceKcal() > -day.deficitKcal()
        }
    // Days after today without data count like open days with today's deficit.
    val remainingDays = 7 - today.dayOfWeek.isoDayNumber
    val openDays = (remainingDays - plannedSurplusDays.size).coerceAtLeast(0)
    val unlistedDays = (remainingDays - plannedFutureDays.size).coerceAtLeast(0)
    val maxDailyDeficit = maxOf(cap, dietEnergyDeficitKcal)
    val bestCase =
        previousDays.sumOf { it.normalBalanceKcal() } +
            plannedSurplusDays.sumOf { it.normalBalanceKcal() } +
            maxOf(todayDay.normalBalanceKcal(), -maxDailyDeficit) -
            openDays * maxDailyDeficit
    val dietTarget =
        -(previousDays.sumOf { it.deficitKcal() } +
            plannedFutureDays.sumOf { it.deficitKcal() } +
            unlistedDays * dietEnergyDeficitKcal +
            todayDay.deficitKcal())
    val overDietGoal = roundedEnergyKcal(bestCase - dietTarget).takeIf { it > 0 } ?: return null
    return WeekDietOutlook(bestCaseKcal = roundedEnergyKcal(bestCase), overDietGoalKcal = overDietGoal)
}

internal fun LocalDate.startOfWeek(): LocalDate =
    minus(dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)

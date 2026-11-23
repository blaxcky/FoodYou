package com.maksimowiczm.foodyou.app.ui.home.goals

import androidx.compose.runtime.*
import kotlinx.datetime.daysUntil

@Immutable
internal data class DaySummaryModel(
    val energy: Int,
    val burnedEnergy: Int,
    val netEnergy: Int,
    val energyGoal: Int,
    val showEnergyGoalValue: Boolean,
    val goalDisplayMode: GoalDisplayMode,
    val goalDisplaySummaries: List<GoalDisplaySummaryModel>,
    val dietGoalDisplayModeEnabled: Boolean,
    val proteins: Int,
    val proteinsGoal: Int,
    val carbohydrates: Int,
    val carbohydratesGoal: Int,
    val fats: Int,
    val fatsGoal: Int,
    val goalCardModeSwitchingEnabled: Boolean = true,
    val supplementalGoalsEnabled: Boolean = true,
    val todayEnergyGoal: Int? = null,
    val todayRemainingEnergy: Int? = null,
    val todayEnergyGoalReductionKcal: Double? = null,
    val todayEnergyGoalEditable: Boolean = false,
)

internal enum class GoalDisplayMode {
    Normal,
    Optimized,
    Diet,
}

@Immutable
internal data class GoalDisplaySummaryModel(
    val mode: GoalDisplayMode,
    val energyGoal: Int,
    val showEnergyGoalValue: Boolean,
    val percentageEnergyGoal: Int = energyGoal,
    /** The week's balance the capped diet goal no longer catches up; null when nothing is left. */
    val uncaughtBalanceKcal: Int? = null,
)

@Immutable
internal data class WeekSummaryModel(
    val days: List<WeekDaySummaryModel>,
    val totalEnergy: Int,
    val totalGoal: Int,
    val today: kotlinx.datetime.LocalDate,
    val dietOutlook: WeekDietOutlook? = null,
)

/**
 * How the current week ends at best when the capped diet goal can no longer reach the week's diet
 * target.
 *
 * @param bestCaseKcal The week's energy against its normal goals if every remaining day ends on the
 *   capped diet goal; negative for a deficit.
 * @param overDietGoalKcal How far that best case stays above the week's diet target.
 */
@Immutable
internal data class WeekDietOutlook(val bestCaseKcal: Int, val overDietGoalKcal: Int)

@Immutable
internal data class WeekDaySummaryModel(
    val date: kotlinx.datetime.LocalDate,
    val energy: Int,
    val goal: Int,
    val locked: Boolean = false,
    val dietGoalReached: Boolean = false,
) {
    val difference: Int = energy - goal
    val percent: Int =
        if (goal <= 0) 0 else (energy.toFloat() / goal * 100).toInt().coerceAtLeast(0)
}

/** The most recent visible weight measurement, as far as the weekly summary needs it. */
internal sealed interface LatestWeightState {
    data object Loading : LatestWeightState

    data object None : LatestWeightState

    data class Measured(val weightKg: Double, val date: kotlinx.datetime.LocalDate) :
        LatestWeightState
}

/** A measurement younger than this many days is shown next to the weekly weight estimate. */
internal const val RecentWeightMeasurementDays = 7

internal sealed interface WeeklyWeightStatus {
    data class Recent(val weightKg: Double, val daysAgo: Int) : WeeklyWeightStatus

    /** No measurement in the last [RecentWeightMeasurementDays] days; [daysAgo] null if none. */
    data class Stale(val daysAgo: Int?) : WeeklyWeightStatus
}

/** Null while the measurements are still loading. */
internal fun weeklyWeightStatus(
    latestWeight: LatestWeightState,
    today: kotlinx.datetime.LocalDate,
): WeeklyWeightStatus? =
    when (latestWeight) {
        LatestWeightState.Loading -> null
        LatestWeightState.None -> WeeklyWeightStatus.Stale(daysAgo = null)
        is LatestWeightState.Measured -> {
            val daysAgo = latestWeight.date.daysUntil(today).coerceAtLeast(0)
            if (daysAgo < RecentWeightMeasurementDays) {
                WeeklyWeightStatus.Recent(weightKg = latestWeight.weightKg, daysAgo = daysAgo)
            } else {
                WeeklyWeightStatus.Stale(daysAgo = daysAgo)
            }
        }
    }

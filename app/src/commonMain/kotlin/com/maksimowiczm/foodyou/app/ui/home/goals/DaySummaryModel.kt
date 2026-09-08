package com.maksimowiczm.foodyou.app.ui.home.goals

import androidx.compose.runtime.*

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
)

@Immutable
internal data class WeekSummaryModel(
    val days: List<WeekDaySummaryModel>,
    val totalEnergy: Int,
    val totalGoal: Int,
    val today: kotlinx.datetime.LocalDate,
)

@Immutable
internal data class WeekDaySummaryModel(
    val date: kotlinx.datetime.LocalDate,
    val energy: Int,
    val goal: Int,
    val locked: Boolean = false,
) {
    val difference: Int = energy - goal
    val percent: Int =
        if (goal <= 0) 0 else (energy.toFloat() / goal * 100).toInt().coerceAtLeast(0)
}

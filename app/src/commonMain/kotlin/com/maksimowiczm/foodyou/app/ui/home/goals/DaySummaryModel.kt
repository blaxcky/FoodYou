package com.maksimowiczm.foodyou.app.ui.home.goals

import androidx.compose.runtime.*

@Immutable
internal data class DaySummaryModel(
    val energy: Int,
    val burnedEnergy: Int,
    val netEnergy: Int,
    val energyGoal: Int,
    val optimizedGoalDisplayEnabled: Boolean,
    val proteins: Int,
    val proteinsGoal: Int,
    val carbohydrates: Int,
    val carbohydratesGoal: Int,
    val fats: Int,
    val fatsGoal: Int,
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
) {
    val difference: Int = energy - goal
    val percent: Int = if (goal <= 0) 0 else (energy.toFloat() / goal * 100).toInt()
}

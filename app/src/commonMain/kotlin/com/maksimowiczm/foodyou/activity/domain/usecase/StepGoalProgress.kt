package com.maksimowiczm.foodyou.activity.domain.usecase

data class StepGoalProgress(
    val goalSteps: Long,
    val steps: Long,
    val remainingSteps: Long,
    val fraction: Float,
    val reached: Boolean,
    /** Energy the remaining steps would add to the day's budget, or null without kcal per step. */
    val remainingEnergyKcal: Double?,
)

fun stepGoalProgress(
    countedSteps: Long,
    dailyStepGoal: Int?,
    kcalPerStep: Double?,
): StepGoalProgress? {
    val goal = dailyStepGoal?.takeIf { it > 0 }?.toLong() ?: return null
    val steps = countedSteps.coerceAtLeast(0)
    val remaining = (goal - steps).coerceAtLeast(0)

    return StepGoalProgress(
        goalSteps = goal,
        steps = steps,
        remainingSteps = remaining,
        fraction = (steps.toDouble() / goal).coerceIn(0.0, 1.0).toFloat(),
        reached = remaining == 0L,
        remainingEnergyKcal =
            kcalPerStep
                ?.takeIf { it > 0.0 }
                ?.let { calculateStepEnergyKcal(remaining, it) },
    )
}

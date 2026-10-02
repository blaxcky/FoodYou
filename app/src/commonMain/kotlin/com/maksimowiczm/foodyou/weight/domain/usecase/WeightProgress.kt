package com.maksimowiczm.foodyou.weight.domain.usecase

fun calculateWeightGoalProgress(
    startWeightKg: Double?,
    currentWeightKg: Double?,
    targetWeightKg: Double?,
): Float? {
    if (startWeightKg == null || currentWeightKg == null || targetWeightKg == null) return null
    val total = targetWeightKg - startWeightKg
    if (total == 0.0) return 1f
    val current = currentWeightKg - startWeightKg
    return (current / total).toFloat().coerceIn(0f, 1f)
}

/**
 * Position of the current weight on the way from start to target: 0 is the start, 1 the target.
 * Negative values mean the weight moved away from the target, values above 1 overshoot it.
 */
fun calculateWeightGoalPosition(
    startWeightKg: Double?,
    currentWeightKg: Double?,
    targetWeightKg: Double?,
): Double? {
    if (startWeightKg == null || currentWeightKg == null || targetWeightKg == null) return null
    val total = targetWeightKg - startWeightKg
    if (total == 0.0) return if (currentWeightKg == targetWeightKg) 1.0 else null
    return (currentWeightKg - startWeightKg) / total
}

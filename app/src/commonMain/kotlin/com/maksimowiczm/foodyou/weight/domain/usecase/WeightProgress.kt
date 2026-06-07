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

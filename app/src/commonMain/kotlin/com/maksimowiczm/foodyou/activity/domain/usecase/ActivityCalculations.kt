package com.maksimowiczm.foodyou.activity.domain.usecase

fun calculateStepEnergyKcal(steps: Long, kcalPerStep: Double?): Double =
    steps.coerceAtLeast(0) * (kcalPerStep ?: 0.0).coerceAtLeast(0.0)

fun calculateNetEnergyKcal(consumedKcal: Double, burnedKcal: Double): Double =
    consumedKcal - burnedKcal

fun calculateDiscountedActivityEnergyKcal(energyKcal: Double, discountPercent: Double): Double =
    energyKcal * (1.0 - discountPercent / 100.0)

fun String.toCompleteActivityDiscountPercentOrNull(): Double? {
    val normalized = trim().replace(',', '.')
    if (normalized.isEmpty() || normalized.endsWith(".")) return null

    return normalized.toDoubleOrNull()?.takeIf { it in 0.0..100.0 }
}

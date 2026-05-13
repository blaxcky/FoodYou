package com.maksimowiczm.foodyou.activity.domain.usecase

fun calculateStepEnergyKcal(steps: Long, kcalPerStep: Double?): Double =
    steps.coerceAtLeast(0) * (kcalPerStep ?: 0.0).coerceAtLeast(0.0)

fun calculateNetEnergyKcal(consumedKcal: Double, burnedKcal: Double): Double =
    consumedKcal - burnedKcal

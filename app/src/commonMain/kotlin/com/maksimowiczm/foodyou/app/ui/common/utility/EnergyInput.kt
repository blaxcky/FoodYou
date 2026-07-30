package com.maksimowiczm.foodyou.app.ui.common.utility

internal fun validatedNonNegativeEnergyKcal(
    input: String,
    toKcal: (Double) -> Double,
): Double? =
    input
        .trim()
        .replace(',', '.')
        .toDoubleOrNull()
        ?.takeIf { it.isFinite() }
        ?.let(toKcal)
        ?.takeIf { it.isFinite() && it >= 0.0 }

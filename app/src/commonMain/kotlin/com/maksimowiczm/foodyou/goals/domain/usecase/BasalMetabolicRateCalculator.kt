package com.maksimowiczm.foodyou.goals.domain.usecase

import com.maksimowiczm.foodyou.goals.domain.entity.BasalMetabolicRateProfile
import com.maksimowiczm.foodyou.goals.domain.entity.BasalMetabolicRateSuggestion
import com.maksimowiczm.foodyou.goals.domain.entity.BiologicalSex
import kotlin.math.roundToInt
import kotlinx.datetime.LocalDate

fun calculateBasalMetabolicRateSuggestion(
    profile: BasalMetabolicRateProfile,
    today: LocalDate,
): BasalMetabolicRateSuggestion? {
    val weightKg = profile.weightKg?.takeIf { it > 0 } ?: return null
    val heightCm = profile.heightCm?.takeIf { it > 0 } ?: return null
    val birthDate = profile.birthDate ?: return null
    val sex = profile.sex ?: return null
    val age = birthDate.ageInYearsAt(today).takeIf { it > 0 } ?: return null

    val sexAdjustment =
        when (sex) {
            BiologicalSex.Male -> 5
            BiologicalSex.Female -> -161
        }

    val basalMetabolicRate = 10 * weightKg + 6.25 * heightCm - 5 * age + sexAdjustment
    return BasalMetabolicRateSuggestion(
        basalMetabolicRateKcal = basalMetabolicRate.roundToInt(),
        minimalActivityKcal = (basalMetabolicRate * 1.05).roundToInt(),
    )
}

private fun LocalDate.ageInYearsAt(today: LocalDate): Int {
    var age = today.year - year
    if (today.month < month || today.month == month && today.day < day) {
        age--
    }
    return age
}

package com.maksimowiczm.foodyou.goals.domain.entity

import kotlinx.datetime.LocalDate

data class BasalMetabolicRateProfile(
    val weightKg: Double?,
    val heightCm: Double?,
    val birthDate: LocalDate?,
    val sex: BiologicalSex?,
) {
    companion object {
        val Empty =
            BasalMetabolicRateProfile(weightKg = null, heightCm = null, birthDate = null, sex = null)
    }
}

enum class BiologicalSex {
    Male,
    Female,
}

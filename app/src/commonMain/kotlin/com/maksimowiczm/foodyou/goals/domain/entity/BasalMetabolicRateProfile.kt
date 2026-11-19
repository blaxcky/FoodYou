package com.maksimowiczm.foodyou.goals.domain.entity

import kotlinx.datetime.LocalDate

/**
 * Body data used to estimate the basal metabolic rate, plus the values the user actually wants to
 * plan with.
 *
 * The estimate derived from [weightKg], [heightCm], [birthDate] and [sex] is only a suggestion.
 * [basalMetabolicRateKcal] is entered (or adopted from the estimate) by the user and stays fixed,
 * so a new weight measurement never moves the energy goal on its own.
 *
 * @param basalMetabolicRateKcal The fixed basal metabolic rate, or null when not set yet.
 * @param physicalActivityLevelPercent The physical activity level as a percentage of the basal
 *   metabolic rate (105 means PAL 1.05), or null for the default.
 * @param calculateEnergyGoal Whether the energy goal is derived from [basalMetabolicRateKcal] and
 *   the activity level instead of being typed in.
 */
data class BasalMetabolicRateProfile(
    val weightKg: Double?,
    val heightCm: Double?,
    val birthDate: LocalDate?,
    val sex: BiologicalSex?,
    val basalMetabolicRateKcal: Double? = null,
    val physicalActivityLevelPercent: Int? = null,
    val calculateEnergyGoal: Boolean = false,
) {
    val effectivePhysicalActivityLevelPercent: Int
        get() = physicalActivityLevelPercent ?: DEFAULT_PHYSICAL_ACTIVITY_LEVEL_PERCENT

    /** The energy goal derived from the fixed basal metabolic rate, or null when it is not set. */
    val calculatedEnergyGoalKcal: Double?
        get() =
            calculatedEnergyGoalKcal(
                basalMetabolicRateKcal = basalMetabolicRateKcal,
                physicalActivityLevelPercent = effectivePhysicalActivityLevelPercent,
            )

    companion object {
        const val DEFAULT_PHYSICAL_ACTIVITY_LEVEL_PERCENT = 105
        const val MIN_PHYSICAL_ACTIVITY_LEVEL_PERCENT = 100
        const val MAX_PHYSICAL_ACTIVITY_LEVEL_PERCENT = 250

        val Empty =
            BasalMetabolicRateProfile(weightKg = null, heightCm = null, birthDate = null, sex = null)

        fun calculatedEnergyGoalKcal(
            basalMetabolicRateKcal: Double?,
            physicalActivityLevelPercent: Int,
        ): Double? {
            val basalMetabolicRate = basalMetabolicRateKcal?.takeIf { it > 0.0 } ?: return null
            return basalMetabolicRate * physicalActivityLevelPercent / 100.0
        }
    }
}

enum class BiologicalSex {
    Male,
    Female,
}

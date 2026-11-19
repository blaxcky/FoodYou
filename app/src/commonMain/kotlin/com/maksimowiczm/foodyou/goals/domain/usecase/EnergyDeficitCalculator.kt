package com.maksimowiczm.foodyou.goals.domain.usecase

/** Common estimate of the energy stored in one kilogram of body fat. */
const val KCAL_PER_KG_BODY_FAT = 7700.0

/** Daily energy deficit needed to lose [weeklyWeightLossKg] per week (0.5 kg → 550 kcal). */
fun dailyEnergyDeficitKcal(weeklyWeightLossKg: Double): Double =
    weeklyWeightLossKg * KCAL_PER_KG_BODY_FAT / 7

/** Weekly weight loss a daily energy deficit leads to (550 kcal → 0.5 kg). */
fun weeklyWeightLossKg(dailyEnergyDeficitKcal: Double): Double =
    dailyEnergyDeficitKcal * 7 / KCAL_PER_KG_BODY_FAT

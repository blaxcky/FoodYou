package com.maksimowiczm.foodyou.activity.domain.entity

data class DailyActivitySummary(
    val steps: Long,
    val stepEnergyKcal: Double,
    val manualEnergyKcal: Double,
    val totalEnergyKcal: Double,
)

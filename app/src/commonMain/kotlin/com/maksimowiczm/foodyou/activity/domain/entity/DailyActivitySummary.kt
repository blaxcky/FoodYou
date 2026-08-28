package com.maksimowiczm.foodyou.activity.domain.entity

data class DailyActivitySummary(
    val rawSteps: Long,
    val excludedSteps: Long,
    val countedSteps: Long,
    val stepEnergyKcal: Double,
    val manualEnergyKcal: Double,
    val totalEnergyKcal: Double,
)

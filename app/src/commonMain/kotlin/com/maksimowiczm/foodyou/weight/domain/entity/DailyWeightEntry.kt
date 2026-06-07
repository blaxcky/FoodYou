package com.maksimowiczm.foodyou.weight.domain.entity

import kotlin.time.Instant
import kotlinx.datetime.LocalDate

data class DailyWeightEntry(
    val date: LocalDate,
    val weightKg: Double,
    val measuredAt: Instant,
    val healthConnectRecordId: String?,
    val isFoodYouRecord: Boolean,
)

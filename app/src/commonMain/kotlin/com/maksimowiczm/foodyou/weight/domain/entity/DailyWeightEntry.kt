package com.maksimowiczm.foodyou.weight.domain.entity

import kotlin.time.Instant
import kotlinx.datetime.LocalDate

data class DailyWeightEntry(
    val date: LocalDate,
    val weightKg: Double,
    val measuredAt: Instant,
    val healthConnectRecordId: String?,
    val isFoodYouRecord: Boolean,
    val id: String =
        if (isFoodYouRecord) "local:${date.toEpochDays()}"
        else if (healthConnectRecordId != null) "hc:$healthConnectRecordId"
        else "legacy:${date.toEpochDays()}:${measuredAt.epochSeconds}:$weightKg",
    val sourcePackageName: String? = null,
    val sourceDeviceType: Int? = null,
    val isHidden: Boolean = false,
)

package com.maksimowiczm.foodyou.activity.domain.entity

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

@JvmInline value class ManualActivityEntryId(val value: Long)

data class ManualActivityEntry(
    val id: ManualActivityEntryId,
    val date: LocalDate,
    val name: String,
    val energyKcal: Double,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

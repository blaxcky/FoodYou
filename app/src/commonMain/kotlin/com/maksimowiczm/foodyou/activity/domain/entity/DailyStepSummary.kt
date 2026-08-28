package com.maksimowiczm.foodyou.activity.domain.entity

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

data class DailyStepSummary(
    val date: LocalDate,
    val rawSteps: Long,
    val excludedSteps: Long,
    val syncedAt: Instant,
) {
    val countedSteps: Long = (rawSteps - excludedSteps).coerceAtLeast(0)
}

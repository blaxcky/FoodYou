package com.maksimowiczm.foodyou.activity.domain.entity

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

data class DailyStepSummary(val date: LocalDate, val steps: Long, val syncedAt: Instant)

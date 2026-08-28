package com.maksimowiczm.foodyou.activity.domain.entity

import kotlinx.datetime.LocalDate

data class StepExclusionPeriod(
    val date: LocalDate,
    val startMinute: Int,
    val endMinute: Int,
)

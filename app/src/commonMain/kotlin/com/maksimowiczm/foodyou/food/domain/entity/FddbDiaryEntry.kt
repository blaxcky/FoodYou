package com.maksimowiczm.foodyou.food.domain.entity

import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import kotlinx.datetime.LocalDate

data class FddbDiaryEntry(
    val entryId: String,
    val date: LocalDate,
    val mealName: String,
    val productName: String,
    val productUrl: String,
    val measurement: Measurement,
)

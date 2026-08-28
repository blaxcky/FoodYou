package com.maksimowiczm.foodyou.activity.infrastructure.room

import androidx.room.Entity

@Entity(
    tableName = "StepExclusionPeriod",
    primaryKeys = ["dateEpochDay", "startMinute", "endMinute"],
)
data class StepExclusionPeriodEntity(
    val dateEpochDay: Long,
    val startMinute: Int,
    val endMinute: Int,
)

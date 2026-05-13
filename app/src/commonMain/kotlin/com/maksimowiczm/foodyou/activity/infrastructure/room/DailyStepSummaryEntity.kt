package com.maksimowiczm.foodyou.activity.infrastructure.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "DailyStepSummary")
data class DailyStepSummaryEntity(
    @PrimaryKey val dateEpochDay: Long,
    val steps: Long,
    val syncedEpochSeconds: Long,
)

package com.maksimowiczm.foodyou.weight.infrastructure.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "DailyWeightEntry",
    indices = [Index("measuredEpochSeconds")],
)
data class DailyWeightEntryEntity(
    @PrimaryKey val dateEpochDay: Long,
    val measuredEpochSeconds: Long,
    val weightKg: Double,
    val healthConnectRecordId: String?,
    val isFoodYouRecord: Boolean,
)

package com.maksimowiczm.foodyou.weight.infrastructure.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "DailyWeightEntry",
    indices = [Index("dateEpochDay"), Index("measuredEpochSeconds")],
)
data class DailyWeightEntryEntity(
    @PrimaryKey val id: String,
    val dateEpochDay: Long,
    val measuredEpochSeconds: Long,
    val weightKg: Double,
    val healthConnectRecordId: String?,
    val isFoodYouRecord: Boolean,
    val sourcePackageName: String?,
    val sourceDeviceType: Int?,
    val isHidden: Boolean,
)

package com.maksimowiczm.foodyou.activity.infrastructure.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "ManualActivityEntry", indices = [Index(value = ["dateEpochDay"])])
data class ManualActivityEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val name: String,
    val energyKcal: Double,
    val createdEpochSeconds: Long,
    val updatedEpochSeconds: Long,
)

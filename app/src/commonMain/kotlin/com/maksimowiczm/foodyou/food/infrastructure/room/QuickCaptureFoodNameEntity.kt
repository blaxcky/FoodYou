package com.maksimowiczm.foodyou.food.infrastructure.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "QuickCaptureFoodName",
    indices = [Index(value = ["normalizedName"], unique = true)],
)
data class QuickCaptureFoodNameEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val normalizedName: String,
    val usageCount: Long,
    val lastUsedAt: Long,
)

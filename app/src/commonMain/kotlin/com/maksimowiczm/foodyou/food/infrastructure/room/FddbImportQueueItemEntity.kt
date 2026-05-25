package com.maksimowiczm.foodyou.food.infrastructure.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "FddbImportQueueItem", indices = [Index(value = ["url"], unique = true)])
data class FddbImportQueueItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val createdAt: Long,
    val lastAttemptedAt: Long?,
    val lastError: String?,
)

package com.maksimowiczm.foodyou.food.infrastructure.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "FddbProductSyncStatus",
    foreignKeys =
        [
            ForeignKey(
                entity = ProductEntity::class,
                parentColumns = ["id"],
                childColumns = ["productId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index(value = ["productId"])],
)
data class FddbProductSyncStatusEntity(
    @PrimaryKey val productId: Long,
    val lastSyncedAt: Long?,
    val lastAttemptAt: Long?,
    val lastError: String?,
)

data class FddbProductSyncQueueEntity(
    val productId: Long,
    val name: String,
    val brand: String?,
    val lastSyncedAt: Long?,
    val lastAttemptAt: Long?,
    val lastError: String?,
)

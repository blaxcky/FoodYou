package com.maksimowiczm.foodyou.food.infrastructure.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "PendingProduct", indices = [Index(value = ["barcode"], unique = true)])
data class PendingProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val barcode: String?,
    val photoPaths: String,
    val createdAt: Long,
    val updatedAt: Long?,
)

package com.maksimowiczm.foodyou.food.infrastructure.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "ProductPortionOverride",
    primaryKeys = ["productId", "normalizedLabel"],
    foreignKeys = [ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["productId"])],
)
data class ProductPortionOverrideEntity(
    val productId: Long,
    val normalizedLabel: String,
    val label: String,
    val amount: Double,
    val unit: String,
    val isDeleted: Boolean,
)

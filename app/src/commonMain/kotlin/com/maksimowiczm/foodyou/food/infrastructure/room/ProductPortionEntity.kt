package com.maksimowiczm.foodyou.food.infrastructure.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.maksimowiczm.foodyou.common.infrastructure.room.FoodSourceType

@Entity(
    tableName = "ProductPortion",
    foreignKeys =
        [
            ForeignKey(
                entity = ProductEntity::class,
                parentColumns = ["id"],
                childColumns = ["productId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices =
        [
            Index(value = ["productId"]),
            Index(value = ["productId", "sourceType", "normalizedLabel"], unique = true),
        ],
)
data class ProductPortionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val sourceType: FoodSourceType,
    val label: String,
    val normalizedLabel: String,
    val amount: Double,
    val unit: String,
)

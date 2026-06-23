package com.maksimowiczm.foodyou.food.infrastructure.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "FoodSnapEntry")
data class FoodSnapEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val photoPath: String,
    val createdAt: Long,
    val foodType: Int?,
    val foodId: Long?,
    val foodName: String?,
    val weightInGrams: Double?,
)

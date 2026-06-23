package com.maksimowiczm.foodyou.food.domain.entity

import kotlin.time.Instant

/** A photo captured during the one currently open FoodSnap session. */
data class FoodSnapEntry(
    val id: Long,
    val photoPath: String,
    val createdAt: Instant,
    val foodId: FoodId? = null,
    val foodName: String? = null,
    val weightInGrams: Double? = null,
) {
    val isProcessed: Boolean
        get() = foodId != null && foodName != null && weightInGrams != null
}

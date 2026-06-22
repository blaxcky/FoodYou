package com.maksimowiczm.foodyou.food.domain.entity

import kotlin.time.Instant

data class FddbProductSyncQueueItem(
    val productId: FoodId.Product,
    val name: String,
    val brand: String?,
    val lastSyncedAt: Instant?,
    val lastAttemptAt: Instant?,
    val lastError: String?,
)

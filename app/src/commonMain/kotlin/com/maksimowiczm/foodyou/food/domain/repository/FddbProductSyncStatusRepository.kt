package com.maksimowiczm.foodyou.food.domain.repository

import com.maksimowiczm.foodyou.food.domain.entity.FddbProductSyncQueueItem
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow

interface FddbProductSyncStatusRepository {
    fun observeQueue(): Flow<List<FddbProductSyncQueueItem>>

    suspend fun getDueProducts(limit: Int): List<FddbProductSyncQueueItem>

    suspend fun markSuccess(productId: FoodId.Product, syncedAt: Instant)

    suspend fun markFailure(productId: FoodId.Product, attemptedAt: Instant, error: String)
}

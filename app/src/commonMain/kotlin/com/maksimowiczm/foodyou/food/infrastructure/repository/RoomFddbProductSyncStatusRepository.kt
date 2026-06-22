package com.maksimowiczm.foodyou.food.infrastructure.repository

import com.maksimowiczm.foodyou.food.domain.entity.FddbProductSyncQueueItem
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.repository.FddbProductSyncStatusRepository
import com.maksimowiczm.foodyou.food.infrastructure.room.FddbProductSyncQueueEntity
import com.maksimowiczm.foodyou.food.infrastructure.room.FddbProductSyncStatusDao
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomFddbProductSyncStatusRepository(
    private val dao: FddbProductSyncStatusDao
) : FddbProductSyncStatusRepository {
    override fun observeQueue(): Flow<List<FddbProductSyncQueueItem>> =
        dao.observeQueue().map { items -> items.map { it.toModel() } }

    override suspend fun getDueProducts(limit: Int): List<FddbProductSyncQueueItem> =
        dao.getDueProducts(limit).map { it.toModel() }

    override suspend fun markSuccess(productId: FoodId.Product, syncedAt: Instant) {
        dao.markSuccess(productId = productId.id, syncedAt = syncedAt.epochSeconds)
    }

    override suspend fun markFailure(productId: FoodId.Product, attemptedAt: Instant, error: String) {
        dao.markFailure(productId = productId.id, attemptedAt = attemptedAt.epochSeconds, error = error)
    }
}

private fun FddbProductSyncQueueEntity.toModel(): FddbProductSyncQueueItem =
    FddbProductSyncQueueItem(
        productId = FoodId.Product(productId),
        name = name,
        brand = brand,
        lastSyncedAt = lastSyncedAt?.let(Instant::fromEpochSeconds),
        lastAttemptAt = lastAttemptAt?.let(Instant::fromEpochSeconds),
        lastError = lastError,
    )

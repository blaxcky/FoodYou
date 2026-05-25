package com.maksimowiczm.foodyou.food.infrastructure.repository

import com.maksimowiczm.foodyou.food.domain.entity.FddbImportQueueItem
import com.maksimowiczm.foodyou.food.domain.repository.FddbImportQueueRepository
import com.maksimowiczm.foodyou.food.infrastructure.room.FddbImportQueueDao
import com.maksimowiczm.foodyou.food.infrastructure.room.FddbImportQueueItemEntity
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomFddbImportQueueRepository(private val dao: FddbImportQueueDao) :
    FddbImportQueueRepository {
    override fun observeQueue(): Flow<List<FddbImportQueueItem>> =
        dao.observeQueue().map { items -> items.map { it.toModel() } }

    override suspend fun getQueue(): List<FddbImportQueueItem> =
        dao.getQueue().map { it.toModel() }

    override suspend fun add(url: String, createdAt: Instant) {
        dao.insert(
            FddbImportQueueItemEntity(
                url = url,
                createdAt = createdAt.toEpochMilliseconds(),
                lastAttemptedAt = null,
                lastError = null,
            )
        )
    }

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun markAttempt(id: Long, attemptedAt: Instant, error: String?) =
        dao.markAttempt(
            id = id,
            attemptedAt = attemptedAt.toEpochMilliseconds(),
            error = error,
        )
}

private fun FddbImportQueueItemEntity.toModel(): FddbImportQueueItem =
    FddbImportQueueItem(
        id = id,
        url = url,
        createdAt = Instant.fromEpochMilliseconds(createdAt),
        lastAttemptedAt = lastAttemptedAt?.let(Instant::fromEpochMilliseconds),
        lastError = lastError,
    )

package com.maksimowiczm.foodyou.food.domain.repository

import com.maksimowiczm.foodyou.food.domain.entity.FddbImportQueueItem
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow

interface FddbImportQueueRepository {
    fun observeQueue(): Flow<List<FddbImportQueueItem>>

    suspend fun getQueue(): List<FddbImportQueueItem>

    suspend fun add(url: String, createdAt: Instant)

    suspend fun delete(id: Long)

    suspend fun markAttempt(id: Long, attemptedAt: Instant, error: String?)
}

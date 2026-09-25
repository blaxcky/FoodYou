package com.maksimowiczm.foodyou.food.infrastructure.repository

import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.infrastructure.room.FddbProductSyncQueueEntity
import com.maksimowiczm.foodyou.food.infrastructure.room.FddbProductSyncStatusDao
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class RoomFddbProductSyncStatusRepositoryTest {
    @Test
    fun queueContainsProductSourceUrl() = runBlocking {
        val dao = FakeDao()
        dao.queue.value =
            listOf(
                FddbProductSyncQueueEntity(
                    productId = 7,
                    name = "Product",
                    brand = "Brand",
                    sourceUrl = Url,
                    lastSyncedAt = 100,
                    lastAttemptAt = 200,
                    lastError = "HTTP 404",
                )
            )

        val item = RoomFddbProductSyncStatusRepository(dao).observeQueue().first().single()

        assertEquals(FoodId.Product(7), item.productId)
        assertEquals(Url, item.sourceUrl)
        assertEquals(Instant.fromEpochSeconds(100), item.lastSyncedAt)
        assertEquals("HTTP 404", item.lastError)
    }

    @Test
    fun clearDelegatesWithoutDeletingProduct() = runBlocking {
        val dao = FakeDao()
        val repository = RoomFddbProductSyncStatusRepository(dao)

        repository.clear(FoodId.Product(7))

        assertEquals(listOf(7L), dao.cleared)
    }

    private class FakeDao : FddbProductSyncStatusDao {
        val queue = MutableStateFlow<List<FddbProductSyncQueueEntity>>(emptyList())
        val cleared = mutableListOf<Long>()

        override fun observeQueue(): Flow<List<FddbProductSyncQueueEntity>> = queue

        override suspend fun getDueProducts(limit: Int): List<FddbProductSyncQueueEntity> =
            queue.value.take(limit)

        override suspend fun markSuccess(productId: Long, syncedAt: Long) = Unit

        override suspend fun markFailure(productId: Long, attemptedAt: Long, error: String) = Unit

        override suspend fun clear(productId: Long) {
            cleared += productId
        }
    }

    private companion object {
        const val Url = "https://fddb.info/db/de/lebensmittel/product/index.html"
    }
}

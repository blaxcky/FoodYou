package com.maksimowiczm.foodyou.food.infrastructure.repository

import com.maksimowiczm.foodyou.food.infrastructure.room.FddbDiarySyncEntryDao
import com.maksimowiczm.foodyou.food.infrastructure.room.FddbDiarySyncEntryEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

class RoomFddbDiarySyncEntryRepositoryTest {
    @Test
    fun emptyIdsDoNotReadDatabase() = runTest {
        val dao = FakeDao()
        assertEquals(emptySet(), RoomFddbDiarySyncEntryRepository(dao).findSyncedIds(emptyList()))
        assertEquals(emptyList(), dao.batches)
    }

    @Test
    fun largeListsAreDeduplicatedAndReadInBoundedBatches() = runTest {
        val ids = (1..1801).map { it.toString() }
        val dao = FakeDao(ids.filterIndexed { index, _ -> index % 2 == 0 }.toSet())
        val found = RoomFddbDiarySyncEntryRepository(dao).findSyncedIds(ids + ids)
        assertEquals(dao.existing, found)
        assertEquals(listOf(900, 900, 1), dao.batches.map { it.size })
        assertEquals(ids, dao.batches.flatten())
    }

    @Test
    fun failedReadDoesNotReturnPartialResults() = runTest {
        val dao = FakeDao(setOf("first"), failOnBatch = 2)
        assertFailsWith<IllegalStateException> {
            RoomFddbDiarySyncEntryRepository(dao).findSyncedIds(listOf("first") + (1..900).map { "$it" })
        }
    }

    private class FakeDao(val existing: Set<String> = emptySet(), val failOnBatch: Int? = null) : FddbDiarySyncEntryDao {
        val batches = mutableListOf<List<String>>()
        override suspend fun findSyncedIds(ids: List<String>): List<String> {
            batches += ids
            if (batches.size == failOnBatch) error("Read failed")
            return ids.filter { it in existing }
        }
        override suspend fun insert(entity: FddbDiarySyncEntryEntity): Long = error("Not used")
    }
}

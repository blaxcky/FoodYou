package com.maksimowiczm.foodyou.food.infrastructure.repository

import com.maksimowiczm.foodyou.food.domain.repository.FddbDiarySyncEntryRepository
import com.maksimowiczm.foodyou.food.infrastructure.room.FddbDiarySyncEntryDao
import com.maksimowiczm.foodyou.food.infrastructure.room.FddbDiarySyncEntryEntity
import kotlin.time.Instant

internal class RoomFddbDiarySyncEntryRepository(private val dao: FddbDiarySyncEntryDao) :
    FddbDiarySyncEntryRepository {
    override suspend fun findSyncedIds(ids: List<String>): Set<String> =
        ids.distinct().chunked(900).flatMap { dao.findSyncedIds(it) }.toSet()

    override suspend fun add(fddbEntryId: String, syncedAt: Instant) {
        dao.insert(FddbDiarySyncEntryEntity(fddbEntryId = fddbEntryId, syncedAt = syncedAt.epochSeconds))
    }
}

package com.maksimowiczm.foodyou.food.infrastructure.repository

import com.maksimowiczm.foodyou.food.domain.repository.FddbDiarySyncEntryRepository
import com.maksimowiczm.foodyou.food.infrastructure.room.FddbDiarySyncEntryDao
import com.maksimowiczm.foodyou.food.infrastructure.room.FddbDiarySyncEntryEntity
import kotlin.time.Instant

internal class RoomFddbDiarySyncEntryRepository(private val dao: FddbDiarySyncEntryDao) :
    FddbDiarySyncEntryRepository {
    override suspend fun contains(fddbEntryId: String): Boolean = dao.contains(fddbEntryId)

    override suspend fun add(fddbEntryId: String, syncedAt: Instant) {
        dao.insert(FddbDiarySyncEntryEntity(fddbEntryId = fddbEntryId, syncedAt = syncedAt.epochSeconds))
    }
}

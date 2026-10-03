package com.maksimowiczm.foodyou.food.domain.repository

import kotlin.time.Instant

interface FddbDiarySyncEntryRepository {
    suspend fun findSyncedIds(ids: List<String>): Set<String>

    suspend fun add(fddbEntryId: String, syncedAt: Instant)
}

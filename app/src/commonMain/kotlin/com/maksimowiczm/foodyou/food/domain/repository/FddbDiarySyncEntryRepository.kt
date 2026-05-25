package com.maksimowiczm.foodyou.food.domain.repository

import kotlin.time.Instant

interface FddbDiarySyncEntryRepository {
    suspend fun contains(fddbEntryId: String): Boolean

    suspend fun add(fddbEntryId: String, syncedAt: Instant)
}

package com.maksimowiczm.foodyou.food.domain.repository

import com.maksimowiczm.foodyou.food.domain.entity.FoodSnapEntry
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

interface FoodSnapRepository {
    fun observeEntries(): Flow<List<FoodSnapEntry>>

    fun observeEntry(id: Long): Flow<FoodSnapEntry?>

    suspend fun insert(photoPath: String, createdAt: Instant): Long

    suspend fun update(entry: FoodSnapEntry)

    suspend fun delete(entry: FoodSnapEntry)
}

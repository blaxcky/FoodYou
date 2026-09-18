package com.maksimowiczm.foodyou.food.domain.repository

import com.maksimowiczm.foodyou.food.domain.entity.QuickCaptureFoodName
import com.maksimowiczm.foodyou.food.domain.entity.QuickCaptureLogEntry
import com.maksimowiczm.foodyou.food.domain.entity.QuickCaptureWeightMode
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

interface QuickCaptureRepository {
    fun observeFoodNames(): Flow<List<QuickCaptureFoodName>>

    fun observeEntries(): Flow<List<QuickCaptureLogEntry>>

    fun observeEntry(id: Long): Flow<QuickCaptureLogEntry?>

    suspend fun createEntry(
        foodName: String,
        weightMode: QuickCaptureWeightMode,
        directWeightInGrams: Double?,
        beforeWeightInGrams: Double?,
        afterWeightInGrams: Double?,
        createdAt: Instant,
    ): Long

    suspend fun capturePhoto(photoPath: String, createdAt: Instant): Long

    suspend fun processPhoto(id: Long, foodName: String, weightInGrams: Double, usedAt: Instant)

    suspend fun setAfterWeight(id: Long, afterWeightInGrams: Double)

    suspend fun markCompleted(ids: List<Long>, completedAt: Instant)

    suspend fun deleteEntries(ids: List<Long>)

    suspend fun renameFoodName(id: Long, name: String, usedAt: Instant)

    suspend fun deleteFoodName(id: Long)
}

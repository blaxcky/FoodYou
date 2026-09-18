package com.maksimowiczm.foodyou.food.infrastructure.repository

import com.maksimowiczm.foodyou.food.domain.entity.QuickCaptureFoodName
import com.maksimowiczm.foodyou.food.domain.entity.QuickCaptureLogEntry
import com.maksimowiczm.foodyou.food.domain.entity.QuickCaptureWeightMode
import com.maksimowiczm.foodyou.food.domain.entity.normalizeQuickCaptureFoodName
import com.maksimowiczm.foodyou.food.domain.repository.QuickCaptureRepository
import com.maksimowiczm.foodyou.food.infrastructure.room.QuickCaptureDao
import com.maksimowiczm.foodyou.food.infrastructure.room.QuickCaptureFoodNameEntity
import com.maksimowiczm.foodyou.food.infrastructure.room.QuickCaptureLogEntryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

internal class RoomQuickCaptureRepository(private val dao: QuickCaptureDao) :
    QuickCaptureRepository {
    override fun observeFoodNames(): Flow<List<QuickCaptureFoodName>> =
        dao.observeFoodNames().map { names -> names.map(QuickCaptureFoodNameEntity::toModel) }

    override fun observeEntries(): Flow<List<QuickCaptureLogEntry>> =
        dao.observeEntries().map { entries -> entries.map(QuickCaptureLogEntryEntity::toModel) }

    override fun observeEntry(id: Long): Flow<QuickCaptureLogEntry?> =
        dao.observeEntry(id).map { it?.toModel() }

    override suspend fun createEntry(
        foodName: String,
        weightMode: QuickCaptureWeightMode,
        directWeightInGrams: Double?,
        beforeWeightInGrams: Double?,
        afterWeightInGrams: Double?,
        createdAt: Instant,
    ): Long {
        val name =
            dao.resolveFoodName(
                name = foodName,
                normalizedName = normalizeQuickCaptureFoodName(foodName),
                usedAt = createdAt.toEpochMilliseconds(),
            )
        return dao.insertEntry(
            QuickCaptureLogEntryEntity(
                foodNameId = name.id,
                foodName = name.name,
                weightMode = weightMode.ordinal,
                directWeightInGrams = directWeightInGrams,
                beforeWeightInGrams = beforeWeightInGrams,
                afterWeightInGrams = afterWeightInGrams,
                afterRequired =
                    weightMode == QuickCaptureWeightMode.BeforeAfter &&
                        afterWeightInGrams == null,
                photoPath = null,
                createdAt = createdAt.toEpochMilliseconds(),
                completedAt = null,
            )
        )
    }

    override suspend fun capturePhoto(photoPath: String, createdAt: Instant): Long =
        dao.insertEntry(
            QuickCaptureLogEntryEntity(
                foodNameId = null,
                foodName = null,
                weightMode = QuickCaptureWeightMode.Direct.ordinal,
                directWeightInGrams = null,
                beforeWeightInGrams = null,
                afterWeightInGrams = null,
                afterRequired = false,
                photoPath = photoPath,
                createdAt = createdAt.toEpochMilliseconds(),
                completedAt = null,
            )
        )

    override suspend fun processPhoto(
        id: Long,
        foodName: String,
        weightInGrams: Double,
        usedAt: Instant,
    ) {
        val name =
            dao.resolveFoodName(
                name = foodName,
                normalizedName = normalizeQuickCaptureFoodName(foodName),
                usedAt = usedAt.toEpochMilliseconds(),
            )
        dao.processPhoto(id, name.id, name.name, weightInGrams)
    }

    override suspend fun setAfterWeight(id: Long, afterWeightInGrams: Double) =
        dao.setAfterWeight(id, afterWeightInGrams)

    override suspend fun markCompleted(ids: List<Long>, completedAt: Instant) =
        dao.markCompleted(ids, completedAt.toEpochMilliseconds())

    override suspend fun deleteEntries(ids: List<Long>) {
        if (ids.isNotEmpty()) dao.deleteEntries(ids)
    }

    override suspend fun renameFoodName(id: Long, name: String, usedAt: Instant) =
        dao.renameFoodName(
            id = id,
            name = name,
            normalizedName = normalizeQuickCaptureFoodName(name),
            usedAt = usedAt.toEpochMilliseconds(),
        )

    override suspend fun deleteFoodName(id: Long) = dao.deleteFoodName(id)
}

private fun QuickCaptureFoodNameEntity.toModel(): QuickCaptureFoodName =
    QuickCaptureFoodName(
        id = id,
        name = name,
        normalizedName = normalizedName,
        usageCount = usageCount,
        lastUsedAt = Instant.fromEpochMilliseconds(lastUsedAt),
    )

private fun QuickCaptureLogEntryEntity.toModel(): QuickCaptureLogEntry =
    QuickCaptureLogEntry(
        id = id,
        foodNameId = foodNameId,
        foodName = foodName,
        weightMode = QuickCaptureWeightMode.entries.getOrElse(weightMode) { QuickCaptureWeightMode.Direct },
        directWeightInGrams = directWeightInGrams,
        beforeWeightInGrams = beforeWeightInGrams,
        afterWeightInGrams = afterWeightInGrams,
        photoPath = photoPath,
        createdAt = Instant.fromEpochMilliseconds(createdAt),
        completedAt = completedAt?.let(Instant::fromEpochMilliseconds),
    )

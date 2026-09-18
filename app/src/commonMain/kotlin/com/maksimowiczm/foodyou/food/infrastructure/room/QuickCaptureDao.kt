package com.maksimowiczm.foodyou.food.infrastructure.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class QuickCaptureDao {
    @Query(
        """
        SELECT * FROM QuickCaptureFoodName
        ORDER BY usageCount DESC, lastUsedAt DESC, name COLLATE NOCASE ASC
        """
    )
    abstract fun observeFoodNames(): Flow<List<QuickCaptureFoodNameEntity>>

    @Query("SELECT * FROM QuickCaptureFoodName WHERE id = :id")
    abstract suspend fun getFoodName(id: Long): QuickCaptureFoodNameEntity?

    @Query("SELECT * FROM QuickCaptureFoodName WHERE normalizedName = :normalizedName LIMIT 1")
    abstract suspend fun getFoodNameByNormalizedName(
        normalizedName: String
    ): QuickCaptureFoodNameEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertFoodName(entity: QuickCaptureFoodNameEntity): Long

    @Update protected abstract suspend fun updateFoodName(entity: QuickCaptureFoodNameEntity)

    @Query("DELETE FROM QuickCaptureFoodName WHERE id = :id")
    abstract suspend fun deleteFoodName(id: Long)

    @Query("SELECT * FROM QuickCaptureLogEntry ORDER BY createdAt DESC, id DESC")
    abstract fun observeEntries(): Flow<List<QuickCaptureLogEntryEntity>>

    @Query("SELECT * FROM QuickCaptureLogEntry WHERE id = :id")
    abstract fun observeEntry(id: Long): Flow<QuickCaptureLogEntryEntity?>

    @Insert abstract suspend fun insertEntry(entity: QuickCaptureLogEntryEntity): Long

    @Update abstract suspend fun updateEntry(entity: QuickCaptureLogEntryEntity)

    @Query(
        """
        UPDATE QuickCaptureLogEntry
        SET foodNameId = :foodNameId,
            foodName = :foodName,
            weightMode = 0,
            directWeightInGrams = :weightInGrams,
            beforeWeightInGrams = NULL,
            afterWeightInGrams = NULL,
            afterRequired = 0
        WHERE id = :id
        """
    )
    abstract suspend fun processPhoto(
        id: Long,
        foodNameId: Long,
        foodName: String,
        weightInGrams: Double,
    )

    @Query("UPDATE QuickCaptureLogEntry SET afterWeightInGrams = :afterWeight WHERE id = :id")
    abstract suspend fun setAfterWeight(id: Long, afterWeight: Double)

    @Query("UPDATE QuickCaptureLogEntry SET completedAt = :completedAt WHERE id IN (:ids)")
    abstract suspend fun markCompleted(ids: List<Long>, completedAt: Long)

    @Query("DELETE FROM QuickCaptureLogEntry WHERE id IN (:ids)")
    abstract suspend fun deleteEntries(ids: List<Long>)

    @Query(
        """
        UPDATE QuickCaptureLogEntry
        SET foodNameId = :targetId, foodName = :targetName
        WHERE foodNameId = :sourceId
        """
    )
    protected abstract suspend fun moveEntriesToFoodName(
        sourceId: Long,
        targetId: Long,
        targetName: String,
    )

    @Query("UPDATE QuickCaptureLogEntry SET foodName = :name WHERE foodNameId = :foodNameId")
    protected abstract suspend fun updateEntryNames(foodNameId: Long, name: String)

    @Transaction
    open suspend fun resolveFoodName(
        name: String,
        normalizedName: String,
        usedAt: Long,
    ): QuickCaptureFoodNameEntity {
        val existing = getFoodNameByNormalizedName(normalizedName)
        if (existing != null) {
            val updated =
                existing.copy(
                    usageCount = existing.usageCount + 1,
                    lastUsedAt = usedAt,
                )
            updateFoodName(updated)
            return updated
        }

        val id =
            insertFoodName(
                QuickCaptureFoodNameEntity(
                    name = name,
                    normalizedName = normalizedName,
                    usageCount = 1,
                    lastUsedAt = usedAt,
                )
            )
        if (id != -1L) {
            return requireNotNull(getFoodName(id))
        }

        val raced = requireNotNull(getFoodNameByNormalizedName(normalizedName))
        val updated = raced.copy(usageCount = raced.usageCount + 1, lastUsedAt = usedAt)
        updateFoodName(updated)
        return updated
    }

    @Transaction
    open suspend fun renameFoodName(
        id: Long,
        name: String,
        normalizedName: String,
        usedAt: Long,
    ) {
        val source = getFoodName(id) ?: return
        val target = getFoodNameByNormalizedName(normalizedName)
        if (target == null || target.id == source.id) {
            updateFoodName(
                source.copy(
                    name = name,
                    normalizedName = normalizedName,
                    lastUsedAt = usedAt,
                )
            )
            updateEntryNames(source.id, name)
            return
        }

        val merged =
            target.copy(
                usageCount = target.usageCount + source.usageCount,
                lastUsedAt = maxOf(target.lastUsedAt, source.lastUsedAt, usedAt),
            )
        updateFoodName(merged)
        moveEntriesToFoodName(source.id, target.id, target.name)
        deleteFoodName(source.id)
    }
}

package com.maksimowiczm.foodyou.food.infrastructure.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FddbImportQueueDao {
    @Query("SELECT * FROM FddbImportQueueItem ORDER BY createdAt ASC")
    fun observeQueue(): Flow<List<FddbImportQueueItemEntity>>

    @Query("SELECT * FROM FddbImportQueueItem ORDER BY createdAt ASC")
    suspend fun getQueue(): List<FddbImportQueueItemEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: FddbImportQueueItemEntity): Long

    @Query("DELETE FROM FddbImportQueueItem WHERE id = :id")
    suspend fun delete(id: Long)

    @Query(
        """
        UPDATE FddbImportQueueItem
        SET lastAttemptedAt = :attemptedAt, lastError = :error
        WHERE id = :id
        """
    )
    suspend fun markAttempt(id: Long, attemptedAt: Long, error: String?)
}

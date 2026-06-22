package com.maksimowiczm.foodyou.food.infrastructure.room

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FddbProductSyncStatusDao {
    @Query(QUEUE_QUERY)
    fun observeQueue(): Flow<List<FddbProductSyncQueueEntity>>

    @Query("$QUEUE_QUERY LIMIT :limit")
    suspend fun getDueProducts(limit: Int): List<FddbProductSyncQueueEntity>

    @Query(
        """
        INSERT INTO FddbProductSyncStatus(productId, lastSyncedAt, lastAttemptAt, lastError)
        VALUES(:productId, :syncedAt, :syncedAt, NULL)
        ON CONFLICT(productId) DO UPDATE SET
            lastSyncedAt = excluded.lastSyncedAt,
            lastAttemptAt = excluded.lastAttemptAt,
            lastError = NULL
        """
    )
    suspend fun markSuccess(productId: Long, syncedAt: Long)

    @Query(
        """
        INSERT INTO FddbProductSyncStatus(productId, lastSyncedAt, lastAttemptAt, lastError)
        VALUES(:productId, NULL, :attemptedAt, :error)
        ON CONFLICT(productId) DO UPDATE SET
            lastAttemptAt = excluded.lastAttemptAt,
            lastError = excluded.lastError
        """
    )
    suspend fun markFailure(productId: Long, attemptedAt: Long, error: String)

    private companion object {
        const val QUEUE_QUERY =
            """
            SELECT
                Product.id AS productId,
                Product.name AS name,
                Product.brand AS brand,
                FddbProductSyncStatus.lastSyncedAt AS lastSyncedAt,
                FddbProductSyncStatus.lastAttemptAt AS lastAttemptAt,
                FddbProductSyncStatus.lastError AS lastError
            FROM Product
            LEFT JOIN FddbProductSyncStatus
                ON FddbProductSyncStatus.productId = Product.id
            WHERE Product.sourceType = 4
                AND Product.sourceUrl IS NOT NULL
                AND TRIM(Product.sourceUrl) != ''
            ORDER BY
                FddbProductSyncStatus.lastSyncedAt IS NOT NULL ASC,
                FddbProductSyncStatus.lastSyncedAt ASC,
                Product.id ASC
            """
    }
}

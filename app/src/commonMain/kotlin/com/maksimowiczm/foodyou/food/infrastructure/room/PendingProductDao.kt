package com.maksimowiczm.foodyou.food.infrastructure.room

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingProductDao {
    @Query("SELECT * FROM PendingProduct ORDER BY createdAt DESC")
    fun observePendingProducts(): Flow<List<PendingProductEntity>>

    @Query("SELECT * FROM PendingProduct WHERE id = :id")
    fun observePendingProduct(id: Long): Flow<PendingProductEntity?>

    @Query("SELECT * FROM PendingProduct WHERE barcode = :barcode LIMIT 1")
    fun observePendingProductByBarcode(barcode: String): Flow<PendingProductEntity?>

    @Insert suspend fun insertPendingProduct(entity: PendingProductEntity): Long

    @Delete suspend fun deletePendingProduct(entity: PendingProductEntity)
}

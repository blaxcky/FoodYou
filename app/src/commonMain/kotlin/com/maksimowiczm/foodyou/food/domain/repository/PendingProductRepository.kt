package com.maksimowiczm.foodyou.food.domain.repository

import com.maksimowiczm.foodyou.food.domain.entity.PendingProduct
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

interface PendingProductRepository {
    fun observePendingProducts(): Flow<List<PendingProduct>>

    fun observePendingProduct(id: Long): Flow<PendingProduct?>

    fun observePendingProductByBarcode(barcode: String): Flow<PendingProduct?>

    suspend fun insertPendingProduct(
        barcode: String?,
        photoPath: String,
        createdAt: Instant,
    ): Long

    suspend fun deletePendingProduct(pendingProduct: PendingProduct)
}

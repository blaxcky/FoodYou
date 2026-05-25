package com.maksimowiczm.foodyou.food.infrastructure.repository

import com.maksimowiczm.foodyou.food.domain.entity.PendingProduct
import com.maksimowiczm.foodyou.food.domain.repository.PendingProductRepository
import com.maksimowiczm.foodyou.food.infrastructure.room.PendingProductDao
import com.maksimowiczm.foodyou.food.infrastructure.room.PendingProductEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

internal class RoomPendingProductRepository(private val dao: PendingProductDao) :
    PendingProductRepository {
    override fun observePendingProducts(): Flow<List<PendingProduct>> =
        dao.observePendingProducts().map { list -> list.map { it.toModel() } }

    override fun observePendingProduct(id: Long): Flow<PendingProduct?> =
        dao.observePendingProduct(id).map { it?.toModel() }

    override fun observePendingProductByBarcode(barcode: String): Flow<PendingProduct?> =
        dao.observePendingProductByBarcode(barcode).map { it?.toModel() }

    override suspend fun insertPendingProduct(
        barcode: String?,
        photoPaths: List<String>,
        createdAt: Instant,
    ): Long =
        dao.insertPendingProduct(
            PendingProductEntity(
                barcode = barcode,
                photoPaths = photoPaths.joinToString(PHOTO_PATH_SEPARATOR),
                createdAt = createdAt.toEpochMilliseconds(),
                updatedAt = null,
            )
        )

    override suspend fun deletePendingProduct(pendingProduct: PendingProduct) {
        dao.deletePendingProduct(pendingProduct.toEntity())
    }

    override suspend fun updatePendingProduct(pendingProduct: PendingProduct) {
        dao.updatePendingProduct(pendingProduct.toEntity())
    }
}

private fun PendingProductEntity.toModel(): PendingProduct =
    PendingProduct(
        id = id,
        barcode = barcode,
        photoPaths = photoPaths.split(PHOTO_PATH_SEPARATOR).filter(String::isNotBlank),
        createdAt = Instant.fromEpochMilliseconds(createdAt),
        updatedAt = updatedAt?.let(Instant::fromEpochMilliseconds),
    )

private fun PendingProduct.toEntity(): PendingProductEntity =
    PendingProductEntity(
        id = id,
        barcode = barcode,
        photoPaths = photoPaths.joinToString(PHOTO_PATH_SEPARATOR),
        createdAt = createdAt.toEpochMilliseconds(),
        updatedAt = updatedAt?.toEpochMilliseconds(),
    )

private const val PHOTO_PATH_SEPARATOR = "\n"

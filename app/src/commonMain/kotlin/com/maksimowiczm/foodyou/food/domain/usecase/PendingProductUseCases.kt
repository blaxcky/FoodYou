package com.maksimowiczm.foodyou.food.domain.usecase

import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.database.TransactionProvider
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.PendingProduct
import com.maksimowiczm.foodyou.food.domain.repository.PendingProductPhotoStorage
import com.maksimowiczm.foodyou.food.domain.repository.PendingProductRepository
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class ObservePendingProductsUseCase(private val repository: PendingProductRepository) {
    fun observe(): Flow<List<PendingProduct>> = repository.observePendingProducts()
}

class ObservePendingProductUseCase(private val repository: PendingProductRepository) {
    fun observe(id: Long): Flow<PendingProduct?> = repository.observePendingProduct(id)
}

sealed interface CreatePendingProductResult {
    data class Created(val pendingProductId: Long) : CreatePendingProductResult

    data class ExistingPendingProduct(val pendingProductId: Long) : CreatePendingProductResult

    data class ExistingProduct(val productId: FoodId.Product) : CreatePendingProductResult
}

class CreatePendingProductUseCase(
    private val pendingProductRepository: PendingProductRepository,
    private val productRepository: ProductRepository,
    private val photoStorage: PendingProductPhotoStorage,
    private val transactionProvider: TransactionProvider,
    private val dateProvider: DateProvider,
) {
    suspend fun create(barcode: String?, photoPaths: List<String>): CreatePendingProductResult {
        val normalizedBarcode = barcode?.trim()?.takeIf { it.isNotEmpty() }
        return transactionProvider.withTransaction {
            if (normalizedBarcode != null) {
                val existingProduct =
                    productRepository.observeProductByBarcode(normalizedBarcode).first()
                if (existingProduct != null) {
                    photoPaths.forEach(photoStorage::delete)
                    return@withTransaction CreatePendingProductResult.ExistingProduct(
                        existingProduct.id
                    )
                }

                val existingPending =
                    pendingProductRepository.observePendingProductByBarcode(normalizedBarcode).first()
                if (existingPending != null) {
                    photoPaths.forEach(photoStorage::delete)
                    return@withTransaction CreatePendingProductResult.ExistingPendingProduct(
                        existingPending.id
                    )
                }
            }

            val id =
                pendingProductRepository.insertPendingProduct(
                    barcode = normalizedBarcode,
                    photoPaths = photoPaths,
                    createdAt = dateProvider.nowInstant(),
                )
            CreatePendingProductResult.Created(id)
        }
    }
}

class DeletePendingProductUseCase(
    private val repository: PendingProductRepository,
    private val photoStorage: PendingProductPhotoStorage,
    private val transactionProvider: TransactionProvider,
) {
    suspend fun delete(pendingProduct: PendingProduct) =
        transactionProvider.withTransaction {
            repository.deletePendingProduct(pendingProduct)
            pendingProduct.photoPaths.forEach(photoStorage::delete)
        }
}

class AddPendingProductPhotoUseCase(
    private val repository: PendingProductRepository,
    private val transactionProvider: TransactionProvider,
    private val dateProvider: DateProvider,
) {
    suspend fun addPhoto(pendingProduct: PendingProduct, photoPath: String) =
        transactionProvider.withTransaction {
            repository.updatePendingProduct(
                pendingProduct.copy(
                    photoPaths = pendingProduct.photoPaths + photoPath,
                    updatedAt = dateProvider.nowInstant(),
                )
            )
        }
}

class CompletePendingProductUseCase(
    private val repository: PendingProductRepository,
    private val photoStorage: PendingProductPhotoStorage,
    private val transactionProvider: TransactionProvider,
) {
    suspend fun complete(pendingProduct: PendingProduct) =
        transactionProvider.withTransaction {
            repository.deletePendingProduct(pendingProduct)
            pendingProduct.photoPaths.forEach(photoStorage::delete)
        }
}

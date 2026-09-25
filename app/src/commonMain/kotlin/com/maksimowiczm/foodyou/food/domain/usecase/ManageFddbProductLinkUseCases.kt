package com.maksimowiczm.foodyou.food.domain.usecase

import com.maksimowiczm.foodyou.common.domain.database.TransactionProvider
import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.common.result.Err
import com.maksimowiczm.foodyou.common.result.Ok
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.repository.FddbProductSyncStatusRepository
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository
import kotlinx.coroutines.flow.first

sealed interface UpdateFddbProductLinkError {
    data object InvalidUrl : UpdateFddbProductLinkError

    data class ProductNotFound(val id: FoodId.Product) : UpdateFddbProductLinkError

    data object NotFddbProduct : UpdateFddbProductLinkError

    data class AlreadyLinked(val productId: FoodId.Product) : UpdateFddbProductLinkError
}

class UpdateFddbProductLinkUseCase(
    private val productRepository: ProductRepository,
    private val transactionProvider: TransactionProvider,
) {
    suspend fun update(
        productId: FoodId.Product,
        input: String,
    ): Result<String, UpdateFddbProductLinkError> {
        val url = FddbLinkExtractor.normalizeSingleLink(input)
            ?: return Err(UpdateFddbProductLinkError.InvalidUrl)
        return transactionProvider.withTransaction {
            val product = productRepository.observeProduct(productId).first()
                ?: return@withTransaction Err(
                    UpdateFddbProductLinkError.ProductNotFound(productId)
                )
            if (product.source.type != FoodSource.Type.FDDB) {
                return@withTransaction Err(UpdateFddbProductLinkError.NotFddbProduct)
            }
            var linked = productRepository.getProductBySource(FoodSource.Type.FDDB, url)
            for (equivalentUrl in FddbLinkExtractor.equivalentLinks(url).drop(1)) {
                linked = linked ?: productRepository.getProductBySource(
                    FoodSource.Type.FDDB,
                    equivalentUrl,
                )
            }
            if (linked != null && linked.id != productId) {
                return@withTransaction Err(UpdateFddbProductLinkError.AlreadyLinked(linked.id))
            }
            productRepository.updateProduct(product.copy(source = product.source.copy(url = url)))
            Ok(url)
        }
    }
}

sealed interface UnlinkFddbProductError {
    data class ProductNotFound(val id: FoodId.Product) : UnlinkFddbProductError

    data object NotFddbProduct : UnlinkFddbProductError
}

class UnlinkFddbProductUseCase(
    private val productRepository: ProductRepository,
    private val statusRepository: FddbProductSyncStatusRepository,
    private val transactionProvider: TransactionProvider,
) {
    suspend fun unlink(productId: FoodId.Product): Result<Unit, UnlinkFddbProductError> =
        transactionProvider.withTransaction {
            val product = productRepository.observeProduct(productId).first()
                ?: return@withTransaction Err(UnlinkFddbProductError.ProductNotFound(productId))
            if (product.source.type != FoodSource.Type.FDDB) {
                return@withTransaction Err(UnlinkFddbProductError.NotFddbProduct)
            }
            productRepository.updateProduct(product.copy(source = product.source.copy(url = null)))
            statusRepository.clear(productId)
            Ok()
        }
}

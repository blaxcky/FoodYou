package com.maksimowiczm.foodyou.food.domain.usecase

import com.maksimowiczm.foodyou.common.domain.database.TransactionProvider
import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.common.log.logAndReturnFailure
import com.maksimowiczm.foodyou.common.result.Ok
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.repository.FddbAccessBlockedException
import com.maksimowiczm.foodyou.food.domain.repository.FddbHttpException
import com.maksimowiczm.foodyou.food.domain.repository.FddbParseException
import com.maksimowiczm.foodyou.food.domain.repository.FddbProductGateway
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

sealed interface ResyncFddbProductError {
    data class ProductNotFound(val id: FoodId.Product) : ResyncFddbProductError

    data object NotFddbProduct : ResyncFddbProductError

    data object MissingSourceUrl : ResyncFddbProductError

    data object Blocked : ResyncFddbProductError

    data class HttpFailed(val statusCode: Int) : ResyncFddbProductError

    data class ParseFailed(val detail: String) : ResyncFddbProductError

    data class NetworkFailed(val detail: String) : ResyncFddbProductError
}

class ResyncFddbProductUseCase(
    private val productRepository: ProductRepository,
    private val fddbProductGateway: FddbProductGateway,
    private val transactionProvider: TransactionProvider,
    private val logger: Logger,
) {
    suspend fun resync(id: FoodId.Product): Result<Unit, ResyncFddbProductError> {
        val product = productRepository.observeProduct(id).first()
        if (product == null) {
            return logger.logAndReturnFailure(
                tag = TAG,
                error = ResyncFddbProductError.ProductNotFound(id),
                message = { "Product with ID $id not found." },
            )
        }

        if (product.source.type != FoodSource.Type.FDDB) {
            return logger.logAndReturnFailure(
                tag = TAG,
                error = ResyncFddbProductError.NotFddbProduct,
                message = { "Product with ID $id is not an FDDB product." },
            )
        }

        val sourceUrl = product.source.url
        if (sourceUrl.isNullOrBlank()) {
            return logger.logAndReturnFailure(
                tag = TAG,
                error = ResyncFddbProductError.MissingSourceUrl,
                message = { "FDDB product with ID $id has no source URL." },
            )
        }

        val fddbProduct =
            try {
                fddbProductGateway.getProduct(sourceUrl)
            } catch (exception: FddbAccessBlockedException) {
                return logger.logAndReturnFailure(
                    tag = TAG,
                    error = ResyncFddbProductError.Blocked,
                    throwable = exception,
                    message = { "FDDB blocked product resync for $sourceUrl." },
                )
            } catch (exception: FddbHttpException) {
                return logger.logAndReturnFailure(
                    tag = TAG,
                    error = ResyncFddbProductError.HttpFailed(exception.statusCode),
                    throwable = exception,
                    message = { "FDDB returned HTTP ${exception.statusCode} for $sourceUrl." },
                )
            } catch (exception: FddbParseException) {
                return logger.logAndReturnFailure(
                    tag = TAG,
                    error = ResyncFddbProductError.ParseFailed(exception.safeDetail()),
                    throwable = exception,
                    message = { "Failed to parse FDDB product from $sourceUrl." },
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                return logger.logAndReturnFailure(
                    tag = TAG,
                    error = ResyncFddbProductError.NetworkFailed(throwable.safeDetail()),
                    throwable = throwable,
                    message = { "Failed to resync FDDB product from $sourceUrl." },
                )
            }

        return transactionProvider.withTransaction {
            val currentProduct = productRepository.observeProduct(id).first()
            if (currentProduct == null) {
                return@withTransaction logger.logAndReturnFailure(
                    tag = TAG,
                    error = ResyncFddbProductError.ProductNotFound(id),
                    message = { "Product with ID $id not found during resync transaction." },
                )
            }

            productRepository.updateProduct(
                currentProduct.copy(
                    nutritionFacts = fddbProduct.nutritionFacts,
                    packageWeight = fddbProduct.packageWeight,
                    servingWeight = fddbProduct.servingWeight,
                    isLiquid = fddbProduct.isLiquid,
                )
            )
            productRepository.replaceProductPortions(
                productId = id,
                sourceType = FoodSource.Type.FDDB,
                portions = fddbProduct.portions,
            )

            Ok(Unit)
        }
    }

    private companion object {
        const val TAG = "ResyncFddbProductUseCase"
    }
}

private fun Throwable.safeDetail(): String =
    (message ?: "Unknown error").replace(Regex("\\s+"), " ").trim().take(240)

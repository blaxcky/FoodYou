package com.maksimowiczm.foodyou.food.domain.usecase

import com.maksimowiczm.foodyou.common.domain.database.TransactionProvider
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.food.domain.entity.FoodHistory
import com.maksimowiczm.foodyou.food.domain.repository.FddbProductGateway
import com.maksimowiczm.foodyou.food.domain.repository.FoodHistoryRepository
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class ImportFddbProductsUseCase(
    private val fddbProductGateway: FddbProductGateway,
    private val productRepository: ProductRepository,
    private val historyRepository: FoodHistoryRepository,
    private val transactionProvider: TransactionProvider,
    private val dateProvider: DateProvider,
    private val requestDelayMillis: Long = 1_000,
) {
    fun extractLinks(text: String): List<String> =
        FddbUrlRegex.findAll(text)
            .map { it.value.trimEnd('.', ',', ';', ')', ']') }
            .distinct()
            .toList()

    fun import(text: String): Flow<FddbImportProgress> = flow {
        val links = extractLinks(text)
        val results = mutableListOf<FddbImportResult>()
        emit(FddbImportProgress(total = links.size, completed = 0, results = emptyList()))

        links.forEachIndexed { index, link ->
            val result =
                try {
                    importLink(link)
                } catch (throwable: Throwable) {
                    FddbImportResult.Failed(link = link, message = throwable.message)
                }

            results += result
            emit(
                FddbImportProgress(
                    total = links.size,
                    completed = index + 1,
                    results = results.toList(),
                )
            )

            if (index != links.lastIndex && requestDelayMillis > 0) {
                delay(requestDelayMillis)
            }
        }
    }

    private suspend fun importLink(link: String): FddbImportResult {
        val product = fddbProductGateway.getProduct(link)

        return transactionProvider.withTransaction {
            if (
                product.barcode != null &&
                    productRepository.getProductByBarcode(product.barcode) != null
            ) {
                return@withTransaction FddbImportResult.Skipped(
                    link = link,
                    name = product.name,
                    reason = FddbSkipReason.BarcodeExists,
                )
            }

            val id =
                productRepository.insertUniqueProduct(
                    name = product.name,
                    brand = product.brand,
                    barcode = product.barcode,
                    note = null,
                    isLiquid = product.isLiquid,
                    packageWeight = product.packageWeight,
                    servingWeight = product.servingWeight,
                    source = FoodSource(type = FoodSource.Type.FDDB, url = link),
                    nutritionFacts = product.nutritionFacts,
                )

            if (id == null) {
                FddbImportResult.Skipped(
                    link = link,
                    name = product.name,
                    reason = FddbSkipReason.ProductExists,
                )
            } else {
                historyRepository.insert(
                    id,
                    FoodHistory.Imported(timestamp = dateProvider.nowInstant()),
                )
                FddbImportResult.Imported(link = link, name = product.name)
            }
        }
    }

    private companion object {
        val FddbUrlRegex =
            Regex(
                """https?://(?:www\.)?fddb\.info/db/(?:de/lebensmittel|en/food)/[^\s<>"']+/index\.html"""
            )
    }
}

data class FddbImportProgress(
    val total: Int,
    val completed: Int,
    val results: List<FddbImportResult>,
) {
    val imported: Int = results.count { it is FddbImportResult.Imported }
    val skipped: Int = results.count { it is FddbImportResult.Skipped }
    val failed: Int = results.count { it is FddbImportResult.Failed }
    val isFinished: Boolean = completed == total
}

sealed interface FddbImportResult {
    val link: String

    data class Imported(override val link: String, val name: String) : FddbImportResult

    data class Skipped(
        override val link: String,
        val name: String,
        val reason: FddbSkipReason,
    ) : FddbImportResult

    data class Failed(override val link: String, val message: String?) : FddbImportResult
}

enum class FddbSkipReason {
    BarcodeExists,
    ProductExists,
}

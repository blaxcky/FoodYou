package com.maksimowiczm.foodyou.food.domain.usecase

import com.maksimowiczm.foodyou.common.domain.database.TransactionProvider
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.food.domain.entity.FddbImportQueueItem
import com.maksimowiczm.foodyou.food.domain.entity.FoodHistory
import com.maksimowiczm.foodyou.food.domain.repository.FddbAccessBlockedException
import com.maksimowiczm.foodyou.food.domain.repository.FddbImportQueueRepository
import com.maksimowiczm.foodyou.food.domain.repository.FddbProductGateway
import com.maksimowiczm.foodyou.food.domain.repository.FoodHistoryRepository
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class ImportFddbProductsUseCase(
    private val fddbProductGateway: FddbProductGateway,
    private val queueRepository: FddbImportQueueRepository,
    private val productRepository: ProductRepository,
    private val historyRepository: FoodHistoryRepository,
    private val transactionProvider: TransactionProvider,
    private val dateProvider: DateProvider,
    private val requestDelayMillis: Long = 3_000,
    private val defaultBlockedCooldownMillis: Long = 60_000,
) {
    fun extractLinks(text: String): List<String> = FddbLinkExtractor.extractLinks(text)

    fun importQueue(): Flow<FddbImportProgress> = flow {
        val queue = queueRepository.getQueue()
        val results = mutableListOf<FddbImportResult>()
        emit(FddbImportProgress(total = queue.size, completed = 0, results = emptyList()))

        for ((index, item) in queue.withIndex()) {
            val result = importQueueItem(item)

            results += result
            emit(
                FddbImportProgress(
                    total = queue.size,
                    completed = index + 1,
                    results = results.toList(),
                )
            )

            if (result is FddbImportResult.Failed && result.stopImport) {
                emit(
                    FddbImportProgress(
                        total = queue.size,
                        completed = index + 1,
                        results = results.toList(),
                        stopped = true,
                    )
                )
                return@flow
            }

            if (index != queue.lastIndex && requestDelayMillis > 0) {
                delay(requestDelayMillis)
            }
        }
    }

    fun import(text: String): Flow<FddbImportProgress> = flow {
        val now = dateProvider.nowInstant()
        extractLinks(text).forEach { queueRepository.add(it, now) }
        importQueue().collect { emit(it) }
    }

    private suspend fun importQueueItem(item: FddbImportQueueItem): FddbImportResult {
        queueRepository.markAttempt(item.id, dateProvider.nowInstant(), null)
        return when (val result = runImport(item.url)) {
            is FddbImportResult.Imported,
            is FddbImportResult.Skipped -> {
                queueRepository.delete(item.id)
                result
            }
            is FddbImportResult.Failed -> {
                if (result.blocked) {
                    val retryAfterMillis =
                        result.retryAfterMillis ?: defaultBlockedCooldownMillis
                    if (retryAfterMillis > 0) {
                        delay(retryAfterMillis)
                    }
                    val retryResult = runImport(item.url)
                    if (retryResult is FddbImportResult.Imported || retryResult is FddbImportResult.Skipped) {
                        queueRepository.delete(item.id)
                        retryResult
                    } else {
                        val failed = retryResult.asFailed(item.url).copy(stopImport = true)
                        queueRepository.markAttempt(
                            item.id,
                            dateProvider.nowInstant(),
                            failed.message,
                        )
                        failed
                    }
                } else {
                    queueRepository.markAttempt(item.id, dateProvider.nowInstant(), result.message)
                    result
                }
            }
        }
    }

    private suspend fun runImport(link: String): FddbImportResult =
        try {
            importLink(link)
        } catch (exception: FddbAccessBlockedException) {
            FddbImportResult.Failed(
                link = link,
                message = exception.message,
                blocked = true,
                retryAfterMillis = exception.retryAfterMillis,
            )
        } catch (throwable: Throwable) {
            FddbImportResult.Failed(link = link, message = throwable.message)
        }

    private suspend fun importLink(link: String): FddbImportResult {
        val product = fddbProductGateway.getProduct(link)

        return transactionProvider.withTransaction {
            when (val result = productRepository.upsertFddbProduct(link, product)) {
                is FddbProductUpsertResult.Updated -> {
                    val reason =
                        if (result.changed) {
                            FddbSkipReason.UpdatedWeights
                        } else {
                            FddbSkipReason.BarcodeExists
                        }
                    FddbImportResult.Skipped(
                        link = link,
                        name = product.name,
                        reason = reason,
                    )
                }
                is FddbProductUpsertResult.Inserted -> {
                    historyRepository.insert(
                        result.product.id,
                        FoodHistory.Imported(timestamp = dateProvider.nowInstant()),
                    )
                    FddbImportResult.Imported(link = link, name = product.name)
                }
            }
        }
    }

}

data class FddbImportProgress(
    val total: Int,
    val completed: Int,
    val results: List<FddbImportResult>,
    val stopped: Boolean = false,
) {
    val imported: Int = results.count { it is FddbImportResult.Imported }
    val skipped: Int = results.count { it is FddbImportResult.Skipped }
    val failed: Int = results.count { it is FddbImportResult.Failed }
    val isFinished: Boolean = completed == total || stopped
}

sealed interface FddbImportResult {
    val link: String

    data class Imported(override val link: String, val name: String) : FddbImportResult

    data class Skipped(
        override val link: String,
        val name: String,
        val reason: FddbSkipReason,
    ) : FddbImportResult

    data class Failed(
        override val link: String,
        val message: String?,
        val blocked: Boolean = false,
        val retryAfterMillis: Long? = null,
        val stopImport: Boolean = false,
    ) : FddbImportResult
}

private fun FddbImportResult.asFailed(link: String): FddbImportResult.Failed =
    this as? FddbImportResult.Failed ?: FddbImportResult.Failed(link = link, message = null)

enum class FddbSkipReason {
    BarcodeExists,
    ProductExists,
    UpdatedWeights,
}

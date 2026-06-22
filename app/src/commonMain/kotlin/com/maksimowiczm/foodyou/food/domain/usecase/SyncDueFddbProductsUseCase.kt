package com.maksimowiczm.foodyou.food.domain.usecase

import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.food.domain.repository.FddbProductSyncStatusRepository

class SyncDueFddbProductsUseCase(
    private val statusRepository: FddbProductSyncStatusRepository,
    private val resyncFddbProductUseCase: ResyncFddbProductUseCase,
    private val dateProvider: DateProvider,
) {
    suspend fun sync(limit: Int = 2): SyncDueFddbProductsResult {
        var synced = 0
        var failed = 0
        var blocked = false

        for (item in statusRepository.getDueProducts(limit)) {
            val attemptedAt = dateProvider.nowInstant()
            when (val result = resyncFddbProductUseCase.resync(item.productId)) {
                is Result.Success -> {
                    statusRepository.markSuccess(item.productId, attemptedAt)
                    synced += 1
                }

                is Result.Error -> {
                    statusRepository.markFailure(
                        productId = item.productId,
                        attemptedAt = attemptedAt,
                        error = result.error.toStatusMessage(),
                    )
                    failed += 1
                    if (result.error == ResyncFddbProductError.Blocked) {
                        blocked = true
                        break
                    }
                }
            }
        }

        return SyncDueFddbProductsResult(synced = synced, failed = failed, blocked = blocked)
    }
}

data class SyncDueFddbProductsResult(val synced: Int, val failed: Int, val blocked: Boolean)

private fun ResyncFddbProductError.toStatusMessage(): String =
    when (this) {
        is ResyncFddbProductError.ProductNotFound -> "Product not found"
        ResyncFddbProductError.NotFddbProduct -> "Not an FDDB product"
        ResyncFddbProductError.MissingSourceUrl -> "Missing FDDB source URL"
        ResyncFddbProductError.Blocked -> "FDDB access blocked"
        ResyncFddbProductError.NetworkOrParseFailed -> "Network or parse failed"
    }

package com.maksimowiczm.foodyou.food.domain.usecase

import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.food.domain.entity.FddbProductSyncQueueItem
import com.maksimowiczm.foodyou.food.domain.repository.FddbProductSyncStatusRepository

class SyncDueFddbProductsUseCase(
    private val statusRepository: FddbProductSyncStatusRepository,
    private val syncFddbProductUseCase: SyncFddbProductUseCase,
) {
    suspend fun sync(limit: Int = 2): SyncDueFddbProductsResult {
        return sync(statusRepository.getDueProducts(limit))
    }

    suspend fun sync(
        products: List<FddbProductSyncQueueItem>,
        onProgress: (FddbProductSyncBatchProgress) -> Unit = {},
    ): SyncDueFddbProductsResult {
        var synced = 0
        var failed = 0
        var blocked = false
        onProgress(
            FddbProductSyncBatchProgress(
                total = products.size,
                processed = 0,
                synced = 0,
                failed = 0,
                blocked = false,
            )
        )

        for (item in products) {
            when (val result = syncFddbProductUseCase.sync(item.productId)) {
                is Result.Success -> {
                    synced += 1
                }

                is Result.Error -> {
                    failed += 1
                    if (result.error == ResyncFddbProductError.Blocked) {
                        blocked = true
                    }
                }
            }
            onProgress(
                FddbProductSyncBatchProgress(
                    total = products.size,
                    processed = synced + failed,
                    synced = synced,
                    failed = failed,
                    blocked = blocked,
                )
            )
            if (blocked) break
        }

        return SyncDueFddbProductsResult(synced = synced, failed = failed, blocked = blocked)
    }
}

data class SyncDueFddbProductsResult(val synced: Int, val failed: Int, val blocked: Boolean)

data class FddbProductSyncBatchProgress(
    val total: Int,
    val processed: Int,
    val synced: Int,
    val failed: Int,
    val blocked: Boolean,
)

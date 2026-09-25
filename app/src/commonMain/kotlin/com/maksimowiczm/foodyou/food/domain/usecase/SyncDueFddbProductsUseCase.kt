package com.maksimowiczm.foodyou.food.domain.usecase

import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.food.domain.repository.FddbProductSyncStatusRepository

class SyncDueFddbProductsUseCase(
    private val statusRepository: FddbProductSyncStatusRepository,
    private val syncFddbProductUseCase: SyncFddbProductUseCase,
) {
    suspend fun sync(limit: Int = 2): SyncDueFddbProductsResult {
        var synced = 0
        var failed = 0
        var blocked = false

        for (item in statusRepository.getDueProducts(limit)) {
            when (val result = syncFddbProductUseCase.sync(item.productId)) {
                is Result.Success -> {
                    synced += 1
                }

                is Result.Error -> {
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

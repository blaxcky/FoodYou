package com.maksimowiczm.foodyou.food.domain.usecase

import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.repository.FddbProductSyncStatusRepository
import com.maksimowiczm.foodyou.settings.domain.entity.FddbProductSyncManualFrequency
import com.maksimowiczm.foodyou.settings.domain.entity.FddbProductSyncMode
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FddbProductSyncCoordinator(
    private val settingsRepository: UserPreferencesRepository<Settings>,
    private val statusRepository: FddbProductSyncStatusRepository,
    private val syncDueFddbProductsUseCase: SyncDueFddbProductsUseCase,
    private val syncFddbProductUseCase: SyncFddbProductUseCase,
) {
    private val mutex = Mutex()

    suspend fun onManualFddbSyncCompleted(): ManualFddbProductSyncResult =
        mutex.withLock {
            val settings = settingsRepository.observe().first()
            if (settings.fddbProductSyncMode != FddbProductSyncMode.WithManualFddbSync) {
                return@withLock ManualFddbProductSyncResult.NotEnabled
            }

            when (settings.fddbProductSyncManualFrequency) {
                FddbProductSyncManualFrequency.EverySync -> syncNextLocked(MANUAL_SYNC_LIMIT)
                FddbProductSyncManualFrequency.EveryThirdSync -> {
                    val count = settings.fddbProductSyncManualTriggerCount + 1
                    if (count < MANUAL_SYNCS_PER_PRODUCT_SYNC) {
                        settingsRepository.update {
                            copy(fddbProductSyncManualTriggerCount = count)
                        }
                        ManualFddbProductSyncResult.Waiting(count)
                    } else {
                        settingsRepository.update {
                            copy(fddbProductSyncManualTriggerCount = 0)
                        }
                        syncNextLocked(MANUAL_SYNC_LIMIT)
                    }
                }
            }
        }

    suspend fun syncNext(
        limit: Int,
        onProgress: (FddbProductSyncBatchProgress) -> Unit = {},
    ): ManualFddbProductSyncResult = mutex.withLock {
        require(limit > 0) { "The FDDB product sync limit must be positive." }
        syncNextLocked(limit, onProgress)
    }

    suspend fun syncNow(
        productId: FoodId.Product
    ): Result<Unit, ResyncFddbProductError> = mutex.withLock {
        syncFddbProductUseCase.sync(productId)
    }

    companion object {
        const val MANUAL_SYNC_LIMIT = 2
        const val MANUAL_SYNCS_PER_PRODUCT_SYNC = 3
    }

    private suspend fun syncNextLocked(
        limit: Int,
        onProgress: (FddbProductSyncBatchProgress) -> Unit = {},
    ): ManualFddbProductSyncResult {
        val products = statusRepository.getDueProducts(limit)
        if (products.isEmpty()) {
            onProgress(FddbProductSyncBatchProgress(0, 0, 0, 0, false))
            return ManualFddbProductSyncResult.NoProducts
        }
        return ManualFddbProductSyncResult.Completed(
            syncDueFddbProductsUseCase.sync(products, onProgress)
        )
    }
}

sealed interface ManualFddbProductSyncResult {
    data object NotEnabled : ManualFddbProductSyncResult

    data class Waiting(val completedSyncs: Int) : ManualFddbProductSyncResult

    data object NoProducts : ManualFddbProductSyncResult

    data class Completed(val result: SyncDueFddbProductsResult) : ManualFddbProductSyncResult
}

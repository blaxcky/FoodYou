package com.maksimowiczm.foodyou.food.domain.usecase

import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.repository.FddbProductSyncStatusRepository
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
import com.maksimowiczm.foodyou.settings.domain.entity.FddbProductSyncManualFrequency
import com.maksimowiczm.foodyou.settings.domain.entity.FddbProductSyncMode
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FddbProductSyncCoordinator(
    private val settingsRepository: UserPreferencesRepository<Settings>,
    private val statusRepository: FddbProductSyncStatusRepository,
    private val syncDueFddbProductsUseCase: SyncDueFddbProductsUseCase,
    private val syncFddbProductUseCase: SyncFddbProductUseCase,
    private val dateProvider: DateProvider,
) {
    private val mutex = Mutex()

    suspend fun syncDueIfAllowed(): AutomaticFddbProductSyncResult =
        mutex.withLock {
            val now = dateProvider.nowInstant()
            val settings = settingsRepository.observe().first()
            if (settings.fddbProductSyncMode != FddbProductSyncMode.EveryThirtyMinutes) {
                return@withLock AutomaticFddbProductSyncResult.NotEnabled
            }
            val lastAttempt = settings.fddbProductSyncLastAttemptEpochSeconds
            if (
                lastAttempt != null &&
                    now.epochSeconds - lastAttempt < AUTOMATIC_SYNC_INTERVAL.inWholeSeconds
            ) {
                return@withLock AutomaticFddbProductSyncResult.NotDue
            }

            val products = statusRepository.getDueProducts(AUTOMATIC_SYNC_LIMIT)
            if (products.isEmpty()) {
                return@withLock AutomaticFddbProductSyncResult.NoProducts
            }

            AutomaticFddbProductSyncResult.Completed(
                syncDueFddbProductsUseCase.sync(products)
            )
        }

    suspend fun onManualFddbSyncCompleted(): ManualFddbProductSyncResult =
        mutex.withLock {
            val settings = settingsRepository.observe().first()
            if (settings.fddbProductSyncMode != FddbProductSyncMode.WithManualFddbSync) {
                return@withLock ManualFddbProductSyncResult.NotEnabled
            }

            when (settings.fddbProductSyncManualFrequency) {
                FddbProductSyncManualFrequency.EverySync -> syncNextLocked(AUTOMATIC_SYNC_LIMIT)
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
                        syncNextLocked(AUTOMATIC_SYNC_LIMIT)
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
        val AUTOMATIC_SYNC_INTERVAL = 30.minutes
        const val AUTOMATIC_SYNC_LIMIT = 2
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

sealed interface AutomaticFddbProductSyncResult {
    data object NotEnabled : AutomaticFddbProductSyncResult

    data object NotDue : AutomaticFddbProductSyncResult

    data object NoProducts : AutomaticFddbProductSyncResult

    data class Completed(val result: SyncDueFddbProductsResult) :
        AutomaticFddbProductSyncResult
}

sealed interface ManualFddbProductSyncResult {
    data object NotEnabled : ManualFddbProductSyncResult

    data class Waiting(val completedSyncs: Int) : ManualFddbProductSyncResult

    data object NoProducts : ManualFddbProductSyncResult

    data class Completed(val result: SyncDueFddbProductsResult) : ManualFddbProductSyncResult
}

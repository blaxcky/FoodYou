package com.maksimowiczm.foodyou.food.domain.usecase

import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.repository.FddbProductSyncStatusRepository
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
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
            val lastAttempt =
                settingsRepository.observe().first().fddbProductSyncLastAttemptEpochSeconds
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

    suspend fun syncNow(
        productId: FoodId.Product
    ): Result<Unit, ResyncFddbProductError> = mutex.withLock {
        syncFddbProductUseCase.sync(productId)
    }

    companion object {
        val AUTOMATIC_SYNC_INTERVAL = 30.minutes
        const val AUTOMATIC_SYNC_LIMIT = 2
    }
}

sealed interface AutomaticFddbProductSyncResult {
    data object NotDue : AutomaticFddbProductSyncResult

    data object NoProducts : AutomaticFddbProductSyncResult

    data class Completed(val result: SyncDueFddbProductsResult) :
        AutomaticFddbProductSyncResult
}

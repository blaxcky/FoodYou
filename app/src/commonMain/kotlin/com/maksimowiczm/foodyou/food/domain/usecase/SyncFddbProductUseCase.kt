package com.maksimowiczm.foodyou.food.domain.usecase

import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.repository.FddbProductSyncStatusRepository
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
import com.maksimowiczm.foodyou.sync.SyncLog
import com.maksimowiczm.foodyou.sync.SyncLogOutcome
import com.maksimowiczm.foodyou.sync.recordSyncStep

class SyncFddbProductUseCase(
    private val statusRepository: FddbProductSyncStatusRepository,
    private val resyncFddbProductUseCase: ResyncFddbProductUseCase,
    private val dateProvider: DateProvider,
    private val settingsRepository: UserPreferencesRepository<Settings>,
    private val syncLog: SyncLog? = null,
) {
    suspend fun sync(
        productId: FoodId.Product
    ): Result<Unit, ResyncFddbProductError> = syncLog.recordSyncStep("FDDB-Produkt aktualisieren · ${productId.id}", { result ->
        when (result) {
            is Result.Success -> SyncLogOutcome.success("Produkt aktualisiert")
            is Result.Error -> SyncLogOutcome.failed(if (result.error == ResyncFddbProductError.Blocked)
                "FDDB-Zugriff blockiert" else "Produktabgleich fehlgeschlagen")
        }
    }) { syncProduct(productId) }

    private suspend fun syncProduct(productId: FoodId.Product): Result<Unit, ResyncFddbProductError> {
        val attemptedAt = dateProvider.nowInstant()
        settingsRepository.update {
            copy(fddbProductSyncLastAttemptEpochSeconds = attemptedAt.epochSeconds)
        }
        statusRepository.markAttempt(productId, attemptedAt)

        return when (val result = resyncFddbProductUseCase.resync(productId)) {
            is Result.Success -> {
                statusRepository.markSuccess(productId, attemptedAt)
                result
            }

            is Result.Error -> {
                statusRepository.markFailure(
                    productId = productId,
                    attemptedAt = attemptedAt,
                    error = result.error.toStatusMessage(),
                )
                result
            }
        }
    }
}

internal fun ResyncFddbProductError.toStatusMessage(): String =
    when (this) {
        is ResyncFddbProductError.ProductNotFound -> "Product not found"
        ResyncFddbProductError.NotFddbProduct -> "Not an FDDB product"
        ResyncFddbProductError.MissingSourceUrl -> "Missing FDDB source URL"
        ResyncFddbProductError.Blocked -> "FDDB access blocked"
        is ResyncFddbProductError.HttpFailed ->
            if (statusCode == 404) "FDDB page not found (HTTP 404)"
            else "FDDB request failed (HTTP $statusCode)"
        is ResyncFddbProductError.ParseFailed -> "FDDB page could not be parsed: $detail"
        is ResyncFddbProductError.NetworkFailed -> "Network request failed: $detail"
    }

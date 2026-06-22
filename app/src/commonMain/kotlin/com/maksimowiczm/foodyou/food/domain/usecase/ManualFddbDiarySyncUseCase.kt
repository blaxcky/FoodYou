package com.maksimowiczm.foodyou.food.domain.usecase

import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.common.result.Err
import com.maksimowiczm.foodyou.common.result.Ok
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
import kotlin.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate

class ManualFddbDiarySyncUseCase(
    private val settingsRepository: UserPreferencesRepository<Settings>,
    private val diarySyncUseCase: FddbDiarySyncUseCase,
    private val syncDueFddbProductsUseCase: SyncDueFddbProductsUseCase,
) {
    suspend fun hasCredentials(): Boolean = diarySyncUseCase.hasCredentials()

    suspend fun sync(referenceDate: LocalDate): Result<FddbDiarySyncResult, Throwable> {
        val countAfterStart =
            settingsRepository.observe().first().fddbProductSyncManualCount + 1
        settingsRepository.update {
            copy(fddbProductSyncManualCount = countAfterStart)
        }

        val diaryResult =
            try {
                diarySyncUseCase.sync(referenceDate)
            } catch (throwable: Throwable) {
                settingsRepository.recordFddbDiarySyncFailure(throwable)
                return Err(throwable)
            }

        settingsRepository.recordFddbDiarySyncResult(diaryResult)

        if (countAfterStart >= MANUAL_SYNCS_PER_PRODUCT_SYNC) {
            syncDueFddbProductsUseCase.sync(limit = PRODUCT_SYNC_LIMIT)
            settingsRepository.update { copy(fddbProductSyncManualCount = 0) }
        }

        return Ok(diaryResult)
    }

    private companion object {
        const val MANUAL_SYNCS_PER_PRODUCT_SYNC = 3
        const val PRODUCT_SYNC_LIMIT = 2
    }
}

suspend fun UserPreferencesRepository<Settings>.recordFddbDiarySyncResult(
    result: FddbDiarySyncResult
) {
    val now = Clock.System.now().epochSeconds
    update {
        copy(
            fddbDiarySyncLastImported = result.imported,
            fddbDiarySyncLastSkipped = result.skipped,
            fddbDiarySyncLastFailed = result.failed,
            fddbDiarySyncLastErrorMessage = result.errorMessage,
            fddbDiarySyncLastAttemptEpochSeconds = now,
        )
    }
}

suspend fun UserPreferencesRepository<Settings>.recordFddbDiarySyncFailure(throwable: Throwable) {
    val now = Clock.System.now().epochSeconds
    update {
        copy(
            fddbDiarySyncLastImported = 0,
            fddbDiarySyncLastSkipped = 0,
            fddbDiarySyncLastFailed = 1,
            fddbDiarySyncLastErrorMessage = throwable.stackTraceToString(),
            fddbDiarySyncLastAttemptEpochSeconds = now,
        )
    }
}

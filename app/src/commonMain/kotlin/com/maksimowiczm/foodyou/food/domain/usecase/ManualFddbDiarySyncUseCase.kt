package com.maksimowiczm.foodyou.food.domain.usecase

import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.common.result.Err
import com.maksimowiczm.foodyou.common.result.Ok
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
import com.maksimowiczm.foodyou.sync.SyncLog
import com.maksimowiczm.foodyou.sync.SyncLogOutcome
import com.maksimowiczm.foodyou.sync.logOutcome
import com.maksimowiczm.foodyou.sync.recordSyncStep
import kotlin.time.Clock
import kotlinx.datetime.LocalDate

class ManualFddbDiarySyncUseCase(
    private val settingsRepository: UserPreferencesRepository<Settings>,
    private val diarySyncUseCase: FddbDiarySyncUseCase,
    private val fddbProductSyncCoordinator: FddbProductSyncCoordinator,
    private val syncLog: SyncLog? = null,
) {
    suspend fun hasCredentials(): Boolean = diarySyncUseCase.hasCredentials()

    suspend fun sync(referenceDate: LocalDate): Result<FddbDiarySyncResult, Throwable> =
        syncLog.recordSyncStep("FDDB-Tagebuch und Produktnachsync · $referenceDate", { result ->
            when (result) {
                is Result.Error -> SyncLogOutcome.failed("Tagebuchabgleich fehlgeschlagen")
                is Result.Success -> if (result.data.failed > 0) SyncLogOutcome.failed("${result.data.failed} Tagebuchfehler")
                    else SyncLogOutcome.success()
            }
        }) { syncDiaryAndProducts(referenceDate) }

    private suspend fun syncDiaryAndProducts(referenceDate: LocalDate): Result<FddbDiarySyncResult, Throwable> {
        val diaryResult =
            try {
                diarySyncUseCase.sync(referenceDate)
            } catch (throwable: Throwable) {
                if (throwable is kotlinx.coroutines.CancellationException) throw throwable
                settingsRepository.recordFddbDiarySyncFailure(throwable)
                return Err(throwable)
            }

        syncLog.recordSyncStep("FDDB-Tagebuchstatus speichern") {
            settingsRepository.recordFddbDiarySyncResult(diaryResult)
        }
        syncLog.recordSyncStep("FDDB-Produktnachsync", ManualFddbProductSyncResult::logOutcome) {
            fddbProductSyncCoordinator.onManualFddbSyncCompleted()
        }

        return Ok(diaryResult)
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

package com.maksimowiczm.foodyou.food.domain.usecase

import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.common.result.Err
import com.maksimowiczm.foodyou.common.result.Ok
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
import kotlin.time.Clock
import kotlinx.datetime.LocalDate

class ManualFddbDiarySyncUseCase(
    private val settingsRepository: UserPreferencesRepository<Settings>,
    private val diarySyncUseCase: FddbDiarySyncUseCase,
) {
    suspend fun hasCredentials(): Boolean = diarySyncUseCase.hasCredentials()

    suspend fun sync(referenceDate: LocalDate): Result<FddbDiarySyncResult, Throwable> {
        val diaryResult =
            try {
                diarySyncUseCase.sync(referenceDate)
            } catch (throwable: Throwable) {
                settingsRepository.recordFddbDiarySyncFailure(throwable)
                return Err(throwable)
            }

        settingsRepository.recordFddbDiarySyncResult(diaryResult)

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

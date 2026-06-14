package com.maksimowiczm.foodyou.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.app.widget.updateCalorieWidgetValues
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.food.domain.repository.FddbCredentialsRepository
import com.maksimowiczm.foodyou.food.domain.usecase.FddbDiarySyncResult
import com.maksimowiczm.foodyou.food.domain.usecase.FddbDiarySyncUseCase
import com.maksimowiczm.foodyou.settings.domain.entity.FddbDiarySyncStatus
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
import com.maksimowiczm.foodyou.settings.domain.entity.fddbDiarySyncStatus
import kotlin.time.Clock
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

internal class SynchronizationSettingsViewModel(
    private val settingsRepository: UserPreferencesRepository<Settings>,
    private val fddbDiarySyncUseCase: FddbDiarySyncUseCase,
    fddbCredentialsRepository: FddbCredentialsRepository,
) : ViewModel() {

    private val fddbSyncInProgress = MutableStateFlow(false)

    val model: StateFlow<SynchronizationSettingsModel?> =
        combine(
                settingsRepository.observe(),
                fddbCredentialsRepository.hasCredentials(),
                fddbSyncInProgress,
            ) { settings, hasFddbCredentials, fddbSyncing ->
                SynchronizationSettingsModel(
                    homeSyncHealthConnectEnabled = settings.homeSyncHealthConnectEnabled,
                    homeSyncFddbDiaryEnabled = settings.homeSyncFddbDiaryEnabled,
                    fddbDiarySyncStatus = settings.fddbDiarySyncStatus(),
                    hasFddbCredentials = hasFddbCredentials,
                    fddbSyncInProgress = fddbSyncing,
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(2_000),
                initialValue = null,
            )

    fun setHomeSyncHealthConnectEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.update { copy(homeSyncHealthConnectEnabled = enabled) }
        }
    }

    fun setHomeSyncFddbDiaryEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.update { copy(homeSyncFddbDiaryEnabled = enabled) }
        }
    }

    fun syncFddbDiary() {
        if (fddbSyncInProgress.value) return

        viewModelScope.launch {
            if (!fddbDiarySyncUseCase.hasCredentials()) {
                return@launch
            }
            fddbSyncInProgress.value = true
            try {
                val date = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
                val result = fddbDiarySyncUseCase.sync(date)
                settingsRepository.recordFddbDiarySyncResult(result)
                updateCalorieWidgetValues()
            } catch (throwable: Throwable) {
                settingsRepository.recordFddbDiarySyncFailure(throwable)
            } finally {
                fddbSyncInProgress.value = false
            }
        }
    }
}

internal data class SynchronizationSettingsModel(
    val homeSyncHealthConnectEnabled: Boolean,
    val homeSyncFddbDiaryEnabled: Boolean,
    val fddbDiarySyncStatus: FddbDiarySyncStatus?,
    val hasFddbCredentials: Boolean,
    val fddbSyncInProgress: Boolean,
)

private suspend fun UserPreferencesRepository<Settings>.recordFddbDiarySyncResult(
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

private suspend fun UserPreferencesRepository<Settings>.recordFddbDiarySyncFailure(
    throwable: Throwable
) {
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

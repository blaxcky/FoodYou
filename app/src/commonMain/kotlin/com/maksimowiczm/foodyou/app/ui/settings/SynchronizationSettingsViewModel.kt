package com.maksimowiczm.foodyou.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.app.widget.updateCalorieWidgetValues
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.food.domain.repository.FddbCredentialsRepository
import com.maksimowiczm.foodyou.food.domain.usecase.ManualFddbDiarySyncUseCase
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
    private val manualFddbDiarySyncUseCase: ManualFddbDiarySyncUseCase,
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
                    fddbProductSyncProgress = settings.fddbProductSyncManualCount.coerceIn(0, 2),
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
            if (!manualFddbDiarySyncUseCase.hasCredentials()) {
                return@launch
            }
            fddbSyncInProgress.value = true
            try {
                val date = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
                when (manualFddbDiarySyncUseCase.sync(date)) {
                    is Result.Success -> updateCalorieWidgetValues()
                    is Result.Error -> Unit
                }
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
    val fddbProductSyncProgress: Int,
)

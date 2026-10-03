package com.maksimowiczm.foodyou.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.activity.HealthConnectAvailability
import com.maksimowiczm.foodyou.app.widget.updateCalorieWidgetValues
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.food.domain.repository.FddbCredentialsRepository
import com.maksimowiczm.foodyou.food.domain.usecase.ManualFddbDiarySyncUseCase
import com.maksimowiczm.foodyou.settings.domain.entity.FddbDiarySyncStatus
import com.maksimowiczm.foodyou.settings.domain.entity.FddbProductSyncManualFrequency
import com.maksimowiczm.foodyou.settings.domain.entity.FddbProductSyncMode
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
import com.maksimowiczm.foodyou.settings.domain.entity.fddbDiarySyncStatus
import com.maksimowiczm.foodyou.weight.HealthConnectWeightSync
import com.maksimowiczm.foodyou.sync.SyncLog
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

internal class SynchronizationSettingsViewModel(
    private val settingsRepository: UserPreferencesRepository<Settings>,
    private val manualFddbDiarySyncUseCase: ManualFddbDiarySyncUseCase,
    fddbCredentialsRepository: FddbCredentialsRepository,
    private val healthConnectWeightSync: HealthConnectWeightSync,
    private val syncLog: SyncLog,
) : ViewModel() {

    val syncLogRuns = syncLog.runs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), emptyList())

    fun clearSyncLog() {
        viewModelScope.launch { syncLog.clearCompleted() }
    }

    private val fddbSyncInProgress = MutableStateFlow(false)
    private val weightSyncAvailable = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            weightSyncAvailable.value =
                healthConnectWeightSync.availability() == HealthConnectAvailability.Available
        }
    }

    val model: StateFlow<SynchronizationSettingsModel?> =
        combine(
                settingsRepository.observe(),
                fddbCredentialsRepository.hasCredentials(),
                fddbSyncInProgress,
                weightSyncAvailable,
            ) { settings, hasFddbCredentials, fddbSyncing, weightAvailable ->
                SynchronizationSettingsModel(
                    homeSyncHealthConnectEnabled = settings.homeSyncHealthConnectEnabled,
                    weightSyncEnabled = settings.healthConnectWeightEnabled,
                    weightSyncAvailable = weightAvailable,
                    homeSyncFddbDiaryEnabled = settings.homeSyncFddbDiaryEnabled,
                    fddbDiarySyncStatus = settings.fddbDiarySyncStatus(),
                    hasFddbCredentials = hasFddbCredentials,
                    fddbSyncInProgress = fddbSyncing,
                    fddbProductSyncMode = settings.fddbProductSyncMode,
                    fddbProductSyncManualFrequency = settings.fddbProductSyncManualFrequency,
                    fddbProductSyncManualTriggerCount =
                        settings.fddbProductSyncManualTriggerCount,
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

    fun setWeightSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.update { copy(healthConnectWeightEnabled = enabled) }
            if (enabled) healthConnectWeightSync.syncHistorical()
        }
    }

    fun setHomeSyncFddbDiaryEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.update { copy(homeSyncFddbDiaryEnabled = enabled) }
        }
    }

    fun setFddbProductSyncPolicy(
        mode: FddbProductSyncMode,
        frequency: FddbProductSyncManualFrequency,
    ) {
        viewModelScope.launch {
            val current = settingsRepository.observe().first()
            val changed =
                current.fddbProductSyncMode != mode ||
                    current.fddbProductSyncManualFrequency != frequency
            settingsRepository.update {
                copy(
                    fddbProductSyncMode = mode,
                    fddbProductSyncManualFrequency = frequency,
                    fddbProductSyncManualTriggerCount =
                        if (changed) 0 else fddbProductSyncManualTriggerCount,
                )
            }
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
    val weightSyncEnabled: Boolean,
    val weightSyncAvailable: Boolean,
    val homeSyncFddbDiaryEnabled: Boolean,
    val fddbDiarySyncStatus: FddbDiarySyncStatus?,
    val hasFddbCredentials: Boolean,
    val fddbSyncInProgress: Boolean,
    val fddbProductSyncMode: FddbProductSyncMode,
    val fddbProductSyncManualFrequency: FddbProductSyncManualFrequency,
    val fddbProductSyncManualTriggerCount: Int,
)

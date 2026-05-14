package com.maksimowiczm.foodyou.app.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.activity.HealthConnectActivitySync
import com.maksimowiczm.foodyou.activity.HealthConnectAvailability
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal data class ActivitySettingsModel(
    val kcalPerStep: String,
    val healthConnectEnabled: Boolean,
    val lastSyncedEpochSeconds: Long?,
    val healthConnectStatus: ActivityHealthConnectStatus,
)

internal enum class ActivityHealthConnectStatus {
    Checking,
    Available,
    Unavailable,
    UpdateRequired,
    PermissionMissing,
    PermissionDenied,
    SyncFailed,
    Synced,
}

internal class ActivitySettingsViewModel(
    private val settingsRepository: UserPreferencesRepository<Settings>,
    private val healthConnectActivitySync: HealthConnectActivitySync,
) : ViewModel() {
    private val healthConnectStatus = MutableStateFlow(ActivityHealthConnectStatus.Checking)

    val model: StateFlow<ActivitySettingsModel?> =
        combine(settingsRepository.observe(), healthConnectStatus) { settings, status ->
                ActivitySettingsModel(
                    kcalPerStep = settings.stepsCaloriesPerStepKcal?.toString() ?: "",
                    healthConnectEnabled =
                        settings.healthConnectStepsEnabled &&
                            status != ActivityHealthConnectStatus.PermissionMissing &&
                            status != ActivityHealthConnectStatus.Unavailable &&
                            status != ActivityHealthConnectStatus.UpdateRequired,
                    lastSyncedEpochSeconds = settings.healthConnectStepsLastSyncedEpochSeconds,
                    healthConnectStatus = status,
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), null)

    init {
        refreshHealthConnectStatus()
    }

    fun setKcalPerStep(value: String) {
        val parsed = value.replace(',', '.').toDoubleOrNull()
        viewModelScope.launch {
            settingsRepository.update { copy(stepsCaloriesPerStepKcal = parsed) }
        }
    }

    fun refreshHealthConnectStatus() {
        viewModelScope.launch {
            val settings = settingsRepository.observe().first()
            when (healthConnectActivitySync.availability()) {
                HealthConnectAvailability.Unavailable -> {
                    disableHealthConnectIfNeeded(settings)
                    healthConnectStatus.value = ActivityHealthConnectStatus.Unavailable
                }
                HealthConnectAvailability.UpdateRequired -> {
                    disableHealthConnectIfNeeded(settings)
                    healthConnectStatus.value = ActivityHealthConnectStatus.UpdateRequired
                }
                HealthConnectAvailability.Available -> {
                    val hasPermission = healthConnectActivitySync.hasReadStepsPermission()
                    if (!hasPermission) {
                        disableHealthConnectIfNeeded(settings)
                        healthConnectStatus.value = ActivityHealthConnectStatus.PermissionMissing
                    } else {
                        healthConnectStatus.value =
                            if (settings.healthConnectStepsLastSyncedEpochSeconds == null) {
                                ActivityHealthConnectStatus.Available
                            } else {
                                ActivityHealthConnectStatus.Synced
                            }
                    }
                }
            }
        }
    }

    fun setHealthConnectEnabled(enabled: Boolean, requestPermission: () -> Unit) {
        if (enabled) {
            enableHealthConnect(requestPermission)
        } else {
            disableHealthConnect()
        }
    }

    fun onHealthConnectPermissionResult(granted: Boolean) {
        viewModelScope.launch {
            if (!granted) {
                settingsRepository.update { copy(healthConnectStepsEnabled = false) }
                healthConnectStatus.value = ActivityHealthConnectStatus.PermissionDenied
                return@launch
            }

            settingsRepository.update { copy(healthConnectStepsEnabled = true) }
            healthConnectStatus.value = ActivityHealthConnectStatus.Available
        }
    }

    private fun enableHealthConnect(requestPermission: () -> Unit) {
        viewModelScope.launch {
            healthConnectStatus.value = ActivityHealthConnectStatus.Checking
            when (healthConnectActivitySync.availability()) {
                HealthConnectAvailability.Unavailable -> {
                    settingsRepository.update { copy(healthConnectStepsEnabled = false) }
                    healthConnectStatus.value = ActivityHealthConnectStatus.Unavailable
                }
                HealthConnectAvailability.UpdateRequired -> {
                    settingsRepository.update { copy(healthConnectStepsEnabled = false) }
                    healthConnectStatus.value = ActivityHealthConnectStatus.UpdateRequired
                }
                HealthConnectAvailability.Available -> {
                    if (healthConnectActivitySync.hasReadStepsPermission()) {
                        settingsRepository.update { copy(healthConnectStepsEnabled = true) }
                        healthConnectStatus.value = ActivityHealthConnectStatus.Available
                    } else {
                        healthConnectStatus.value = ActivityHealthConnectStatus.PermissionMissing
                        requestPermission()
                    }
                }
            }
        }
    }

    private fun disableHealthConnect() {
        viewModelScope.launch {
            settingsRepository.update { copy(healthConnectStepsEnabled = false) }
            refreshHealthConnectStatus()
        }
    }

    private suspend fun disableHealthConnectIfNeeded(settings: Settings) {
        if (settings.healthConnectStepsEnabled) {
            settingsRepository.update { copy(healthConnectStepsEnabled = false) }
        }
    }
}

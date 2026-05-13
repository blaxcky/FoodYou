package com.maksimowiczm.foodyou.app.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal data class ActivitySettingsModel(
    val kcalPerStep: String,
    val healthConnectEnabled: Boolean,
    val lastSyncedEpochSeconds: Long?,
)

internal class ActivitySettingsViewModel(
    private val settingsRepository: UserPreferencesRepository<Settings>
) : ViewModel() {
    val model: StateFlow<ActivitySettingsModel?> =
        settingsRepository
            .observe()
            .map {
                ActivitySettingsModel(
                    kcalPerStep = it.stepsCaloriesPerStepKcal?.toString() ?: "",
                    healthConnectEnabled = it.healthConnectStepsEnabled,
                    lastSyncedEpochSeconds = it.healthConnectStepsLastSyncedEpochSeconds,
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), null)

    fun setKcalPerStep(value: String) {
        val parsed = value.replace(',', '.').toDoubleOrNull()
        viewModelScope.launch {
            settingsRepository.update { copy(stepsCaloriesPerStepKcal = parsed) }
        }
    }

    fun setHealthConnectEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.update { copy(healthConnectStepsEnabled = enabled) }
        }
    }
}

package com.maksimowiczm.foodyou.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.settings.domain.entity.FddbDiarySyncStatus
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
import com.maksimowiczm.foodyou.settings.domain.entity.fddbDiarySyncStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class SynchronizationSettingsViewModel(
    private val settingsRepository: UserPreferencesRepository<Settings>
) : ViewModel() {

    val model: StateFlow<SynchronizationSettingsModel?> =
        settingsRepository
            .observe()
            .map { settings ->
                SynchronizationSettingsModel(
                    homeSyncHealthConnectEnabled = settings.homeSyncHealthConnectEnabled,
                    homeSyncFddbDiaryEnabled = settings.homeSyncFddbDiaryEnabled,
                    fddbDiarySyncStatus = settings.fddbDiarySyncStatus(),
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
}

internal data class SynchronizationSettingsModel(
    val homeSyncHealthConnectEnabled: Boolean,
    val homeSyncFddbDiaryEnabled: Boolean,
    val fddbDiarySyncStatus: FddbDiarySyncStatus?,
)

package com.maksimowiczm.foodyou.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.food.domain.entity.FddbProductSyncQueueItem
import com.maksimowiczm.foodyou.food.domain.repository.FddbProductSyncStatusRepository
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

internal class FddbProductSyncQueueViewModel(
    statusRepository: FddbProductSyncStatusRepository,
    settingsRepository: UserPreferencesRepository<Settings>,
) : ViewModel() {
    val model: StateFlow<FddbProductSyncQueueModel> =
        combine(statusRepository.observeQueue(), settingsRepository.observe()) { queue, settings ->
                FddbProductSyncQueueModel(
                    progress = settings.fddbProductSyncManualCount.coerceIn(0, 2),
                    queue = queue,
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(2_000),
                initialValue = FddbProductSyncQueueModel(progress = 0, queue = emptyList()),
            )
}

internal data class FddbProductSyncQueueModel(
    val progress: Int,
    val queue: List<FddbProductSyncQueueItem>,
)

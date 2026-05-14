package com.maksimowiczm.foodyou.app.ui.home.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.activity.domain.entity.ManualActivityEntry
import com.maksimowiczm.foodyou.activity.domain.repository.ActivityRepository
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

internal data class ActivitiesCardModel(
    val steps: Long,
    val stepEnergyKcal: Int,
    val manualEnergyKcal: Int,
    val totalEnergyKcal: Int,
    val manualEntries: List<ManualActivityEntry>,
)

internal class ActivitiesCardViewModel(
    private val activityRepository: ActivityRepository,
    private val settingsRepository: UserPreferencesRepository<Settings>,
) : ViewModel() {
    private val dateState = MutableStateFlow<LocalDate?>(null)

    val model: StateFlow<ActivitiesCardModel?> =
        dateState
            .filterNotNull()
            .flatMapLatest { date ->
                settingsRepository.observe().flatMapLatest { settings ->
                    combine(
                        activityRepository.observeDailySummary(
                            date,
                            settings.stepsCaloriesPerStepKcal,
                        ),
                        activityRepository.observeManualEntries(date),
                    ) { summary, entries ->
                        ActivitiesCardModel(
                            steps = summary.steps,
                            stepEnergyKcal = summary.stepEnergyKcal.toInt(),
                            manualEnergyKcal = summary.manualEnergyKcal.toInt(),
                            totalEnergyKcal = summary.totalEnergyKcal.toInt(),
                            manualEntries = entries,
                        )
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), null)

    fun setDate(date: LocalDate) {
        dateState.value = date
    }
}

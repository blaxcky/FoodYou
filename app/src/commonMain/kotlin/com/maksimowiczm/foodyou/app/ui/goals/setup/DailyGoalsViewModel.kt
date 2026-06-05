package com.maksimowiczm.foodyou.app.ui.goals.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.goals.domain.entity.BasalMetabolicRateProfile
import com.maksimowiczm.foodyou.goals.domain.entity.WeeklyGoals
import com.maksimowiczm.foodyou.goals.domain.repository.BasalMetabolicRateProfileRepository
import com.maksimowiczm.foodyou.goals.domain.repository.GoalsRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class DailyGoalsViewModel(
    private val goalsRepository: GoalsRepository,
    private val basalMetabolicRateProfileRepository: BasalMetabolicRateProfileRepository,
) : ViewModel() {

    val state =
        combine(
                goalsRepository.observeWeeklyGoals(),
                basalMetabolicRateProfileRepository.observeProfile(),
            ) { weeklyGoals, basalMetabolicRateProfile ->
                DailyGoalsSetupState(
                    weeklyGoals = weeklyGoals,
                    basalMetabolicRateProfile = basalMetabolicRateProfile,
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(2_000),
                initialValue = null,
            )

    private val _eventChannel = Channel<DailyGoalsViewModelEvent>()
    val events = _eventChannel.receiveAsFlow()

    fun update(weeklyGoals: WeeklyGoals, basalMetabolicRateProfile: BasalMetabolicRateProfile) {
        viewModelScope.launch {
            goalsRepository.updateWeeklyGoals(weeklyGoals)
            basalMetabolicRateProfileRepository.updateProfile(basalMetabolicRateProfile)
            _eventChannel.send(DailyGoalsViewModelEvent.Updated)
        }
    }
}

internal data class DailyGoalsSetupState(
    val weeklyGoals: WeeklyGoals,
    val basalMetabolicRateProfile: BasalMetabolicRateProfile,
)

package com.maksimowiczm.foodyou.app.ui.goals.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.goals.domain.entity.BasalMetabolicRateProfile
import com.maksimowiczm.foodyou.goals.domain.entity.WeeklyGoals
import com.maksimowiczm.foodyou.goals.domain.repository.BasalMetabolicRateProfileRepository
import com.maksimowiczm.foodyou.goals.domain.repository.GoalsRepository
import com.maksimowiczm.foodyou.settings.domain.entity.DietEnergyDeficitOverride
import com.maksimowiczm.foodyou.settings.domain.entity.GoalDisplayMode
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class DailyGoalsViewModel(
    private val goalsRepository: GoalsRepository,
    private val basalMetabolicRateProfileRepository: BasalMetabolicRateProfileRepository,
    private val settingsRepository: UserPreferencesRepository<Settings>,
) : ViewModel() {

    val state =
        combine(
                goalsRepository.observeWeeklyGoals(),
                basalMetabolicRateProfileRepository.observeProfile(),
                settingsRepository.observe(),
            ) { weeklyGoals, basalMetabolicRateProfile, settings ->
                DailyGoalsSetupState(
                    weeklyGoals = weeklyGoals,
                    basalMetabolicRateProfile = basalMetabolicRateProfile,
                    dietEnergyDeficitKcal = settings.dietEnergyDeficitKcal,
                    dietEnergyDeficitOverride = settings.dietEnergyDeficitOverride,
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(2_000),
                initialValue = null,
            )

    private val _eventChannel = Channel<DailyGoalsViewModelEvent>()
    val events = _eventChannel.receiveAsFlow()

    fun update(
        weeklyGoals: WeeklyGoals,
        basalMetabolicRateProfile: BasalMetabolicRateProfile,
        dietEnergyDeficitKcal: Double?,
        dietEnergyDeficitOverride: DietEnergyDeficitOverride?,
    ) {
        viewModelScope.launch {
            val sanitizedDeficit = dietEnergyDeficitKcal?.takeIf { it > 0.0 }
            val sanitizedOverride =
                dietEnergyDeficitOverride?.takeIf {
                    it.energyDeficitKcal > 0.0 && it.endDate >= it.startDate
                }
            goalsRepository.updateWeeklyGoals(weeklyGoals)
            basalMetabolicRateProfileRepository.updateProfile(basalMetabolicRateProfile)
            settingsRepository.update {
                copy(
                    dietEnergyDeficitKcal = sanitizedDeficit,
                    dietEnergyDeficitOverride = sanitizedOverride,
                    goalDisplayMode =
                        if (
                            sanitizedDeficit == null &&
                                sanitizedOverride == null &&
                                goalDisplayMode == GoalDisplayMode.Diet
                        ) {
                            GoalDisplayMode.Normal
                        } else {
                            goalDisplayMode
                        },
                )
            }
            _eventChannel.send(DailyGoalsViewModelEvent.Updated)
        }
    }
}

internal data class DailyGoalsSetupState(
    val weeklyGoals: WeeklyGoals,
    val basalMetabolicRateProfile: BasalMetabolicRateProfile,
    val dietEnergyDeficitKcal: Double?,
    val dietEnergyDeficitOverride: DietEnergyDeficitOverride?,
)

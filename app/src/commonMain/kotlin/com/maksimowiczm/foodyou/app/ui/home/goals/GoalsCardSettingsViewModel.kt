package com.maksimowiczm.foodyou.app.ui.home.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.settings.domain.entity.GoalDisplayMode
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

internal class GoalsCardSettingsViewModel(
    private val settingsRepository: UserPreferencesRepository<Settings>
) : ViewModel() {

    private val savedDietEnergyDeficitKcal =
        settingsRepository.observe().map {
            it.dietEnergyDeficitKcal?.takeIf { value -> value > 0.0 }
        }

    private val dietEnergyDeficitInput =
        MutableStateFlow(runBlocking { savedDietEnergyDeficitKcal.first().toInput() })

    val model: StateFlow<GoalsCardSettingsModel> =
        dietEnergyDeficitInput
            .map { input -> GoalsCardSettingsModel(dietEnergyDeficitKcal = input) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(2_000),
                initialValue =
                    runBlocking {
                        GoalsCardSettingsModel(dietEnergyDeficitKcal = dietEnergyDeficitInput.value)
                    },
            )

    fun setDietEnergyDeficitKcal(input: String) {
        dietEnergyDeficitInput.value = input
        val deficit = input.parsePositiveDeficit()

        if (input.isBlank() || deficit != null) {
            viewModelScope.launch {
                settingsRepository.update {
                    val sanitizedDeficit = deficit?.takeIf { it > 0.0 }
                    copy(
                        dietEnergyDeficitKcal = sanitizedDeficit,
                        goalDisplayMode =
                            if (sanitizedDeficit == null &&
                                goalDisplayMode == GoalDisplayMode.Diet
                            ) {
                                GoalDisplayMode.Normal
                            } else {
                                goalDisplayMode
                            },
                    )
                }
            }
        }
    }
}

internal data class GoalsCardSettingsModel(
    val dietEnergyDeficitKcal: String
)

private fun Double?.toInput(): String =
    this?.let { value ->
        if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
    } ?: ""

private fun String.parsePositiveDeficit(): Double? =
    trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0.0 }

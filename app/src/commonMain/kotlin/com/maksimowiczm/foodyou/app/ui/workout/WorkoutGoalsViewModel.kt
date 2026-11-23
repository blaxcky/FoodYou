package com.maksimowiczm.foodyou.app.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
import com.maksimowiczm.foodyou.workout.domain.WorkoutGoals
import com.maksimowiczm.foodyou.workout.domain.WorkoutMilestone
import com.maksimowiczm.foodyou.workout.domain.WorkoutMilestoneId
import com.maksimowiczm.foodyou.workout.domain.WorkoutRepository
import com.maksimowiczm.foodyou.workout.workoutGoals
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class WorkoutGoalsViewModel(
    private val repository: WorkoutRepository,
    private val settingsRepository: UserPreferencesRepository<Settings>,
) : ViewModel() {
    val goals: StateFlow<WorkoutGoals?> =
        settingsRepository
            .observe()
            .map { it.workoutGoals }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), null)

    val milestones: StateFlow<List<WorkoutMilestone>> =
        repository
            .observeMilestones()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), emptyList())

    fun updateGoals(transform: WorkoutGoals.() -> WorkoutGoals) {
        viewModelScope.launch {
            settingsRepository.update {
                val updated = workoutGoals.transform()
                copy(
                    workoutStrengthDaysPerWeek = updated.strengthDaysPerWeek.coerceIn(1, 7),
                    workoutStrengthDayMinSets = updated.strengthDayMinSets.coerceIn(1, 20),
                    workoutCardioGoalEnabled = updated.cardioGoalEnabled,
                    workoutCardioDaysPerWeek = updated.cardioDaysPerWeek.coerceIn(1, 7),
                    workoutCardioMinMinutes = updated.cardioMinMinutes.coerceIn(1, 240),
                )
            }
        }
    }

    fun saveMilestone(id: WorkoutMilestoneId?, title: String, description: String, achieved: Boolean) {
        if (title.isBlank()) return
        viewModelScope.launch {
            repository.saveMilestone(
                WorkoutMilestone(
                    id = id ?: WorkoutMilestoneId(0),
                    title = title.trim(),
                    description = description.trim(),
                    achieved = achieved,
                    sortOrder = milestones.value.firstOrNull { it.id == id }?.sortOrder
                        ?: ((milestones.value.maxOfOrNull { it.sortOrder } ?: 0) + 1),
                )
            )
        }
    }

    fun deleteMilestone(id: WorkoutMilestoneId) {
        viewModelScope.launch { repository.deleteMilestone(id) }
    }
}

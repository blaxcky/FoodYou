package com.maksimowiczm.foodyou.app.ui.home.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.activity.domain.entity.ManualActivityEntry
import com.maksimowiczm.foodyou.activity.domain.repository.ActivityRepository
import com.maksimowiczm.foodyou.activity.domain.usecase.StepGoalProgress
import com.maksimowiczm.foodyou.activity.domain.usecase.stepGoalProgress
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
import com.maksimowiczm.foodyou.workout.domain.WorkoutEntry
import com.maksimowiczm.foodyou.workout.domain.WorkoutExercise
import com.maksimowiczm.foodyou.workout.domain.WorkoutKind
import com.maksimowiczm.foodyou.workout.domain.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.math.roundToInt

internal data class ActivitiesCardModel(
    val countedSteps: Long,
    val excludedSteps: Long,
    val healthConnectStepsEnabled: Boolean,
    val stepEnergyKcal: Int,
    val manualEnergyKcal: Int,
    val totalEnergyKcal: Int,
    val manualEntries: List<ManualActivityEntry>,
    val importedEntries: List<com.maksimowiczm.foodyou.training.ImportedActivity> = emptyList(),
    val stepGoal: StepGoalProgress? = null,
    val stepGoalDay: StepGoalDay = StepGoalDay.Today,
    val workoutRows: List<WorkoutCardRow> = emptyList(),
)

/** One row of the "From training" section: all strength sets of the day, or one cardio session. */
internal data class WorkoutCardRow(
    val name: String?,
    val energyKcal: Int,
    val exerciseCount: Int = 0,
    val setCount: Int = 0,
    val firstMinute: Int = 0,
    val lastMinute: Int = 0,
    val durationMinutes: Int? = null,
    val deviceKcal: Int? = null,
) {
    val isStrengthSummary: Boolean
        get() = name == null
}

internal fun workoutCardRows(entries: List<WorkoutEntry>, exercises: List<WorkoutExercise>): List<WorkoutCardRow> {
    val byId = exercises.associateBy { it.id }
    val (cardio, strength) = entries.partition { byId[it.exerciseId]?.kind == WorkoutKind.Cardio }
    val rows = mutableListOf<WorkoutCardRow>()
    if (strength.isNotEmpty()) {
        rows +=
            WorkoutCardRow(
                name = null,
                energyKcal = roundedActivityEnergyKcal(strength.sumOf { it.energyKcal }),
                exerciseCount = strength.map { it.exerciseId }.distinct().size,
                setCount = strength.size,
                firstMinute = strength.minOf { it.minuteOfDay },
                lastMinute = strength.maxOf { it.minuteOfDay },
            )
    }
    cardio.sortedBy { it.minuteOfDay }.forEach { entry ->
        rows +=
            WorkoutCardRow(
                name = byId[entry.exerciseId]?.name.orEmpty(),
                energyKcal = roundedActivityEnergyKcal(entry.energyKcal),
                durationMinutes = entry.durationMinutes,
                deviceKcal = entry.deviceKcal?.let(::roundedActivityEnergyKcal),
            )
    }
    return rows
}

internal enum class StepGoalDay {
    Past,
    Today,
    Future,
}

internal fun stepGoalDay(selectedDate: LocalDate, today: LocalDate): StepGoalDay =
    when {
        selectedDate < today -> StepGoalDay.Past
        selectedDate > today -> StepGoalDay.Future
        else -> StepGoalDay.Today
    }

internal class ActivitiesCardViewModel(
    private val activityRepository: ActivityRepository,
    private val settingsRepository: UserPreferencesRepository<Settings>,
    private val dateProvider: DateProvider,
    private val workoutRepository: WorkoutRepository? = null,
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
                        activityRepository.observeImportedEntries(date),
                        dateProvider.observeDate(),
                        observeWorkoutRows(date),
                    ) { summary, entries, importedEntries, today, workoutRows ->
                        ActivitiesCardModel(
                            countedSteps = summary.countedSteps,
                            excludedSteps = summary.excludedSteps,
                            healthConnectStepsEnabled = settings.healthConnectStepsEnabled,
                            stepEnergyKcal = roundedActivityEnergyKcal(summary.stepEnergyKcal),
                            manualEnergyKcal = roundedActivityEnergyKcal(summary.manualEnergyKcal),
                            totalEnergyKcal = roundedActivityEnergyKcal(summary.totalEnergyKcal),
                            manualEntries = entries,
                            importedEntries = importedEntries,
                            stepGoal =
                                stepGoalProgress(
                                    countedSteps = summary.countedSteps,
                                    dailyStepGoal = settings.dailyStepGoal,
                                    kcalPerStep = settings.stepsCaloriesPerStepKcal,
                                ),
                            stepGoalDay = stepGoalDay(date, today),
                            workoutRows = workoutRows,
                        )
                    }
                }
            }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), null)

    private fun observeWorkoutRows(date: LocalDate): Flow<List<WorkoutCardRow>> {
        val repository = workoutRepository ?: return flowOf(emptyList())
        return combine(repository.observeEntries(date, date), repository.observeExercises()) { entries, exercises ->
            workoutCardRows(entries, exercises)
        }
    }

    fun setDate(date: LocalDate) {
        dateState.value = date
    }
}

internal fun roundedActivityEnergyKcal(energyKcal: Double): Int = energyKcal.roundToInt()

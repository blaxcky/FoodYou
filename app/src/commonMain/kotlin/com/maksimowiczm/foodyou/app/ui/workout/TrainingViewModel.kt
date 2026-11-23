package com.maksimowiczm.foodyou.app.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.activity.HealthConnectActivitySync
import com.maksimowiczm.foodyou.app.widget.updateCalorieWidgetValues
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
import com.maksimowiczm.foodyou.weight.domain.repository.WeightRepository
import com.maksimowiczm.foodyou.workout.domain.WorkoutEntry
import com.maksimowiczm.foodyou.workout.domain.WorkoutExercise
import com.maksimowiczm.foodyou.workout.domain.WorkoutGoals
import com.maksimowiczm.foodyou.workout.domain.WorkoutKind
import com.maksimowiczm.foodyou.workout.domain.WorkoutRepository
import com.maksimowiczm.foodyou.workout.domain.WorkoutToday
import com.maksimowiczm.foodyou.workout.domain.WorkoutWeek
import com.maksimowiczm.foodyou.workout.domain.planWorkoutToday
import com.maksimowiczm.foodyou.workout.domain.planWorkoutWeek
import com.maksimowiczm.foodyou.workout.domain.weekStart
import com.maksimowiczm.foodyou.workout.workoutGoals
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

enum class TrainingTab {
    Today,
    Week,
    History,
}

internal data class TrainingHistoryRow(val entry: WorkoutEntry, val exercise: WorkoutExercise?)

internal data class TrainingUiState(
    val today: LocalDate,
    val query: String,
    val plan: WorkoutToday,
    val todayStrengthSets: Int,
    val todayCardioMinutes: Int,
    val todayKcal: Double,
    val week: WorkoutWeek,
    val goals: WorkoutGoals,
    val historyDate: LocalDate,
    val history: List<TrainingHistoryRow>,
    val exercises: List<WorkoutExercise>,
    val latestWeightKg: Double?,
    /** Entries before [today] are kept for prefilling sheets with the last values. */
    val recentEntries: List<WorkoutEntry>,
)

/** How far back the start screen looks for the last time an exercise was done. */
private const val LOOKBACK_DAYS = 400

internal class TrainingViewModel(
    private val repository: WorkoutRepository,
    private val settingsRepository: UserPreferencesRepository<Settings>,
    private val weightRepository: WeightRepository,
    private val healthConnectActivitySync: HealthConnectActivitySync,
    private val dateProvider: DateProvider,
    /** Outlives the screen, so leaving right after saving neither loses the write nor the step sync. */
    private val backgroundScope: CoroutineScope,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val historyDate = MutableStateFlow<LocalDate?>(null)
    private val weekAnchor = MutableStateFlow<LocalDate?>(null)

    private val latestWeight: Flow<Double?> =
        weightRepository.observeEntries().map { it.firstOrNull()?.weightKg }

    val state: StateFlow<TrainingUiState?> =
        dateProvider
            .observeDate()
            .flatMapLatest { today ->
                combine(historyDate, weekAnchor) { history, week -> history to week }
                    .flatMapLatest { (history, week) ->
                        val selectedHistory = history ?: today
                        val selectedWeek = week ?: today
                        val from =
                            minOf(
                                today.minus(LOOKBACK_DAYS, DateTimeUnit.DAY),
                                selectedHistory,
                                weekStart(selectedWeek),
                            )
                        val to =
                            maxOf(today, selectedHistory, weekStart(selectedWeek).plus(6, DateTimeUnit.DAY))
                        combine(
                            repository.observeExercises(),
                            repository.observeEntries(from, to),
                            settingsRepository.observe(),
                            query,
                            latestWeight,
                        ) { exercises, entries, settings, query, weight ->
                            val goals = settings.workoutGoals
                            val byId = exercises.associateBy { it.id }
                            val todays = entries.filter { it.date == today }
                            TrainingUiState(
                                today = today,
                                query = query,
                                plan = planWorkoutToday(exercises, entries, today, query),
                                todayStrengthSets =
                                    todays.count { byId[it.exerciseId]?.kind == WorkoutKind.Strength },
                                todayCardioMinutes =
                                    todays
                                        .filter { byId[it.exerciseId]?.kind == WorkoutKind.Cardio }
                                        .sumOf { it.durationMinutes ?: 0 },
                                todayKcal = todays.sumOf { it.energyKcal },
                                week = planWorkoutWeek(exercises, entries, selectedWeek, today, goals),
                                goals = goals,
                                historyDate = selectedHistory,
                                history =
                                    entries
                                        .filter { it.date == selectedHistory }
                                        .sortedWith(compareBy({ it.minuteOfDay }, { it.id.value }))
                                        .map { TrainingHistoryRow(it, byId[it.exerciseId]) },
                                exercises = exercises,
                                latestWeightKg = weight,
                                recentEntries = entries.filter { it.date <= today },
                            )
                        }
                    }
            }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), null)

    fun setQuery(value: String) {
        query.value = value
    }

    fun setHistoryDate(date: LocalDate) {
        historyDate.value = date
    }

    private var initialDateApplied = false

    /** Applies the day a screen was opened for once per ViewModel, also after recreation. */
    fun applyInitialDate(date: LocalDate?) {
        if (initialDateApplied) return
        initialDateApplied = true
        if (date != null) historyDate.value = date
    }

    fun shiftHistoryDay(days: Int) {
        val current = historyDate.value ?: state.value?.today ?: return
        historyDate.value = current.plus(days, DateTimeUnit.DAY)
    }

    fun shiftWeek(weeks: Int) {
        val current = weekAnchor.value ?: state.value?.today ?: return
        weekAnchor.value = current.plus(weeks * 7, DateTimeUnit.DAY)
    }

    fun nowMinuteOfDay(): Int = dateProvider.now().time.let { it.hour * 60 + it.minute }

    /** Saves a new or edited entry built by [buildWorkoutEntry]. */
    fun saveEntry(exercise: WorkoutExercise, draft: WorkoutEntryDraft) {
        backgroundScope.launch {
            val weight = latestWeight.first()
            val now = dateProvider.nowInstant()
            val existing = draft.entryId?.let { repository.observeEntry(it).first() }
            val entry =
                buildWorkoutEntry(
                    exercise = exercise,
                    draft = draft,
                    nowMinuteOfDay = nowMinuteOfDay(),
                    bodyWeightKg = weight,
                    now = now,
                    createdAt = existing?.createdAt ?: now,
                )
            if (existing == null) repository.createEntry(entry) else repository.updateEntry(entry)
            afterChange(exercise, listOfNotNull(entry.date, existing?.date).distinct())
        }
    }

    fun deleteEntry(entry: WorkoutEntry) {
        backgroundScope.launch {
            repository.deleteEntry(entry.id)
            val exercise = repository.observeExercise(entry.exerciseId).first()
            afterChange(exercise, listOf(entry.date))
        }
    }

    fun restoreEntry(entry: WorkoutEntry) {
        backgroundScope.launch {
            repository.createEntry(entry)
            val exercise = repository.observeExercise(entry.exerciseId).first()
            afterChange(exercise, listOf(entry.date))
        }
    }

    private suspend fun afterChange(exercise: WorkoutExercise?, dates: List<LocalDate>) {
        runCatching { updateCalorieWidgetValues() }
        if (exercise?.kind == WorkoutKind.Cardio) {
            // Cardio minutes are excluded from the steps; recalculate the affected days.
            try {
                healthConnectActivitySync.syncSteps(dates)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // The next regular step sync recalculates the day.
            }
        }
    }
}

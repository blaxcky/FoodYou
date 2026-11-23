package com.maksimowiczm.foodyou.workout.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Up to this many exercises the start screen shows only the list, without suggestions. */
const val WORKOUT_SUGGESTIONS_MIN_EXERCISES = 5
const val WORKOUT_SUGGESTION_COUNT = 2
const val WORKOUT_SEARCH_MIN_EXERCISES = 12

data class WorkoutTodayItem(
    val exercise: WorkoutExercise,
    /** Most recent entry before or on the selected day. */
    val lastEntry: WorkoutEntry?,
    val daysSinceLast: Int?,
    val entriesToday: List<WorkoutEntry>,
) {
    val doneToday: Boolean
        get() = entriesToday.isNotEmpty()

    /** True when the exercise has its own reminder and was not done for at least that long. */
    val isOverdue: Boolean
        get() {
            val reminder = exercise.reminderDays ?: return false
            return daysSinceLast == null || daysSinceLast >= reminder
        }
}

data class WorkoutToday(
    val suggestions: List<WorkoutTodayItem>,
    val items: List<WorkoutTodayItem>,
    val showSearch: Boolean,
    val exerciseCount: Int,
)

/**
 * Orders the exercises for the start screen: longest ago first, never logged before that, and
 * everything already done today at the bottom in the order it was done.
 */
fun planWorkoutToday(
    exercises: List<WorkoutExercise>,
    entries: List<WorkoutEntry>,
    today: LocalDate,
    query: String = "",
): WorkoutToday {
    val active = exercises.filterNot { it.archived }
    val byExercise = entries.filter { it.date <= today }.groupBy { it.exerciseId }
    val all =
        active.map { exercise ->
            val own = byExercise[exercise.id].orEmpty()
            val todays = own.filter { it.date == today }.sortedBy { it.minuteOfDay }
            val last = own.maxWithOrNull(compareBy({ it.date }, { it.minuteOfDay }, { it.id.value }))
            WorkoutTodayItem(
                exercise = exercise,
                lastEntry = last,
                daysSinceLast = last?.date?.daysUntil(today),
                entriesToday = todays,
            )
        }
    val open =
        all.filterNot { it.doneToday }
            .sortedWith(
                compareByDescending<WorkoutTodayItem> { it.daysSinceLast ?: Int.MAX_VALUE }
                    .thenBy { it.exercise.name.lowercase() }
            )
    val done = all.filter { it.doneToday }.sortedBy { it.entriesToday.first().minuteOfDay }
    val ordered = open + done
    // Below the threshold there is no search field, so a leftover query must not filter.
    val normalizedQuery =
        if (active.size < WORKOUT_SEARCH_MIN_EXERCISES) "" else query.trim().lowercase()
    val matching =
        if (normalizedQuery.isEmpty()) ordered
        else ordered.filter { it.exercise.name.lowercase().contains(normalizedQuery) }
    // Exercises past their own reminder come first; otherwise the longest ago wins.
    val suggestions =
        if (active.size < WORKOUT_SUGGESTIONS_MIN_EXERCISES || normalizedQuery.isNotEmpty()) emptyList()
        else
            matching
                .filterNot { it.doneToday }
                .sortedByDescending { it.isOverdue }
                .take(WORKOUT_SUGGESTION_COUNT)
    return WorkoutToday(
        suggestions = suggestions,
        items = matching - suggestions.toSet(),
        showSearch = active.size >= WORKOUT_SEARCH_MIN_EXERCISES,
        exerciseCount = active.size,
    )
}

/** Monday of the week containing [date]. */
fun weekStart(date: LocalDate): LocalDate =
    date.minus(date.dayOfWeek.ordinal - DayOfWeek.MONDAY.ordinal, DateTimeUnit.DAY)

data class WorkoutWeekRow(
    val exercise: WorkoutExercise,
    /** Seven flags, Monday to Sunday. */
    val doneOnDay: List<Boolean>,
) {
    val dayCount: Int
        get() = doneOnDay.count { it }
}

data class WorkoutWeek(
    val start: LocalDate,
    val strengthRows: List<WorkoutWeekRow>,
    val cardioRows: List<WorkoutWeekRow>,
    val notDone: List<WorkoutExercise>,
    val strengthDays: Int,
    val cardioDays: Int,
    /** Days of the week up to and including today, 0..7. */
    val elapsedDays: Int,
)

fun planWorkoutWeek(
    exercises: List<WorkoutExercise>,
    entries: List<WorkoutEntry>,
    anyDayInWeek: LocalDate,
    today: LocalDate,
    goals: WorkoutGoals,
): WorkoutWeek {
    val start = weekStart(anyDayInWeek)
    val days = (0 until 7).map { start.plus(it, DateTimeUnit.DAY) }
    val inWeek = entries.filter { it.date in days }
    val byExercise = inWeek.groupBy { it.exerciseId }
    val exerciseById = exercises.associateBy { it.id }
    val rows =
        exercises
            .filter { !it.archived || byExercise.containsKey(it.id) }
            .map { exercise ->
                val dates = byExercise[exercise.id].orEmpty().map { it.date }.toSet()
                WorkoutWeekRow(exercise, days.map { it in dates })
            }
    val withEntries = rows.filter { it.dayCount > 0 }.sortedByDescending { it.dayCount }
    val strengthDays =
        days.count { day ->
            inWeek.count { it.date == day && exerciseById[it.exerciseId]?.kind == WorkoutKind.Strength } >=
                goals.strengthDayMinSets.coerceAtLeast(1)
        }
    val cardioDays =
        days.count { day ->
            inWeek
                .filter { it.date == day && exerciseById[it.exerciseId]?.kind == WorkoutKind.Cardio }
                .sumOf { it.durationMinutes ?: 0 } >= goals.cardioMinMinutes.coerceAtLeast(1)
        }
    return WorkoutWeek(
        start = start,
        strengthRows = withEntries.filter { it.exercise.kind == WorkoutKind.Strength },
        cardioRows = withEntries.filter { it.exercise.kind == WorkoutKind.Cardio },
        notDone = rows.filter { it.dayCount == 0 && !it.exercise.archived }.map { it.exercise },
        strengthDays = strengthDays,
        cardioDays = cardioDays,
        elapsedDays = (start.daysUntil(today) + 1).coerceIn(0, 7),
    )
}

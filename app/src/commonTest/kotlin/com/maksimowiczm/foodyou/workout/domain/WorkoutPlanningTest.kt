package com.maksimowiczm.foodyou.workout.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

class WorkoutPlanningTest {
    private val created = Instant.fromEpochSeconds(0)
    private val today = LocalDate(2026, 11, 26) // Thursday

    private fun exercise(id: Long, name: String, kind: WorkoutKind = WorkoutKind.Strength, archived: Boolean = false) =
        WorkoutExercise(
            id = WorkoutExerciseId(id),
            name = name,
            kind = kind,
            archived = archived,
            createdAt = created,
        )

    private var nextEntryId = 1L

    private fun entry(exercise: Long, date: LocalDate, minute: Int = 600, duration: Int? = null) =
        WorkoutEntry(
            id = WorkoutEntryId(nextEntryId++),
            exerciseId = WorkoutExerciseId(exercise),
            date = date,
            minuteOfDay = minute,
            durationMinutes = duration,
            energyKcal = 4.0,
            createdAt = created,
            updatedAt = created,
        )

    @Test
    fun fewExercisesShowOnlyTheListSortedByLongestAgo() {
        val exercises = listOf(exercise(1, "Hang"), exercise(2, "Rudern KH"), exercise(3, "Neu"))
        val entries =
            listOf(
                entry(1, LocalDate(2026, 11, 23)),
                entry(2, LocalDate(2026, 11, 25)),
            )
        val plan = planWorkoutToday(exercises, entries, today)
        assertTrue(plan.suggestions.isEmpty())
        assertFalse(plan.showSearch)
        assertEquals(listOf("Neu", "Hang", "Rudern KH"), plan.items.map { it.exercise.name })
        assertEquals(3, plan.items[1].daysSinceLast)
    }

    @Test
    fun doneTodayMovesToTheBottomInTimeOrder() {
        val exercises = (1L..6L).map { exercise(it, "E$it") }
        val entries =
            listOf(
                entry(1, today, minute = 1110),
                entry(2, today, minute = 460),
                entry(3, LocalDate(2026, 11, 20)),
                entry(4, LocalDate(2026, 11, 22)),
                entry(5, LocalDate(2026, 11, 25)),
                entry(6, LocalDate(2026, 11, 24)),
            )
        val plan = planWorkoutToday(exercises, entries, today)
        assertEquals(listOf("E3", "E4"), plan.suggestions.map { it.exercise.name })
        assertEquals(listOf("E6", "E5", "E2", "E1"), plan.items.map { it.exercise.name })
        assertTrue(plan.items.last().doneToday)
    }

    @Test
    fun anExercisePastItsOwnReminderIsSuggestedFirst() {
        val exercises =
            (1L..5L).map { exercise(it, "E$it") }.map {
                if (it.id.value == 5L) it.copy(reminderDays = 2) else it
            }
        val entries =
            listOf(
                entry(1, LocalDate(2026, 11, 16)),
                entry(2, LocalDate(2026, 11, 17)),
                entry(3, LocalDate(2026, 11, 18)),
                entry(4, LocalDate(2026, 11, 19)),
                entry(5, LocalDate(2026, 11, 24)),
            )
        val plan = planWorkoutToday(exercises, entries, today)
        assertEquals(listOf("E5", "E1"), plan.suggestions.map { it.exercise.name })
    }

    @Test
    fun searchAppearsFromTwelveExercisesAndFilters() {
        val exercises = (1L..12L).map { exercise(it, if (it == 7L) "Toter Hang" else "Übung $it") }
        val plan = planWorkoutToday(exercises, emptyList(), today, query = "hang")
        assertTrue(plan.showSearch)
        assertTrue(plan.suggestions.isEmpty())
        assertEquals(listOf("Toter Hang"), plan.items.map { it.exercise.name })

        val fewer = planWorkoutToday(exercises.take(11), emptyList(), today, query = "hang")
        assertFalse(fewer.showSearch)
        assertEquals(11, (fewer.suggestions + fewer.items).size)
    }

    @Test
    fun archivedExercisesAreHidden() {
        val plan = planWorkoutToday(listOf(exercise(1, "Alt", archived = true)), emptyList(), today)
        assertEquals(0, plan.exerciseCount)
        assertTrue(plan.items.isEmpty())
    }

    @Test
    fun weekStartsOnMonday() {
        assertEquals(LocalDate(2026, 11, 23), weekStart(today))
        assertEquals(LocalDate(2026, 11, 23), weekStart(LocalDate(2026, 11, 29)))
    }

    @Test
    fun weekCountsStrengthAndCardioDaysAndGroupsUnusedExercises() {
        val exercises =
            listOf(
                exercise(1, "KH-Rudern"),
                exercise(2, "Hang"),
                exercise(3, "Schulter"),
                exercise(4, "Crosstrainer", WorkoutKind.Cardio),
            )
        val entries =
            listOf(
                entry(1, LocalDate(2026, 11, 23)),
                entry(1, LocalDate(2026, 11, 25)),
                entry(2, LocalDate(2026, 11, 25)),
                entry(4, LocalDate(2026, 11, 24), duration = 10),
                entry(4, LocalDate(2026, 11, 26), duration = 5),
                entry(1, LocalDate(2026, 11, 16)),
            )
        val week =
            planWorkoutWeek(exercises, entries, today, today, WorkoutGoals(cardioMinMinutes = 10))
        assertEquals(2, week.strengthDays)
        assertEquals(1, week.cardioDays)
        assertEquals(4, week.elapsedDays)
        assertEquals(listOf("KH-Rudern", "Hang"), week.strengthRows.map { it.exercise.name })
        assertEquals(listOf(true, false, true, false, false, false, false), week.strengthRows[0].doneOnDay)
        assertEquals(listOf("Schulter"), week.notDone.map { it.name })

        val strict =
            planWorkoutWeek(exercises, entries, today, today, WorkoutGoals(strengthDayMinSets = 2))
        assertEquals(1, strict.strengthDays)
    }
}

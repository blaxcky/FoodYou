package com.maksimowiczm.foodyou.app.ui.workout

import com.maksimowiczm.foodyou.app.ui.home.activity.workoutCardRows
import com.maksimowiczm.foodyou.workout.domain.CardioConversion
import com.maksimowiczm.foodyou.workout.domain.StrengthKcalMode
import com.maksimowiczm.foodyou.workout.domain.StrengthMeasure
import com.maksimowiczm.foodyou.workout.domain.WorkoutEntry
import com.maksimowiczm.foodyou.workout.domain.WorkoutEntryId
import com.maksimowiczm.foodyou.workout.domain.WorkoutExercise
import com.maksimowiczm.foodyou.workout.domain.WorkoutExerciseId
import com.maksimowiczm.foodyou.workout.domain.WorkoutKind
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

class WorkoutEntryDraftTest {
    private val now = Instant.fromEpochSeconds(1_000)
    private val date = LocalDate(2026, 11, 26)

    private val rows =
        WorkoutExercise(
            id = WorkoutExerciseId(1),
            name = "Kurzhantel-Rudern",
            kind = WorkoutKind.Strength,
            usesWeight = true,
            weightOptionsKg = listOf(6.0, 8.0),
            kcalValue = 4.0,
            createdAt = now,
        )

    private val rowing =
        WorkoutExercise(
            id = WorkoutExerciseId(2),
            name = "Rudern",
            kind = WorkoutKind.Cardio,
            cardioConversion = CardioConversion.Concept2,
            cardioDiscountPercent = 10.0,
            createdAt = now,
        )

    private fun entry(exercise: WorkoutExercise, minute: Int, kcal: Double, duration: Int? = null, reps: Int? = 10) =
        WorkoutEntry(
            id = WorkoutEntryId(minute.toLong()),
            exerciseId = exercise.id,
            date = date,
            minuteOfDay = minute,
            reps = reps,
            weightKg = if (exercise.kind == WorkoutKind.Strength) 8.0 else null,
            durationMinutes = duration,
            deviceKcal = if (duration != null) 260.0 else null,
            energyKcal = kcal,
            createdAt = now,
            updatedAt = now,
        )

    @Test
    fun newDraftIsPrefilledWithTheLastEntry() {
        val draft = newWorkoutEntryDraft(rows, date, entry(rows, 460, 4.0).copy(reps = 12))
        assertEquals(12, draft.reps)
        assertEquals(8.0, draft.weightKg)
        assertNull(draft.minuteOfDay)

        val first = newWorkoutEntryDraft(rows, date, lastEntry = null)
        assertEquals(6.0, first.weightKg)
    }

    @Test
    fun strengthEntryKeepsOnlyTheMeasuredValues() {
        val built = buildWorkoutEntry(rows, newWorkoutEntryDraft(rows, date, null), nowMinuteOfDay = 600, bodyWeightKg = 82.4, now = now)
        assertEquals(600, built.minuteOfDay)
        assertEquals(10, built.reps)
        assertEquals(6.0, built.weightKg)
        assertNull(built.seconds)
        assertNull(built.durationMinutes)
        assertEquals(4.0, built.energyKcal)

        val seconds = rows.copy(measure = StrengthMeasure.Seconds, usesWeight = false, kcalValue = 2.0)
        val hang = buildWorkoutEntry(seconds, WorkoutEntryDraft(date = date, seconds = 25), 600, null, now)
        assertEquals(25, hang.seconds)
        assertNull(hang.reps)
        assertNull(hang.weightKg)
    }

    @Test
    fun typedKcalOverridesTheDefaultButPerRepFollowsTheRepetitions() {
        val override = buildWorkoutEntry(rows, WorkoutEntryDraft(date = date, kcalOverride = 6.0), 600, null, now)
        assertEquals(6.0, override.energyKcal)

        val perRep = rows.copy(kcalMode = StrengthKcalMode.PerRep, kcalValue = 0.5)
        assertEquals(6.0, WorkoutEntryDraft(date = date, reps = 12).energyKcal(perRep, null))
    }

    @Test
    fun cardioLoggedNowStartsWhenTheSessionBegan() {
        val draft = WorkoutEntryDraft(date = date, durationMinutes = 20, deviceKcal = 260.0)
        val built = buildWorkoutEntry(rowing, draft, nowMinuteOfDay = 985, bodyWeightKg = 82.4, now = now)
        assertEquals(965, built.minuteOfDay)
        assertEquals(20, built.durationMinutes)
        assertEquals(260.0, built.deviceKcal)
        assertNull(built.reps)
        assertTrue(abs(built.energyKcal - 237.4) < 0.2, "was ${built.energyKcal}")
    }

    @Test
    fun editingKeepsTheIdAndCreationTime() {
        val existing = entry(rows, 460, 4.0)
        val built =
            buildWorkoutEntry(rows, existing.toDraft().copy(reps = 11), 600, null, now = Instant.fromEpochSeconds(5_000), createdAt = existing.createdAt)
        assertEquals(existing.id, built.id)
        assertEquals(460, built.minuteOfDay)
        assertEquals(11, built.reps)
        assertEquals(existing.createdAt, built.createdAt)
    }

    @Test
    fun cardRowsGroupStrengthAndListCardio() {
        val result =
            workoutCardRows(
                listOf(
                    entry(rows, 460, 4.0),
                    entry(rows, 470, 4.0),
                    entry(rowing, 965, 238.0, duration = 20, reps = null),
                ),
                listOf(rows, rowing),
            )
        assertEquals(2, result.size)
        val strength = result[0]
        assertTrue(strength.isStrengthSummary)
        assertEquals(1, strength.exerciseCount)
        assertEquals(2, strength.setCount)
        assertEquals(8, strength.energyKcal)
        assertEquals(460, strength.firstMinute)
        assertEquals(470, strength.lastMinute)
        assertFalse(result[1].isStrengthSummary)
        assertEquals("Rudern", result[1].name)
        assertEquals(260, result[1].deviceKcal)
    }

    @Test
    fun exerciseFormParsesGermanDecimals() {
        val exercise =
            WorkoutExerciseForm(
                    name = "  Rudern ",
                    kind = WorkoutKind.Cardio,
                    kcalValue = "",
                    cardioFactor = "0,85",
                    cardioDiscountPercent = "12,5",
                    reminderDays = "4",
                )
                .toExercise(now)
        assertEquals("Rudern", exercise.name)
        assertEquals(0.85, exercise.cardioFactor)
        assertEquals(12.5, exercise.cardioDiscountPercent)
        assertEquals(4, exercise.reminderDays)
        assertFalse(exercise.usesWeight)
        assertFalse(WorkoutExerciseForm(name = "x", cardioDiscountPercent = "120").canSave)
        assertFalse(WorkoutExerciseForm(name = " ").canSave)
    }
}

package com.maksimowiczm.foodyou.app.ui.workout

import com.maksimowiczm.foodyou.workout.domain.StrengthMeasure
import com.maksimowiczm.foodyou.workout.domain.WorkoutEntry
import com.maksimowiczm.foodyou.workout.domain.WorkoutEntryId
import com.maksimowiczm.foodyou.workout.domain.WorkoutExercise
import com.maksimowiczm.foodyou.workout.domain.WorkoutFeel
import com.maksimowiczm.foodyou.workout.domain.WorkoutKind
import com.maksimowiczm.foodyou.workout.domain.cardioEnergy
import com.maksimowiczm.foodyou.workout.domain.startMinuteForNow
import com.maksimowiczm.foodyou.workout.domain.strengthSetKcal
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/** Values typed into an entry sheet. A null [minuteOfDay] means "now". */
data class WorkoutEntryDraft(
    val entryId: WorkoutEntryId? = null,
    val date: LocalDate,
    val minuteOfDay: Int? = null,
    val reps: Int = 10,
    val seconds: Int = 20,
    val level: Int = 1,
    val weightKg: Double? = null,
    val durationMinutes: Int = 10,
    val deviceKcal: Double = 0.0,
    val distanceMeters: Int? = null,
    /** kcal typed for this set; null keeps the exercise default. */
    val kcalOverride: Double? = null,
    val feel: WorkoutFeel? = null,
)

/** Prefills a new entry with the values of the most recent one, so one tap usually saves. */
fun newWorkoutEntryDraft(
    exercise: WorkoutExercise,
    date: LocalDate,
    lastEntry: WorkoutEntry?,
    minuteOfDay: Int? = null,
): WorkoutEntryDraft {
    val base = WorkoutEntryDraft(date = date, minuteOfDay = minuteOfDay)
    val weight =
        lastEntry?.weightKg?.takeIf { exercise.usesWeight }
            ?: exercise.weightOptionsKg.firstOrNull().takeIf { exercise.usesWeight }
    return base.copy(
        reps = lastEntry?.reps ?: base.reps,
        seconds = lastEntry?.seconds ?: base.seconds,
        level = lastEntry?.level ?: base.level,
        weightKg = weight,
        durationMinutes = lastEntry?.durationMinutes ?: base.durationMinutes,
        deviceKcal = lastEntry?.deviceKcal ?: base.deviceKcal,
        distanceMeters = lastEntry?.distanceMeters,
    )
}

fun WorkoutEntry.toDraft(): WorkoutEntryDraft =
    WorkoutEntryDraft(
        entryId = id,
        date = date,
        minuteOfDay = minuteOfDay,
        reps = reps ?: 10,
        seconds = seconds ?: 20,
        level = level ?: 1,
        weightKg = weightKg,
        durationMinutes = durationMinutes ?: 10,
        deviceKcal = deviceKcal ?: 0.0,
        distanceMeters = distanceMeters,
        kcalOverride = energyKcal,
        feel = feel,
    )

/** kcal that the sheet shows and that will be stored. */
fun WorkoutEntryDraft.energyKcal(exercise: WorkoutExercise, bodyWeightKg: Double?): Double =
    when (exercise.kind) {
        WorkoutKind.Strength -> kcalOverride ?: strengthSetKcal(exercise, reps)
        WorkoutKind.Cardio ->
            cardioEnergy(exercise, deviceKcal, durationMinutes, bodyWeightKg).finalKcal
    }

fun buildWorkoutEntry(
    exercise: WorkoutExercise,
    draft: WorkoutEntryDraft,
    nowMinuteOfDay: Int,
    bodyWeightKg: Double?,
    now: Instant,
    createdAt: Instant = now,
): WorkoutEntry {
    val cardio = exercise.kind == WorkoutKind.Cardio
    val minute =
        draft.minuteOfDay
            ?: startMinuteForNow(nowMinuteOfDay, if (cardio) draft.durationMinutes else null)
    return WorkoutEntry(
        id = draft.entryId ?: WorkoutEntryId(0),
        exerciseId = exercise.id,
        date = draft.date,
        minuteOfDay = minute,
        reps =
            draft.reps.takeIf {
                !cardio && exercise.measure in setOf(StrengthMeasure.Reps, StrengthMeasure.Level)
            },
        seconds = draft.seconds.takeIf { !cardio && exercise.measure == StrengthMeasure.Seconds },
        level = draft.level.takeIf { !cardio && exercise.measure == StrengthMeasure.Level },
        weightKg = draft.weightKg.takeIf { !cardio && exercise.usesWeight },
        durationMinutes = draft.durationMinutes.takeIf { cardio },
        deviceKcal = draft.deviceKcal.takeIf { cardio },
        distanceMeters = draft.distanceMeters.takeIf { cardio && exercise.askDistance },
        energyKcal = draft.energyKcal(exercise, bodyWeightKg),
        feel = draft.feel.takeIf { !cardio },
        createdAt = createdAt,
        updatedAt = now,
    )
}

package com.maksimowiczm.foodyou.workout.domain

import kotlin.jvm.JvmInline
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/** Home training exercise. Every value is entered by hand; nothing is tracked live. */
@JvmInline value class WorkoutExerciseId(val value: Long)

enum class WorkoutKind {
    Strength,
    Cardio,
}

enum class StrengthMeasure {
    Reps,
    Seconds,
    Level,
}

enum class StrengthKcalMode {
    PerSet,
    PerRep,
}

enum class CardioConversion {
    Device,
    Concept2,
    Factor,
}

data class WorkoutExercise(
    val id: WorkoutExerciseId,
    val name: String,
    val kind: WorkoutKind,
    val measure: StrengthMeasure = StrengthMeasure.Reps,
    val usesWeight: Boolean = false,
    val weightOptionsKg: List<Double> = emptyList(),
    val note: String = "",
    val kcalMode: StrengthKcalMode = StrengthKcalMode.PerSet,
    /** kcal per set or per repetition, depending on [kcalMode]. */
    val kcalValue: Double = 0.0,
    val helpsWith: String = "",
    val reminderDays: Int? = null,
    val cardioConversion: CardioConversion = CardioConversion.Device,
    val cardioFactor: Double = 1.0,
    val cardioDiscountPercent: Double = 0.0,
    val askDistance: Boolean = false,
    val excludeSteps: Boolean = true,
    val archived: Boolean = false,
    val createdAt: Instant,
)

@JvmInline value class WorkoutEntryId(val value: Long)

enum class WorkoutFeel {
    Easy,
    Fine,
    Hard,
}

/** One strength set or one cardio session. [energyKcal] is a snapshot and never recalculated. */
data class WorkoutEntry(
    val id: WorkoutEntryId,
    val exerciseId: WorkoutExerciseId,
    val date: LocalDate,
    /** Start time as minute of the day, 0..1439. */
    val minuteOfDay: Int,
    val reps: Int? = null,
    val seconds: Int? = null,
    val level: Int? = null,
    val weightKg: Double? = null,
    val durationMinutes: Int? = null,
    val deviceKcal: Double? = null,
    val distanceMeters: Int? = null,
    val energyKcal: Double,
    val feel: WorkoutFeel? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@JvmInline value class WorkoutMilestoneId(val value: Long)

data class WorkoutMilestone(
    val id: WorkoutMilestoneId,
    val title: String,
    val description: String,
    val achieved: Boolean = false,
    val sortOrder: Int = 0,
)

/** Weekly goals stored in the settings. */
data class WorkoutGoals(
    val strengthDaysPerWeek: Int = DEFAULT_STRENGTH_DAYS_PER_WEEK,
    val strengthDayMinSets: Int = 1,
    val cardioGoalEnabled: Boolean = false,
    val cardioDaysPerWeek: Int = DEFAULT_CARDIO_DAYS_PER_WEEK,
    val cardioMinMinutes: Int = DEFAULT_CARDIO_MIN_MINUTES,
) {
    companion object {
        const val DEFAULT_STRENGTH_DAYS_PER_WEEK = 4
        const val DEFAULT_CARDIO_DAYS_PER_WEEK = 3
        const val DEFAULT_CARDIO_MIN_MINUTES = 10
    }
}

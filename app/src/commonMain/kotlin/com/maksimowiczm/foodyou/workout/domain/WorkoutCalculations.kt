package com.maksimowiczm.foodyou.workout.domain

import com.maksimowiczm.foodyou.activity.domain.usecase.MINUTES_PER_DAY
import com.maksimowiczm.foodyou.activity.domain.usecase.calculateDiscountedActivityEnergyKcal

/** Concept2 monitors assume a 175 lb (79.5 kg) rower. */
const val CONCEPT2_REFERENCE_KCAL_PER_HOUR = 300.0
const val CONCEPT2_KCAL_PER_HOUR_PER_POUND = 1.714
const val KILOGRAMS_TO_POUNDS = 2.2046226218

/**
 * Concept2 calorie calculator: true kcal/h = monitor kcal/h − 300 + 1.714 × body weight in lb.
 *
 * Returns the monitor value unchanged when the duration or the weight is unknown.
 */
fun concept2AdjustedKcal(monitorKcal: Double, durationMinutes: Int, bodyWeightKg: Double?): Double {
    if (durationMinutes <= 0 || bodyWeightKg == null || bodyWeightKg <= 0.0) {
        return monitorKcal.coerceAtLeast(0.0)
    }
    val hours = durationMinutes / 60.0
    val monitorPerHour = monitorKcal / hours
    val truePerHour =
        monitorPerHour - CONCEPT2_REFERENCE_KCAL_PER_HOUR +
            CONCEPT2_KCAL_PER_HOUR_PER_POUND * bodyWeightKg * KILOGRAMS_TO_POUNDS
    return (truePerHour * hours).coerceAtLeast(0.0)
}

/** Breakdown of a cardio entry, shown in the entry sheet. */
data class CardioEnergy(
    val deviceKcal: Double,
    val convertedKcal: Double,
    val discountKcal: Double,
    val finalKcal: Double,
    /** False when Concept2 was chosen but no body weight is known. */
    val usedBodyWeight: Boolean,
)

fun cardioEnergy(
    exercise: WorkoutExercise,
    deviceKcal: Double,
    durationMinutes: Int,
    bodyWeightKg: Double?,
): CardioEnergy {
    val device = deviceKcal.coerceAtLeast(0.0)
    val converted =
        when (exercise.cardioConversion) {
            CardioConversion.Device -> device
            CardioConversion.Factor -> device * exercise.cardioFactor.coerceAtLeast(0.0)
            CardioConversion.Concept2 -> concept2AdjustedKcal(device, durationMinutes, bodyWeightKg)
        }
    val discount = exercise.cardioDiscountPercent.coerceIn(0.0, 100.0)
    val final = calculateDiscountedActivityEnergyKcal(converted, discount)
    return CardioEnergy(
        deviceKcal = device,
        convertedKcal = converted,
        discountKcal = converted - final,
        finalKcal = final,
        usedBodyWeight =
            exercise.cardioConversion != CardioConversion.Concept2 ||
                (bodyWeightKg != null && bodyWeightKg > 0.0 && durationMinutes > 0),
    )
}

/** kcal of one strength set. Per-repetition values only apply to exercises counted in reps. */
fun strengthSetKcal(exercise: WorkoutExercise, reps: Int?): Double =
    when {
        exercise.kcalMode == StrengthKcalMode.PerRep && exercise.measure == StrengthMeasure.Reps ->
            exercise.kcalValue.coerceAtLeast(0.0) * (reps ?: 0).coerceAtLeast(0)
        else -> exercise.kcalValue.coerceAtLeast(0.0)
    }

/**
 * Start minute for an entry logged "now": a cardio session that just ended started [durationMinutes]
 * earlier, clamped to midnight.
 */
fun startMinuteForNow(nowMinuteOfDay: Int, durationMinutes: Int?): Int =
    (nowMinuteOfDay - (durationMinutes ?: 0).coerceAtLeast(0)).coerceIn(0, MINUTES_PER_DAY - 1)

/** Minutes during which Health Connect steps should not count, or null when nothing is excluded. */
fun WorkoutEntry.stepExclusionWindow(exercise: WorkoutExercise): IntRange? {
    if (exercise.kind != WorkoutKind.Cardio || !exercise.excludeSteps) return null
    val duration = durationMinutes ?: return null
    if (duration <= 0) return null
    val start = minuteOfDay.coerceIn(0, MINUTES_PER_DAY - 1)
    val end = (start + duration).coerceAtMost(MINUTES_PER_DAY)
    return start until end
}

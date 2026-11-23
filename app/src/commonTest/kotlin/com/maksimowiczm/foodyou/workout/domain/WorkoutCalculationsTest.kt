package com.maksimowiczm.foodyou.workout.domain

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

class WorkoutCalculationsTest {
    private val created = Instant.fromEpochSeconds(0)

    private fun exercise(
        kind: WorkoutKind = WorkoutKind.Cardio,
        conversion: CardioConversion = CardioConversion.Device,
        factor: Double = 1.0,
        discount: Double = 0.0,
        measure: StrengthMeasure = StrengthMeasure.Reps,
        kcalMode: StrengthKcalMode = StrengthKcalMode.PerSet,
        kcalValue: Double = 4.0,
        excludeSteps: Boolean = true,
    ) =
        WorkoutExercise(
            id = WorkoutExerciseId(1),
            name = "Test",
            kind = kind,
            measure = measure,
            kcalMode = kcalMode,
            kcalValue = kcalValue,
            cardioConversion = conversion,
            cardioFactor = factor,
            cardioDiscountPercent = discount,
            excludeSteps = excludeSteps,
            createdAt = created,
        )

    private fun assertClose(expected: Double, actual: Double, tolerance: Double = 0.5) =
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected but was $actual")

    @Test
    fun concept2KeepsTheMonitorValueForTheReferenceWeight() {
        val kg = 175 / KILOGRAMS_TO_POUNDS
        assertClose(260.0, concept2AdjustedKcal(260.0, 20, kg), tolerance = 0.05)
    }

    @Test
    fun concept2AdjustsForAHeavierRower() {
        // 780 kcal/h − 300 + 1.714 × 181.66 lb ≈ 791.4 kcal/h for 20 minutes.
        assertClose(263.8, concept2AdjustedKcal(260.0, 20, 82.4), tolerance = 0.2)
    }

    @Test
    fun concept2FallsBackToTheMonitorValueWithoutDurationOrWeight() {
        assertEquals(260.0, concept2AdjustedKcal(260.0, 0, 82.4))
        assertEquals(260.0, concept2AdjustedKcal(260.0, 20, null))
    }

    @Test
    fun concept2NeverGoesBelowZero() {
        assertEquals(0.0, concept2AdjustedKcal(10.0, 60, 30.0))
    }

    @Test
    fun cardioEnergyAppliesConversionThenDiscount() {
        val rowing = exercise(conversion = CardioConversion.Concept2, discount = 10.0)
        val result = cardioEnergy(rowing, 260.0, 20, 82.4)
        assertClose(263.8, result.convertedKcal, tolerance = 0.2)
        assertClose(237.4, result.finalKcal, tolerance = 0.2)
        assertTrue(result.usedBodyWeight)

        val crosstrainer = exercise(discount = 15.0)
        assertClose(68.0, cardioEnergy(crosstrainer, 80.0, 10, null).finalKcal, tolerance = 0.01)

        val factor = exercise(conversion = CardioConversion.Factor, factor = 0.85)
        assertClose(85.0, cardioEnergy(factor, 100.0, 10, null).finalKcal, tolerance = 0.01)
    }

    @Test
    fun cardioEnergyReportsMissingWeightForConcept2() {
        val rowing = exercise(conversion = CardioConversion.Concept2)
        assertFalse(cardioEnergy(rowing, 260.0, 20, null).usedBodyWeight)
    }

    @Test
    fun strengthKcalPerSetOrPerRep() {
        assertEquals(4.0, strengthSetKcal(exercise(kind = WorkoutKind.Strength), 10))
        val perRep =
            exercise(kind = WorkoutKind.Strength, kcalMode = StrengthKcalMode.PerRep, kcalValue = 0.4)
        assertClose(4.0, strengthSetKcal(perRep, 10), tolerance = 0.0001)
        val seconds =
            exercise(
                kind = WorkoutKind.Strength,
                measure = StrengthMeasure.Seconds,
                kcalMode = StrengthKcalMode.PerRep,
                kcalValue = 2.0,
            )
        assertEquals(2.0, strengthSetKcal(seconds, null))
    }

    @Test
    fun startMinuteForNowSubtractsTheDurationAndClampsAtMidnight() {
        assertEquals(965, startMinuteForNow(985, 20))
        assertEquals(0, startMinuteForNow(5, 20))
        assertEquals(985, startMinuteForNow(985, null))
    }

    @Test
    fun stepExclusionWindowOnlyForCardioThatExcludesSteps() {
        val entry =
            WorkoutEntry(
                id = WorkoutEntryId(1),
                exerciseId = WorkoutExerciseId(1),
                date = LocalDate(2026, 11, 26),
                minuteOfDay = 1430,
                durationMinutes = 20,
                energyKcal = 100.0,
                createdAt = created,
                updatedAt = created,
            )
        assertEquals(1430 until 1440, entry.stepExclusionWindow(exercise()))
        assertNull(entry.stepExclusionWindow(exercise(excludeSteps = false)))
        assertNull(entry.stepExclusionWindow(exercise(kind = WorkoutKind.Strength)))
    }
}

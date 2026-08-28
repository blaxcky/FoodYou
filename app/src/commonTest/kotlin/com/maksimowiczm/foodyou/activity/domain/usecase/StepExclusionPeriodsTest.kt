package com.maksimowiczm.foodyou.activity.domain.usecase

import com.maksimowiczm.foodyou.activity.domain.entity.StepExclusionPeriod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlinx.datetime.LocalDate

class StepExclusionPeriodsTest {
    private val date = LocalDate(2026, 8, 28)

    @Test
    fun sortsAndMergesOverlappingAndAdjacentPeriods() {
        val merged =
            mergeStepExclusionPeriods(
                listOf(period(600, 660), period(480, 540), period(535, 600), period(700, 720))
            )

        assertEquals(listOf(period(480, 660), period(700, 720)), merged)
    }

    @Test
    fun fullyContainedPeriodDoesNotExtendMergedPeriod() {
        assertEquals(
            listOf(period(480, 720)),
            mergeStepExclusionPeriods(listOf(period(480, 720), period(540, 600))),
        )
    }

    @Test
    fun rejectsInvalidAndCrossDayPeriods() {
        assertFalse(isValidStepExclusionPeriod(period(600, 600)))
        assertFalse(isValidStepExclusionPeriod(period(-1, 60)))
        assertFalse(isValidStepExclusionPeriod(period(60, 1441)))
        assertFailsWith<IllegalArgumentException> {
            mergeStepExclusionPeriods(listOf(period(600, 600)))
        }
    }

    @Test
    fun excludedStepSumIsNonNegativeAndBoundedByRawTotal() {
        assertEquals(1_000, boundedExcludedSteps(1_000, listOf(700, 600)))
        assertEquals(800, boundedExcludedSteps(1_000, listOf(800, -100)))
        assertEquals(0, boundedExcludedSteps(-1, listOf(20)))
    }

    private fun period(start: Int, end: Int) = StepExclusionPeriod(date, start, end)
}

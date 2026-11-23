package com.maksimowiczm.foodyou.app.ui.home.goals

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate

class WeekDietOutlookTest {
    private val thursday = LocalDate(2026, 11, 19)

    private fun day(date: LocalDate, consumedEnergyKcal: Double, lockedSurplusKcal: Double? = null) =
        GoalEnergyOptimizationDay(
            date = date,
            consumedEnergyKcal = consumedEnergyKcal,
            baseEnergyGoalKcal = 2_300.0,
            burnedEnergyKcal = 0.0,
            dietEnergyDeficitKcal = 550.0,
            lockedSurplusKcal = lockedSurplusKcal,
        )

    // Mon +900, Tue -400 and Wed +1000 against the normal goal; Thursday is today.
    private val weekUntilThursday =
        listOf(
            day(LocalDate(2026, 11, 16), 3_200.0),
            day(LocalDate(2026, 11, 17), 1_900.0),
            day(LocalDate(2026, 11, 18), 3_300.0),
            day(thursday, 600.0),
        )

    @Test
    fun bestCaseAssumesTheCappedDietGoalOnEveryRemainingDay() {
        val outlook =
            weekDietOutlook(
                today = thursday,
                weekDays = weekUntilThursday,
                plannedFutureDays = emptyList(),
                dietEnergyDeficitKcal = 550.0,
                maxEnergyDeficitKcal = 800.0,
            )

        // +1500 so far, Thu to Sun at most 800 below normal: -1700. The diet target is -3850.
        assertEquals(WeekDietOutlook(bestCaseKcal = -1_700, overDietGoalKcal = 2_150), outlook)
    }

    @Test
    fun aLockedFutureDayCountsWithItsSurplusInsteadOfTheCap() {
        val outlook =
            weekDietOutlook(
                today = thursday,
                weekDays = weekUntilThursday,
                plannedFutureDays =
                    listOf(day(LocalDate(2026, 11, 21), 0.0, lockedSurplusKcal = 500.0)),
                dietEnergyDeficitKcal = 550.0,
                maxEnergyDeficitKcal = 800.0,
            )

        // +1500 so far, Saturday +500, Thu, Fri and Sun at most 800 below normal: -400.
        assertEquals(-400, outlook?.bestCaseKcal)
        assertEquals(3_450, outlook?.overDietGoalKcal)
    }

    @Test
    fun todayAboveTheCappedGoalCountsWithItsIntake() {
        val outlook =
            weekDietOutlook(
                today = thursday,
                weekDays = weekUntilThursday.dropLast(1) + day(thursday, 3_000.0),
                plannedFutureDays = emptyList(),
                dietEnergyDeficitKcal = 550.0,
                maxEnergyDeficitKcal = 800.0,
            )

        // +1500 so far, today +700 as eaten, Fri to Sun at most 800 below normal: -200.
        assertEquals(WeekDietOutlook(bestCaseKcal = -200, overDietGoalKcal = 3_650), outlook)
    }

    @Test
    fun overeatingTodayCanPutAReachableTargetOutOfReach() {
        val outlook =
            weekDietOutlook(
                today = thursday,
                weekDays =
                    listOf(
                        day(LocalDate(2026, 11, 16), 1_750.0),
                        day(LocalDate(2026, 11, 17), 2_300.0),
                        day(LocalDate(2026, 11, 18), 1_750.0),
                        day(thursday, 3_500.0),
                    ),
                plannedFutureDays = emptyList(),
                dietEnergyDeficitKcal = 550.0,
                maxEnergyDeficitKcal = 800.0,
            )

        // -1100 so far, today +1200, Fri to Sun at most 800 below normal: -2300.
        assertEquals(WeekDietOutlook(bestCaseKcal = -2_300, overDietGoalKcal = 1_550), outlook)
    }

    @Test
    fun noOutlookWhileTheDietTargetIsStillReachable() {
        val outlook =
            weekDietOutlook(
                today = thursday,
                weekDays =
                    listOf(
                        day(LocalDate(2026, 11, 16), 1_750.0),
                        day(LocalDate(2026, 11, 17), 2_300.0),
                        day(LocalDate(2026, 11, 18), 1_750.0),
                        day(thursday, 0.0),
                    ),
                plannedFutureDays = emptyList(),
                dietEnergyDeficitKcal = 550.0,
                maxEnergyDeficitKcal = 800.0,
            )

        assertNull(outlook)
    }

    @Test
    fun noOutlookWithoutTheCap() {
        val outlook =
            weekDietOutlook(
                today = thursday,
                weekDays = weekUntilThursday,
                plannedFutureDays = emptyList(),
                dietEnergyDeficitKcal = 550.0,
                maxEnergyDeficitKcal = null,
            )

        assertNull(outlook)
    }
}

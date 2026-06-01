package com.maksimowiczm.foodyou.app.ui.home.goals

import kotlin.test.Test
import kotlin.test.assertEquals

class GoalsCardGoalDisplayModeTest {

    @Test
    fun unavailableOptimizedIsSkippedWhenFindingNextMode() {
        val availableModes =
            goalDisplaySummaries(optimizedAvailable = false)
                .availableForGoalDisplayModes(dietGoalDisplayModeEnabled = true)
                .map { it.mode }

        assertEquals(
            GoalDisplayMode.Diet,
            GoalDisplayMode.Normal.goalDisplayModeAtOffset(
                availableGoalDisplayModes = availableModes,
                offset = 1,
            ),
        )
    }

    @Test
    fun unavailableOptimizedAndDietLeaveOnlyNormal() {
        val availableModes =
            goalDisplaySummaries(optimizedAvailable = false, dietAvailable = false)
                .availableForGoalDisplayModes(dietGoalDisplayModeEnabled = true)
                .map { it.mode }

        assertEquals(listOf(GoalDisplayMode.Normal), availableModes)
        assertEquals(
            GoalDisplayMode.Normal,
            GoalDisplayMode.Normal.goalDisplayModeAtOffset(
                availableGoalDisplayModes = availableModes,
                offset = 1,
            ),
        )
    }

    @Test
    fun unavailableStoredModeFallsBackToNormal() {
        val availableModes =
            goalDisplaySummaries(optimizedAvailable = false)
                .availableForGoalDisplayModes(dietGoalDisplayModeEnabled = true)
                .map { it.mode }

        assertEquals(
            GoalDisplayMode.Normal,
            GoalDisplayMode.Optimized.availableOrNormal(availableModes),
        )
    }

    @Test
    fun availableModesKeepExistingWraparoundOrder() {
        val availableModes =
            goalDisplaySummaries()
                .availableForGoalDisplayModes(dietGoalDisplayModeEnabled = true)
                .map { it.mode }

        assertEquals(
            GoalDisplayMode.Optimized,
            GoalDisplayMode.Normal.goalDisplayModeAtOffset(
                availableGoalDisplayModes = availableModes,
                offset = 1,
            ),
        )
        assertEquals(
            GoalDisplayMode.Diet,
            GoalDisplayMode.Normal.goalDisplayModeAtOffset(
                availableGoalDisplayModes = availableModes,
                offset = -1,
            ),
        )
    }

    @Test
    fun dietModeIsExcludedWhenDietModeIsDisabled() {
        val availableModes =
            goalDisplaySummaries()
                .availableForGoalDisplayModes(dietGoalDisplayModeEnabled = false)
                .map { it.mode }

        assertEquals(listOf(GoalDisplayMode.Normal, GoalDisplayMode.Optimized), availableModes)
    }

    private fun goalDisplaySummaries(
        optimizedAvailable: Boolean = true,
        dietAvailable: Boolean = true,
    ): List<GoalDisplaySummaryModel> =
        listOf(
            GoalDisplaySummaryModel(
                mode = GoalDisplayMode.Normal,
                energyGoal = 2100,
                showEnergyGoalValue = true,
            ),
            GoalDisplaySummaryModel(
                mode = GoalDisplayMode.Optimized,
                energyGoal = 2250,
                showEnergyGoalValue = optimizedAvailable,
            ),
            GoalDisplaySummaryModel(
                mode = GoalDisplayMode.Diet,
                energyGoal = 1800,
                showEnergyGoalValue = dietAvailable,
            ),
        )
}

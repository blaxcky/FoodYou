package com.maksimowiczm.foodyou.app.ui.home.goals

import com.maksimowiczm.foodyou.settings.domain.entity.GoalDisplayMode as SettingsGoalDisplayMode
import kotlin.test.Test
import kotlin.test.assertEquals

class GoalsCardGoalDisplayModeTest {

    @Test
    fun nonCurrentWeekWithDeficitOffersNormalAndDiet() {
        assertEquals(
            listOf(GoalDisplayMode.Normal, GoalDisplayMode.Diet),
            availableGoalDisplayModes(currentWeek = false, dietEnergyDeficitKcal = 550.0),
        )
    }

    @Test
    fun nonCurrentWeekKeepsStoredDietModeWhenDeficitApplies() {
        assertEquals(
            GoalDisplayMode.Diet,
            SettingsGoalDisplayMode.Diet.selectedGoalDisplayMode(
                currentWeek = false,
                dietEnergyDeficitKcal = 550.0,
            ),
        )
    }

    @Test
    fun nonCurrentWeekWithoutDeficitOffersOnlyNormal() {
        assertEquals(
            listOf(GoalDisplayMode.Normal),
            availableGoalDisplayModes(currentWeek = false, dietEnergyDeficitKcal = null),
        )
    }

    @Test
    fun currentWeekWithDeficitOffersAllModes() {
        assertEquals(
            listOf(GoalDisplayMode.Normal, GoalDisplayMode.Optimized, GoalDisplayMode.Diet),
            availableGoalDisplayModes(currentWeek = true, dietEnergyDeficitKcal = 550.0),
        )
    }

    @Test
    fun nonCurrentWeekFallsBackFromStoredOptimizedModeToNormal() {
        assertEquals(
            GoalDisplayMode.Normal,
            SettingsGoalDisplayMode.Optimized.selectedGoalDisplayMode(
                currentWeek = false,
                dietEnergyDeficitKcal = 550.0,
            ),
        )
    }

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

    @Test
    fun supplementalGoalsIncludeChangedOptimizationAndActiveDiet() {
        val summaries = goalDisplaySummaries(optimizedGoal = 2250)

        assertEquals(
            listOf(GoalDisplayMode.Optimized, GoalDisplayMode.Diet),
            summaries.supplementalGoalSummaries(dietGoalDisplayModeEnabled = true).map { it.mode },
        )
    }

    @Test
    fun supplementalGoalsIncludeOnlyTheActiveVariant() {
        val summaries = goalDisplaySummaries(optimizedGoal = 2100)

        assertEquals(
            listOf(GoalDisplayMode.Diet),
            summaries.supplementalGoalSummaries(dietGoalDisplayModeEnabled = true).map { it.mode },
        )
    }

    @Test
    fun supplementalGoalsAreEmptyWithoutChangedOptimizationOrEffectiveDiet() {
        val summaries = goalDisplaySummaries(optimizedGoal = 2100)

        assertEquals(
            emptyList(),
            summaries.supplementalGoalSummaries(dietGoalDisplayModeEnabled = false),
        )
    }

    private fun goalDisplaySummaries(
        optimizedAvailable: Boolean = true,
        dietAvailable: Boolean = true,
        optimizedGoal: Int = 2250,
    ): List<GoalDisplaySummaryModel> =
        listOf(
            GoalDisplaySummaryModel(
                mode = GoalDisplayMode.Normal,
                energyGoal = 2100,
                showEnergyGoalValue = true,
            ),
            GoalDisplaySummaryModel(
                mode = GoalDisplayMode.Optimized,
                energyGoal = optimizedGoal,
                showEnergyGoalValue = optimizedAvailable,
            ),
            GoalDisplaySummaryModel(
                mode = GoalDisplayMode.Diet,
                energyGoal = 1800,
                showEnergyGoalValue = dietAvailable,
            ),
        )
}

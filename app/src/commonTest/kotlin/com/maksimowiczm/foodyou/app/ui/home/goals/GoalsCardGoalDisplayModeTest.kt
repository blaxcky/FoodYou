package com.maksimowiczm.foodyou.app.ui.home.goals

import com.maksimowiczm.foodyou.settings.domain.entity.GoalDisplayMode as SettingsGoalDisplayMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    @Test
    fun ringGoalZonesAreOrderedAlongTheNormalRing() {
        val summaries = goalDisplaySummaries(optimizedGoal = 1935)

        val zones =
            gaugeGoalZones(
                ringSummary = summaries.first { it.mode == GoalDisplayMode.Normal },
                goalSummaries = summaries.supplementalGoalSummaries(dietGoalDisplayModeEnabled = true),
                netEnergy = 1900,
                markerGapDegrees = 20,
            )

        assertEquals(listOf(GoalDisplayMode.Diet, GoalDisplayMode.Optimized), zones.map { it.mode })
        assertEquals(listOf(1800f / 2100f, 1935f / 2100f), zones.map { it.goalFraction })
        // Optimized keeps its place; Diet, closer than the least gap to it, moves back.
        assertEquals(
            listOf(1935f / 2100f - gaugeMarkerGap(20), 1935f / 2100f),
            zones.map { it.fraction },
        )
        assertEquals(listOf(-100, 35), zones.map { it.remaining })
        assertEquals(listOf(true, false), zones.map { it.exceeded })
    }

    @Test
    fun ringGoalZonesLeaveOutGoalsBeyondTheRing() {
        val summaries = goalDisplaySummaries(optimizedGoal = 2250)

        val zones =
            gaugeGoalZones(
                ringSummary = summaries.first { it.mode == GoalDisplayMode.Normal },
                goalSummaries = summaries.supplementalGoalSummaries(dietGoalDisplayModeEnabled = true),
                netEnergy = 1200,
                markerGapDegrees = 20,
            )

        assertEquals(listOf(GoalDisplayMode.Diet), zones.map { it.mode })
    }

    @Test
    fun ringGoalZonesExcludeTheRingsOwnMode() {
        val summaries = goalDisplaySummaries(optimizedGoal = 1935)

        val zones =
            gaugeGoalZones(
                ringSummary = summaries.first { it.mode == GoalDisplayMode.Optimized },
                goalSummaries = summaries.supplementalGoalSummaries(dietGoalDisplayModeEnabled = true),
                netEnergy = 1200,
                markerGapDegrees = 20,
            )

        assertEquals(listOf(GoalDisplayMode.Diet), zones.map { it.mode })
        assertEquals(1800f / 1935f, zones.single().goalFraction)
        assertEquals(1f - gaugeMarkerGap(20), zones.single().fraction)
    }

    @Test
    fun ringGoalZoneCloseToTheRingsEndMovesBack() {
        val summaries = goalDisplaySummaries(optimizedGoal = 2090)

        val zones =
            gaugeGoalZones(
                ringSummary = summaries.first { it.mode == GoalDisplayMode.Normal },
                goalSummaries = summaries.supplementalGoalSummaries(dietGoalDisplayModeEnabled = false),
                netEnergy = 1200,
                markerGapDegrees = 20,
            )

        assertEquals(2090f / 2100f, zones.single().goalFraction)
        assertEquals(1f - gaugeMarkerGap(20), zones.single().fraction)
    }

    @Test
    fun gaugeMarkersFarEnoughApartStayWhereTheyAre() {
        assertEquals(listOf(0.3f, 0.6f), spreadGaugeMarkers(listOf(0.3f, 0.6f), 20))
        assertEquals(emptyList(), spreadGaugeMarkers(emptyList(), 20))
    }

    @Test
    fun crowdedGaugeMarkersKeepTheLeastGap() {
        val markers = spreadGaugeMarkers(listOf(0.95f, 0.96f), 20)

        assertEquals(1f - gaugeMarkerGap(20), markers[1], 1e-6f)
        assertEquals(1f - 2 * gaugeMarkerGap(20), markers[0], 1e-6f)
    }

    @Test
    fun gaugeMarkerGapFollowsTheSetting() {
        val goals = listOf(0.95f, 0.96f)

        assertEquals(goals, spreadGaugeMarkers(goals, 0))
        assertEquals(listOf(1f - 2 * 45f / 270f, 1f - 45f / 270f), spreadGaugeMarkers(goals, 45))
    }

    @Test
    fun ringFillPassesAMovedMarkerExactlyAtItsGoal() {
        val goals = listOf(0.95f)
        val markers = spreadGaugeMarkers(goals, 20)

        assertEquals(markers[0], gaugeRingPosition(0.95f, goals, markers), 1e-6f)
        assertTrue(gaugeRingPosition(0.94f, goals, markers) < markers[0])
        assertTrue(gaugeRingPosition(0.96f, goals, markers) > markers[0])
        assertEquals(markers[0] / 2f, gaugeRingPosition(0.475f, goals, markers), 1e-6f)
        assertEquals(0f, gaugeRingPosition(0f, goals, markers))
        assertEquals(1f, gaugeRingPosition(1f, goals, markers))
    }

    @Test
    fun ringFillHandlesGoalsOfTheSameValue() {
        val goals = listOf(0.5f, 0.5f)
        val markers = spreadGaugeMarkers(goals, 20)

        assertEquals(markers[0], gaugeRingPosition(0.5f, goals, markers), 1e-6f)
        assertTrue(gaugeRingPosition(0.6f, goals, markers) > markers[1])
    }

    @Test
    fun ringGoalZonesAreEmptyForANonPositiveRingGoal() {
        val zones =
            gaugeGoalZones(
                ringSummary =
                    GoalDisplaySummaryModel(
                        mode = GoalDisplayMode.Normal,
                        energyGoal = -300,
                        showEnergyGoalValue = true,
                    ),
                goalSummaries = goalDisplaySummaries(optimizedGoal = 1935),
                netEnergy = 0,
                markerGapDegrees = 20,
            )

        assertEquals(emptyList(), zones)
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

package com.maksimowiczm.foodyou.app.ui.home.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.locale
import com.github.takahirom.roborazzi.size
import com.maksimowiczm.foodyou.app.ui.common.utility.EnergyFormatter
import com.maksimowiczm.foodyou.app.ui.common.utility.EnergyFormatterProvider
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "de-rDE-w620dp-h388dp-mdpi")
@OptIn(ExperimentalRoborazziApi::class)
class GoalsCardScreenshotTest {

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun visibilityCombinations() {
        for (modeSwitching in listOf(true, false)) {
            for (supplemental in listOf(true, false)) {
                captureGoalsCard(
                    filePath = "GoalsCardScreenshotTest.visibility-$modeSwitching-$supplemental.png",
                    height = 500,
                    fixture = SupplementalFixture.copy(
                        goalDisplayMode = GoalDisplayMode.Diet,
                        goalCardModeSwitchingEnabled = modeSwitching,
                        supplementalGoalsEnabled = supplemental,
                    ),
                )
            }
        }
    }

    @Test
    fun settingsVisibilityCombinations() {
        for (modeSwitching in listOf(true, false)) {
            for (supplemental in listOf(true, false)) {
                captureRoboImage(
                    filePath = "GoalsCardScreenshotTest.settings-$modeSwitching-$supplemental.png",
                    roborazziComposeOptions = goalsCardOptions(width = 414, height = 850),
                ) {
                    EnergyFormatterProvider(EnergyFormatter.kilocalories) {
                        MaterialTheme {
                            GoalsCardSettingsContent(
                                onBack = {},
                                goalCardModeSwitchingEnabled = modeSwitching,
                                supplementalGoalsEnabled = supplemental,
                                onModeSwitchingChange = {},
                                onSupplementalGoalsChange = {},
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun reference() {
        captureGoalsCard("GoalsCardScreenshotTest.reference.png", ReferenceFixture)
    }

    @Test
    fun activeDay() {
        captureGoalsCard(
            filePath = "GoalsCardScreenshotTest.active-day.png",
            fixture =
                GoalsCardFixture(
                    energy = 1280,
                    burnedEnergy = 140,
                    netEnergy = 1140,
                    energyGoal = 2100,
                    proteins = 82,
                    proteinsGoal = 130,
                    carbohydrates = 146,
                    carbohydratesGoal = 230,
                    fats = 48,
                    fatsGoal = 70,
                ),
        )
    }

    @Test
    fun todayGoalAdjustment() {
        captureGoalsCard(
            filePath = "GoalsCardScreenshotTest.today-goal-adjustment.png",
            fixture =
                ReferenceFixture.copy(
                    energy = 1200,
                    burnedEnergy = 0,
                    netEnergy = 1200,
                    todayRemainingEnergy = 400,
                ),
        )
    }

    @Test
    fun todayGoalAdjustmentCompact() {
        captureGoalsCard(
            filePath = "GoalsCardScreenshotTest.today-goal-adjustment-compact.png",
            fixture =
                ReferenceFixture.copy(
                    energy = 1700,
                    burnedEnergy = 0,
                    netEnergy = 1700,
                    todayRemainingEnergy = -100,
                ),
            width = 320,
            height = 480,
        )
    }

    @Test
    fun almostDone() {
        captureGoalsCard(
            filePath = "GoalsCardScreenshotTest.almost-done.png",
            fixture =
                GoalsCardFixture(
                    energy = 1960,
                    burnedEnergy = 320,
                    netEnergy = 1640,
                    energyGoal = 1800,
                    proteins = 118,
                    proteinsGoal = 125,
                    carbohydrates = 205,
                    carbohydratesGoal = 220,
                    fats = 61,
                    fatsGoal = 65,
                ),
        )
    }

    @Test
    fun overGoal() {
        captureGoalsCard(
            filePath = "GoalsCardScreenshotTest.over-goal.png",
            fixture =
                GoalsCardFixture(
                    energy = 2380,
                    burnedEnergy = 160,
                    netEnergy = 2220,
                    energyGoal = 2100,
                    proteins = 152,
                    proteinsGoal = 130,
                    carbohydrates = 282,
                    carbohydratesGoal = 230,
                    fats = 89,
                    fatsGoal = 70,
                ),
        )
    }

    @Test
    fun phoneOverGoal() {
        captureGoalsCard(
            filePath = "GoalsCardScreenshotTest.phone-over-goal.png",
            fixture =
                GoalsCardFixture(
                    energy = 2380,
                    burnedEnergy = 160,
                    netEnergy = 2220,
                    energyGoal = 2100,
                    proteins = 152,
                    proteinsGoal = 130,
                    carbohydrates = 282,
                    carbohydratesGoal = 230,
                    fats = 89,
                    fatsGoal = 70,
                ),
            width = 414,
            height = 322,
        )
    }

    @Test
    fun weeklyChartLargeValueNarrowPhone() {
        captureRoboImage(
            filePath = "GoalsCardScreenshotTest.weekly-chart-large-value-narrow-phone.png",
            roborazziComposeOptions = goalsCardOptions(width = 320, height = 220),
        ) {
            WeeklyGoalsChartGolden()
        }
    }

    @Test
    fun weeklyLoading() {
        captureRoboImage(
            filePath = "GoalsCardScreenshotTest.weekly-loading.png",
            roborazziComposeOptions = goalsCardOptions(width = 414, height = 430),
        ) {
            WeeklyGoalsLoadingGolden()
        }
    }

    @Test
    fun weeklyLockedDay() {
        captureRoboImage(
            filePath = "GoalsCardScreenshotTest.weekly-locked-day.png",
            roborazziComposeOptions = goalsCardOptions(width = 414, height = 220),
        ) {
            EnergyFormatterProvider(EnergyFormatter.kilocalories) {
                MaterialTheme {
                    Box(Modifier.requiredSize(414.dp, 220.dp).background(Color.White).padding(16.dp)) {
                        WeeklyGoalsChart(
                            days =
                                listOf(
                                    WeekDaySummaryModel(
                                        date = LocalDate(2026, 7, 27),
                                        energy = 2_500,
                                        goal = 2_000,
                                        locked = true,
                                    ),
                                    WeekDaySummaryModel(
                                        date = LocalDate(2026, 7, 28),
                                        energy = 1_750,
                                        goal = 2_000,
                                    ),
                                ),
                            today = LocalDate(2026, 7, 28),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun burnedEnergyDelta() {
        captureGoalsCard(
            filePath = "GoalsCardScreenshotTest.burned-energy-delta.png",
            fixture =
                GoalsCardFixture(
                    energy = 1280,
                    burnedEnergy = 145,
                    burnedEnergyDelta = 5,
                    netEnergy = 1135,
                    energyGoal = 2100,
                    proteins = 82,
                    proteinsGoal = 130,
                    carbohydrates = 146,
                    carbohydratesGoal = 230,
                    fats = 48,
                    fatsGoal = 70,
                ),
        )
    }

    @Test
    fun normalModeActive() {
        captureGoalsCard(
            filePath = "GoalsCardScreenshotTest.mode-normal.png",
            fixture = SupplementalFixture.copy(goalDisplayMode = GoalDisplayMode.Normal),
            width = 414,
            height = 500,
        )
    }

    @Test
    fun optimizedModeActive() {
        captureGoalsCard(
            filePath = "GoalsCardScreenshotTest.mode-optimized.png",
            fixture = SupplementalFixture.copy(goalDisplayMode = GoalDisplayMode.Optimized),
            width = 414,
            height = 500,
        )
    }

    @Test
    fun dietModeActive() {
        captureGoalsCard(
            filePath = "GoalsCardScreenshotTest.mode-diet.png",
            fixture = SupplementalFixture.copy(goalDisplayMode = GoalDisplayMode.Diet),
            width = 414,
            height = 500,
        )
    }

    @Test
    fun dietModeDisabled() {
        captureGoalsCard(
            filePath = "GoalsCardScreenshotTest.mode-diet-disabled.png",
            fixture = ReferenceFixture.copy(dietGoalDisplayModeEnabled = false),
            width = 414,
            height = 322,
        )
    }

    @Test
    fun supplementalOneRow() {
        captureGoalsCard(
            filePath = "GoalsCardScreenshotTest.supplemental-one-row.png",
            fixture = SupplementalFixture.copy(dietGoalDisplayModeEnabled = false),
            width = 414,
            height = 430,
        )
    }

    @Test
    fun supplementalOverflow() {
        captureGoalsCard(
            filePath = "GoalsCardScreenshotTest.supplemental-overflow.png",
            fixture =
                SupplementalFixture.copy(
                    energy = 2450,
                    burnedEnergy = 150,
                    netEnergy = 2300,
                ),
            width = 414,
            height = 500,
        )
    }

    @Test
    fun supplementalLargeFont() {
        captureGoalsCard(
            filePath = "GoalsCardScreenshotTest.supplemental-large-font.png",
            fixture = SupplementalFixture,
            width = 414,
            height = 520,
            fontScale = 1.25f,
        )
    }

    @Test
    fun phoneBurnedEnergyDelta() {
        captureGoalsCard(
            filePath = "GoalsCardScreenshotTest.phone-burned-energy-delta.png",
            fixture =
                GoalsCardFixture(
                    energy = 1280,
                    burnedEnergy = 333,
                    burnedEnergyDelta = 333,
                    netEnergy = 947,
                    energyGoal = 2100,
                    proteins = 82,
                    proteinsGoal = 130,
                    carbohydrates = 146,
                    carbohydratesGoal = 230,
                    fats = 48,
                    fatsGoal = 70,
                ),
            width = 414,
            height = 322,
        )
    }

    private fun captureGoalsCard(
        filePath: String,
        fixture: GoalsCardFixture,
        width: Int = 620,
        height: Int = 430,
        fontScale: Float = 1f,
    ) {
        captureRoboImage(
            filePath = filePath,
            roborazziComposeOptions = goalsCardOptions(width = width, height = height),
        ) {
            GoalsCardGolden(
                fixture = fixture,
                width = width,
                height = height,
                fontScale = fontScale,
            )
        }
    }

    @Composable
    private fun GoalsCardGolden(
        fixture: GoalsCardFixture,
        width: Int,
        height: Int,
        fontScale: Float,
    ) {
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density = density.density, fontScale = fontScale)
        ) {
            EnergyFormatterProvider(EnergyFormatter.kilocalories) {
                MaterialTheme {
                    Box(
                        modifier =
                            Modifier.requiredSize(width = width.dp, height = height.dp)
                                .background(Color(0xFFEEF5FA))
                    ) {
                        GoalsCard(
                            energy = fixture.energy,
                            burnedEnergy = fixture.burnedEnergy,
                            burnedEnergyDelta = fixture.burnedEnergyDelta,
                            netEnergy = fixture.netEnergy,
                            energyGoal = fixture.energyGoal,
                            goalDisplayMode = fixture.goalDisplayMode,
                            goalDisplaySummaries = fixture.goalDisplaySummaries,
                            goalCardModeSwitchingEnabled = fixture.goalCardModeSwitchingEnabled,
                            supplementalGoalsEnabled = fixture.supplementalGoalsEnabled,
                            dietGoalDisplayModeEnabled = fixture.dietGoalDisplayModeEnabled,
                            proteins = fixture.proteins,
                            proteinsGoal = fixture.proteinsGoal,
                            carbohydrates = fixture.carbohydrates,
                            carbohydratesGoal = fixture.carbohydratesGoal,
                            fats = fixture.fats,
                            fatsGoal = fixture.fatsGoal,
                            todayRemainingEnergy = fixture.todayRemainingEnergy,
                            onClick = {},
                            onLongClick = {},
                            modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun WeeklyGoalsChartGolden() {
        MaterialTheme {
            Box(
                modifier =
                    Modifier.requiredSize(width = 320.dp, height = 220.dp)
                        .background(Color(0xFFEEF5FA))
                        .padding(horizontal = 12.dp, vertical = 18.dp)
            ) {
                WeeklyGoalsChart(
                    days = WeeklyChartLargeValueFixture,
                    today = WeeklyChartToday,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    @Composable
    private fun WeeklyGoalsLoadingGolden() {
        MaterialTheme {
            Box(
                modifier =
                    Modifier.requiredSize(width = 414.dp, height = 430.dp)
                        .background(Color(0xFFEEF5FA))
                        .padding(horizontal = 12.dp, vertical = 18.dp)
            ) {
                WeeklyGoalsSkeleton(
                    modifier = Modifier.fillMaxWidth(),
                    shimmerEnabled = false,
                )
            }
        }
    }

    private data class GoalsCardFixture(
        val energy: Int,
        val burnedEnergy: Int,
        val burnedEnergyDelta: Int? = null,
        val netEnergy: Int,
        val energyGoal: Int,
        val goalDisplayMode: GoalDisplayMode = GoalDisplayMode.Normal,
        val goalDisplaySummaries: List<GoalDisplaySummaryModel> =
            defaultGoalDisplaySummaries(energyGoal),
        val goalCardModeSwitchingEnabled: Boolean = true,
        val supplementalGoalsEnabled: Boolean = true,
        val dietGoalDisplayModeEnabled: Boolean = true,
        val proteins: Int,
        val proteinsGoal: Int,
        val carbohydrates: Int,
        val carbohydratesGoal: Int,
        val fats: Int,
        val fatsGoal: Int,
        val todayRemainingEnergy: Int? = null,
    )

    private companion object {
        fun goalsCardOptions(width: Int, height: Int): RoborazziComposeOptions =
            RoborazziComposeOptions.Builder().size(widthDp = width, heightDp = height)
                .locale("de-rDE")
                .build()

        val ReferenceFixture =
            GoalsCardFixture(
                energy = 0,
                burnedEnergy = 1,
                netEnergy = -1,
                energyGoal = 2100,
                proteins = 0,
                proteinsGoal = 231,
                carbohydrates = 0,
                carbohydratesGoal = 128,
                fats = 0,
                fatsGoal = 68,
            )

        val SupplementalFixture =
            ReferenceFixture.copy(
                energy = 1300,
                burnedEnergy = 100,
                netEnergy = 1200,
                goalDisplaySummaries =
                    listOf(
                        GoalDisplaySummaryModel(
                            mode = GoalDisplayMode.Normal,
                            energyGoal = 2100,
                            showEnergyGoalValue = true,
                        ),
                        GoalDisplaySummaryModel(
                            mode = GoalDisplayMode.Optimized,
                            energyGoal = 2250,
                            showEnergyGoalValue = true,
                        ),
                        GoalDisplaySummaryModel(
                            mode = GoalDisplayMode.Diet,
                            energyGoal = 1800,
                            showEnergyGoalValue = true,
                        ),
                    ),
            )

        val WeeklyChartToday = LocalDate.parse("2026-07-01")

        val WeeklyChartLargeValueFixture =
            listOf(
                WeekDaySummaryModel(
                    date = LocalDate.parse("2026-06-29"),
                    energy = 1800,
                    goal = 2100,
                ),
                WeekDaySummaryModel(
                    date = LocalDate.parse("2026-06-30"),
                    energy = 2400,
                    goal = 2100,
                ),
                WeekDaySummaryModel(
                    date = WeeklyChartToday,
                    energy = 5000,
                    goal = 2100,
                ),
                WeekDaySummaryModel(
                    date = LocalDate.parse("2026-07-02"),
                    energy = 0,
                    goal = 2100,
                ),
                WeekDaySummaryModel(
                    date = LocalDate.parse("2026-07-03"),
                    energy = 2100,
                    goal = 2100,
                ),
                WeekDaySummaryModel(
                    date = LocalDate.parse("2026-07-04"),
                    energy = 1650,
                    goal = 2100,
                ),
                WeekDaySummaryModel(
                    date = LocalDate.parse("2026-07-05"),
                    energy = 1950,
                    goal = 2100,
                ),
            )

        fun defaultGoalDisplaySummaries(energyGoal: Int): List<GoalDisplaySummaryModel> =
            listOf(
                GoalDisplaySummaryModel(
                    mode = GoalDisplayMode.Normal,
                    energyGoal = energyGoal,
                    showEnergyGoalValue = true,
                ),
                GoalDisplaySummaryModel(
                    mode = GoalDisplayMode.Optimized,
                    energyGoal = energyGoal,
                    showEnergyGoalValue = true,
                ),
                GoalDisplaySummaryModel(
                    mode = GoalDisplayMode.Diet,
                    energyGoal = energyGoal,
                    showEnergyGoalValue = true,
                ),
            )
    }
}

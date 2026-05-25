package com.maksimowiczm.foodyou.app.ui.home.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.locale
import com.github.takahirom.roborazzi.size
import com.maksimowiczm.foodyou.app.ui.common.utility.EnergyFormatter
import com.maksimowiczm.foodyou.app.ui.common.utility.EnergyFormatterProvider
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
            fixture = ReferenceFixture.copy(goalDisplayMode = GoalDisplayMode.Normal),
            width = 414,
            height = 322,
        )
    }

    @Test
    fun optimizedModeActive() {
        captureGoalsCard(
            filePath = "GoalsCardScreenshotTest.mode-optimized.png",
            fixture = ReferenceFixture.copy(goalDisplayMode = GoalDisplayMode.Optimized),
            width = 414,
            height = 322,
        )
    }

    @Test
    fun dietModeActive() {
        captureGoalsCard(
            filePath = "GoalsCardScreenshotTest.mode-diet.png",
            fixture = ReferenceFixture.copy(goalDisplayMode = GoalDisplayMode.Diet),
            width = 414,
            height = 322,
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
    ) {
        captureRoboImage(
            filePath = filePath,
            roborazziComposeOptions = goalsCardOptions(width = width, height = height),
        ) {
            GoalsCardGolden(fixture = fixture, width = width, height = height)
        }
    }

    @Composable
    private fun GoalsCardGolden(fixture: GoalsCardFixture, width: Int, height: Int) {
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
                        dietGoalDisplayModeEnabled = fixture.dietGoalDisplayModeEnabled,
                        proteins = fixture.proteins,
                        proteinsGoal = fixture.proteinsGoal,
                        carbohydrates = fixture.carbohydrates,
                        carbohydratesGoal = fixture.carbohydratesGoal,
                        fats = fixture.fats,
                        fatsGoal = fixture.fatsGoal,
                        onClick = {},
                        onLongClick = {},
                        modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth(),
                    )
                }
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
        val dietGoalDisplayModeEnabled: Boolean = true,
        val proteins: Int,
        val proteinsGoal: Int,
        val carbohydrates: Int,
        val carbohydratesGoal: Int,
        val fats: Int,
        val fatsGoal: Int,
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
    }
}

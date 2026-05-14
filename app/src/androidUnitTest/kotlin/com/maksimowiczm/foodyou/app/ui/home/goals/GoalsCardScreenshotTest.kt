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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "de-rDE-w620dp-h388dp-mdpi")
@OptIn(ExperimentalRoborazziApi::class)
class GoalsCardScreenshotTest {

    @Test
    fun reference() {
        captureRoboImage(
            filePath = "GoalsCardScreenshotTest.reference.png",
            roborazziComposeOptions =
                RoborazziComposeOptions.Builder()
                    .size(widthDp = 620, heightDp = 388)
                    .locale("de-rDE")
                    .build(),
        ) {
            GoalsCardGolden()
        }
    }

    @Composable
    private fun GoalsCardGolden() {
        EnergyFormatterProvider(EnergyFormatter.kilocalories) {
            MaterialTheme {
                Box(
                    modifier =
                        Modifier.requiredSize(width = 620.dp, height = 388.dp)
                            .background(Color(0xFFEEF5FA))
                ) {
                    GoalsCard(
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
                        onClick = {},
                        onLongClick = {},
                        modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth(),
                    )
                }
            }
        }
    }
}

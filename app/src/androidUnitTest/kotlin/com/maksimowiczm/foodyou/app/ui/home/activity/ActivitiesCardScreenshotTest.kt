package com.maksimowiczm.foodyou.app.ui.home.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
@Config(sdk = [35], qualifiers = "de-rDE-w390dp-h420dp-mdpi")
@OptIn(ExperimentalRoborazziApi::class)
class ActivitiesCardScreenshotTest {
    @After fun tearDown() = stopKoin()

    @Test
    fun countedAndExcludedSteps() {
        captureRoboImage(
            filePath = "ActivitiesCardScreenshotTest.step-exclusions.png",
            roborazziComposeOptions =
                RoborazziComposeOptions.Builder().size(390, 420).locale("de-rDE").build(),
        ) {
            ActivitiesCardGolden()
        }
    }

    @Composable
    private fun ActivitiesCardGolden() {
        MaterialTheme {
            EnergyFormatterProvider(EnergyFormatter.kilocalories) {
                Box(
                    Modifier.requiredSize(390.dp, 420.dp)
                        .background(Color(0xFFEEF5FA))
                        .padding(horizontal = 8.dp, vertical = 24.dp)
                ) {
                    ActivitiesCardContent(
                        model =
                            ActivitiesCardModel(
                                countedSteps = 8_436,
                                excludedSteps = 1_284,
                                healthConnectStepsEnabled = true,
                                stepEnergyKcal = 34,
                                manualEnergyKcal = 0,
                                totalEnergyKcal = 34,
                                manualEntries = emptyList(),
                            ),
                        onAdd = {},
                        onEdit = {},
                        onStepExclusions = {},
                        onLongClick = {},
                    )
                }
            }
        }
    }
}

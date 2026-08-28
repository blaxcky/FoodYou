package com.maksimowiczm.foodyou.app.ui.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import com.maksimowiczm.foodyou.activity.domain.entity.StepExclusionPeriod
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "de-rDE-w390dp-h844dp-mdpi")
@OptIn(ExperimentalRoborazziApi::class)
class StepExclusionsScreenScreenshotTest {
    private val date = LocalDate(2026, 8, 28)

    @After fun tearDown() = stopKoin()

    @Test fun empty() = capture("StepExclusionsScreenScreenshotTest.empty.png", emptyList())

    @Test
    fun filled() =
        capture(
            "StepExclusionsScreenScreenshotTest.filled.png",
            listOf(
                StepExclusionPeriod(date, 7 * 60 + 45, 8 * 60 + 30),
                StepExclusionPeriod(date, 17 * 60 + 20, 18 * 60 + 15),
            ),
        )

    private fun capture(filePath: String, periods: List<StepExclusionPeriod>) {
        captureRoboImage(
            filePath = filePath,
            roborazziComposeOptions =
                RoborazziComposeOptions.Builder().size(390, 844).locale("de-rDE").build(),
        ) {
            Golden(periods)
        }
    }

    @Composable
    private fun Golden(periods: List<StepExclusionPeriod>) {
        MaterialTheme {
            Box(
                Modifier.requiredSize(390.dp, 844.dp).background(Color(0xFFEEF5FA))
            ) {
                StepExclusionsContent(
                    state =
                        StepExclusionsUiState(
                            date = date,
                            rawSteps = 9_720,
                            excludedSteps = 1_284,
                            countedSteps = 8_436,
                            periods = periods,
                            isLoading = false,
                            hasUnsavedChanges = periods.isNotEmpty(),
                        ),
                    onBack = {},
                    onAdd = {},
                    onUpdate = { _, _, _ -> },
                    onDelete = {},
                    onSave = {},
                    onRetry = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

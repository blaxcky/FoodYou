package com.maksimowiczm.foodyou.app.ui.weight

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
import com.maksimowiczm.foodyou.weight.domain.entity.DailyWeightEntry
import kotlin.time.Instant
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
class WeightReportScreenScreenshotTest {

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun weightEntryAndHistory() {
        captureRoboImage(
            filePath = "WeightReportScreenScreenshotTest.weight-entry-history.png",
            roborazziComposeOptions =
                RoborazziComposeOptions.Builder().size(widthDp = 390, heightDp = 844)
                    .locale("de-rDE")
                    .build(),
        ) {
            WeightReportGolden()
        }
    }

    @Composable
    private fun WeightReportGolden() {
        MaterialTheme {
            Box(
                modifier =
                    Modifier.requiredSize(width = 390.dp, height = 844.dp)
                        .background(Color(0xFFEEF5FA))
            ) {
                WeightReportContent(
                    state =
                        WeightReportUiState(
                            entries = WeightEntries,
                            chartEntries = ChartEntries,
                            todayWeightKg = 101.3,
                            suggestedWeightKg = 101.3,
                            startWeightKg = 105.0,
                            currentWeightKg = 101.3,
                            targetWeightKg = 90.0,
                            heightCm = 188.0,
                        ),
                    onBack = {},
                    onMinus = {},
                    onPlus = {},
                    onHealthConnectClick = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    private companion object {
        val WeightEntries =
            listOf(
                weight("2026-06-07", 101.3, "2026-06-07T06:30:00Z"),
                weight("2025-08-08", 104.4, "2025-08-08T06:35:00Z"),
                weight("2025-08-07", 105.0, "2025-08-07T06:20:00Z"),
            )

        val ChartEntries =
            listOf(
                weight("2025-08-07", 105.0, "2025-08-07T06:20:00Z"),
                weight("2025-10-20", 103.7, "2025-10-20T06:25:00Z"),
                weight("2025-12-12", 102.8, "2025-12-12T06:25:00Z"),
                weight("2026-02-18", 102.1, "2026-02-18T06:25:00Z"),
                weight("2026-04-16", 101.8, "2026-04-16T06:25:00Z"),
                weight("2026-06-07", 101.3, "2026-06-07T06:30:00Z"),
            )

        fun weight(date: String, weightKg: Double, measuredAt: String) =
            DailyWeightEntry(
                date = LocalDate.parse(date),
                weightKg = weightKg,
                measuredAt = Instant.parse(measuredAt),
                healthConnectRecordId = null,
                isFoodYouRecord = true,
            )
    }
}

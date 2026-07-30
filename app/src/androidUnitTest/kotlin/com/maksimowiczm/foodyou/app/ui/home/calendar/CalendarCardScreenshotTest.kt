package com.maksimowiczm.foodyou.app.ui.home.calendar

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
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.koin.core.context.stopKoin

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "de-rDE-w414dp-h480dp-hdpi")
@OptIn(ExperimentalRoborazziApi::class)
class CalendarCardScreenshotTest {
    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun lockedDayMarker() {
        captureRoboImage(
            filePath = "CalendarCardScreenshotTest.locked-day-marker.png",
            roborazziComposeOptions = screenshotOptions(height = 150),
        ) {
            GoldenSurface(height = 150) {
                val date = LocalDate(2026, 7, 30)
                LockedCalendarDatePreview(date = date, modifier = Modifier.padding(8.dp))
            }
        }
    }

    @Test
    fun lockedDayDialog() {
        captureRoboImage(
            filePath = "CalendarCardScreenshotTest.locked-day-dialog.png",
            roborazziComposeOptions = screenshotOptions(height = 480),
        ) {
            GoldenSurface(height = 480) {
                LockedDayDialog(
                    date = LocalDate(2026, 7, 30),
                    currentSurplusKcal = 500.0,
                    defaultSurplusKcal = 500.0,
                    onDismiss = {},
                    onSave = {},
                    onUnlock = {},
                )
            }
        }
    }

    @Composable
    private fun GoldenSurface(height: Int, content: @Composable () -> Unit) {
        EnergyFormatterProvider(EnergyFormatter.kilocalories) {
            MaterialTheme {
                Box(
                    Modifier.requiredSize(414.dp, height.dp).background(Color(0xFFEEF5FA))
                ) {
                    content()
                }
            }
        }
    }

    private fun screenshotOptions(height: Int) =
        RoborazziComposeOptions.Builder().size(widthDp = 414, heightDp = height)
            .locale("de-rDE")
            .build()
}

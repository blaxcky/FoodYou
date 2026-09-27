package com.maksimowiczm.foodyou.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import com.maksimowiczm.foodyou.settings.domain.entity.FddbProductSyncManualFrequency
import com.maksimowiczm.foodyou.settings.domain.entity.FddbProductSyncMode
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
class SynchronizationSettingsScreenScreenshotTest {
    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun manualFddbProductSyncPolicy() {
        captureRoboImage(
            filePath = "SynchronizationSettingsScreenScreenshotTest.manual-policy.png",
            roborazziComposeOptions =
                RoborazziComposeOptions.Builder().size(390, 844).locale("de-rDE").build(),
        ) {
            Golden()
        }
    }

    @Composable
    private fun Golden() {
        MaterialTheme {
            Box(
                modifier =
                    Modifier.requiredSize(390.dp, 844.dp)
                        .background(Color(0xFFF9F9FF))
            ) {
                FddbProductSyncPolicyDialog(
                    mode = FddbProductSyncMode.WithManualFddbSync,
                    frequency = FddbProductSyncManualFrequency.EveryThirdSync,
                    onDismiss = {},
                    onSave = { _, _ -> },
                )
            }
        }
    }
}

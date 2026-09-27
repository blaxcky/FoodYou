package com.maksimowiczm.foodyou.ai

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "de-rDE-w390dp-h844dp-mdpi", application = Application::class)
class AiSettingsScreenshotTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ActivityController<ComponentActivity>
    @After fun tearDown() { if (::activity.isInitialized) activity.close() }

    @Test fun localDownload() = showSettings(AiSettings(), ModelDownloadState(bytes = 900_000_000, running = true), "local-download")
    @Test fun localReady() = showSettings(AiSettings(), ModelDownloadState(bytes = GEMMA_SIZE, ready = true), "local-ready")
    @Test fun geminiSettings() = showSettings(AiSettings(AiProvider.Gemini, hasApiKey = true), ModelDownloadState(), "gemini")

    @Test fun batchProgress() {
        show {
            Column {
                AiAnalysisControls(AiProvider.Local, AnalysisProgress(true, 7, 10, 6), true, true, {}, {}, {}, {})
            }
        }
        compose.onRoot().captureRoboImage("AiSettingsScreenshotTest.batch-progress.png")
    }

    private fun showSettings(settings: AiSettings, download: ModelDownloadState, name: String) {
        show { AiSettingsContent(settings, download, false, null, {}, { _, _, _, _ -> }, {}, {}, {}, {}) }
        compose.onRoot().captureRoboImage("AiSettingsScreenshotTest.$name.png")
    }
    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        activity.get().setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { content() } } }
        compose.waitForIdle()
    }
}

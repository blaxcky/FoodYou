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
    @Test fun localReady() = showSettings(
        AiSettings(), ModelDownloadState(bytes = GemmaModel.E4B.size, ready = true), "local-ready",
    )
    @Test fun localE2BReady() = showSettings(
        AiSettings(AiProvider.LocalE2B),
        ModelDownloadState(bytes = GemmaModel.E2B.size, total = GemmaModel.E2B.size, ready = true),
        "local-e2b-ready",
    )
    @Test fun geminiSettings() = showSettings(AiSettings(AiProvider.Gemini, hasApiKey = true), ModelDownloadState(), "gemini")

    @Test fun diagnosticReport() {
        val report = """
            FoodYou KI-Diagnose · 3.4.8
            100 pid=7 Durchlauf=42; Modellladen=4.200 s; Status=Completed
            110 pid=7 Durchlauf=42; Foto 1: Gesamt=20.000 s; Erste Antwort=14.500 s; Status=Completed
            120 pid=7 Durchlauf=42; Foto 2: Gesamt=2.400 s; Erste Antwort=1.800 s; Status=Error
            119 pid=7 phase=result_ResponseFormat availableMiB=3046 lowMemory=false
        """.trimIndent()
        show { AiDiagnosticDialog(report, {}, {}) }
        compose.onNodeWithText("Bericht kopieren").assertExists()
        compose.onNodeWithText("KI-Diagnose").assertExists()
        compose.onNodeWithText("1 von 2 Fotos fehlgeschlagen").assertExists()
        compose.onRoot().captureRoboImage("AiSettingsScreenshotTest.diagnostics.png")
        compose.onNodeWithText("Technische Details anzeigen").performClick()
        compose.onNodeWithText(report).assertExists()
    }

    @Test fun batchProgress() {
        show {
            Column {
                AiAnalysisControls(AiProvider.Local, AnalysisProgress(true, 7, 10, 6), true, true, {}, {})
                AiAnalysisActionButton(AnalysisProgress(true, 7, 10, 6), true, true, {}, {})
            }
        }
        compose.onRoot().captureRoboImage("AiSettingsScreenshotTest.batch-progress.png")
    }

    private fun showSettings(settings: AiSettings, download: ModelDownloadState, name: String) {
        val downloads = settings.provider.localModel?.let { mapOf(settings.provider to download) }.orEmpty()
        show { AiSettingsContent(settings, downloads, false, null, {}, { _, _, _, _ -> }, {}, { _ -> }, {}, { _ -> }) }
        compose.onRoot().captureRoboImage("AiSettingsScreenshotTest.$name.png")
    }
    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        activity.get().setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { content() } } }
        compose.waitForIdle()
    }
}

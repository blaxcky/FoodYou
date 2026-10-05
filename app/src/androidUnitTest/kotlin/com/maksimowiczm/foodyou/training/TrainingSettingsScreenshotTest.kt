package com.maksimowiczm.foodyou.training

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "de-rDE-w390dp-h844dp-mdpi", application = Application::class)
@OptIn(ExperimentalRoborazziApi::class)
class TrainingSettingsScreenshotTest {
    @Test fun login() = capture("login", TrainingSyncState(configured = true))
    @Test fun imported() = capture("imported", TrainingSyncState(
        configured = true, account = TrainingAccount(uid = "test", email = "training@example.test"),
        report = TrainingSyncReport(1790500000000, imported = 2, existing = 5, zeroCalories = 1)))
    @Test fun failure() = capture("failure", TrainingSyncState(
        configured = true, account = TrainingAccount(uid = "test", email = "training@example.test"),
        report = TrainingSyncReport(1790500000000, failed = 1, errors = listOf("Unbekannte Schema-Version."))))
    private fun capture(name: String, state: TrainingSyncState) {
        captureRoboImage(
            filePath = "TrainingSettingsScreenshotTest.$name.png",
            roborazziComposeOptions = RoborazziComposeOptions.Builder().size(390, 844).locale("de-rDE").build(),
        ) { MaterialTheme { Surface(Modifier.fillMaxSize()) {
            Column { TrainingSyncSettingsContent(state, { _, _ -> }, {}) }
        } } }
    }
}

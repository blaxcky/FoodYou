package com.maksimowiczm.foodyou.app.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.locale
import com.github.takahirom.roborazzi.size
import com.maksimowiczm.foodyou.sync.SyncLogRun
import com.maksimowiczm.foodyou.sync.SyncLogStatus
import com.maksimowiczm.foodyou.sync.SyncLogStep
import org.junit.Test
import org.junit.After
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "de-rDE-w390dp-h844dp-mdpi")
@OptIn(ExperimentalRoborazziApi::class)
class SyncLogScreenScreenshotTest {
    @After fun tearDown() { stopKoin() }

    @Test fun empty() = capture("empty", emptyList())

    @Test fun parallelSyncWithFailure() = capture("parallel-failure", listOf(
        SyncLogRun(1, "Startseite · 2026-10-03", 1_791_012_000_000, 6500, SyncLogStatus.Failed, listOf(
            SyncLogStep(1, null, "Trainings synchronisieren", 1_791_012_000_000, 0, 800,
                SyncLogStatus.Success, "2 übernommen · 8 bereits vorhanden · 0 Fehler"),
            SyncLogStep(2, null, "Schritte synchronisieren · 31 Tage", 1_791_012_000_020, 20, 2100,
                SyncLogStatus.Success, "Abgleich abgeschlossen"),
            SyncLogStep(3, null, "FDDB-Tagebucheinträge laden", 1_791_012_000_030, 30, 6400,
                SyncLogStatus.Failed, "Vorgang fehlgeschlagen (IOException)"),
        ))
    ))

    private fun capture(name: String, runs: List<SyncLogRun>) {
        captureRoboImage(
            filePath = "SyncLogScreenScreenshotTest.$name.png",
            roborazziComposeOptions = RoborazziComposeOptions.Builder().size(390, 844).locale("de-rDE").build(),
        ) {
            MaterialTheme {
                Box(Modifier.requiredSize(390.dp, 844.dp)) {
                    SyncLogContent(runs, onBack = {}, onClear = {})
                }
            }
        }
    }
}

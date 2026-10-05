package com.maksimowiczm.foodyou.training

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import kotlin.test.assertEquals
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "de-rDE-w390dp-h844dp-mdpi", application = Application::class)
class TrainingImportCardScreenshotTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ActivityController<ComponentActivity>

    @After fun tearDown() {
        if (::activity.isInitialized) activity.close()
    }

    @Test fun signedOutOffersAccountSetupWithoutStartingSync() {
        var settingsOpened = 0
        show(TrainingSyncState(configured = true), onSettings = { settingsOpened++ })
        compose.onNodeWithText("Trainings jetzt importieren").assertDoesNotExist()
        compose.onNodeWithText("Konto einrichten").performClick()
        assertEquals(1, settingsOpened)
        capture("signed-out")
    }

    @Test fun manualButtonStartsSync() {
        var syncs = 0
        show(Ready, onSync = { syncs++ })
        assertEquals(0, syncs)
        compose.onNodeWithText("Trainings jetzt importieren").performClick()
        assertEquals(1, syncs)
        capture("ready")
    }

    @Test fun runningImportDisablesDuplicateStart() {
        var syncs = 0
        show(Ready.copy(busy = true), onSync = { syncs++ })
        compose.onNodeWithText("Trainings jetzt importieren").assertIsNotEnabled().performClick()
        compose.onNodeWithText("Trainings werden importiert …").assertIsDisplayed()
        assertEquals(0, syncs)
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(400)
        capture("running")
    }

    @Test fun importedSessionsShowDatesBreakdownAndNewestFirst() {
        show(Ready.copy(report = Report))
        compose.onNodeWithText("05.10.2026").assertIsDisplayed()
        val newest = compose.onNodeWithText("05.10.2026").fetchSemanticsNode().boundsInRoot.top
        val oldest = compose.onNodeWithText("27.09.2026").fetchSemanticsNode().boundsInRoot.top
        kotlin.test.assertTrue(newest < oldest)
        compose.onNodeWithText("330 kcal").assertIsDisplayed()
        capture("imported")
    }

    @Test fun emptySyncShowsClearResult() {
        show(Ready.copy(report = TrainingSyncReport(Report.finishedAtMillis)))
        compose.onNodeWithText("Keine neuen Trainings gefunden").assertIsDisplayed()
        compose.onNodeWithText("Neu übernommene Trainings").assertDoesNotExist()
        capture("empty")
    }

    @Test fun partialSuccessKeepsImportedSessionAndFailureVisible() {
        show(Ready.copy(report = Report.copy(imported = 1,
            importedSessions = Report.importedSessions.take(1), failed = 1, pending = 1,
            errors = listOf("Ein Training konnte lokal nicht gespeichert werden."))))
        compose.onNodeWithText("27.09.2026").assertIsDisplayed()
        compose.onNodeWithText("1 weiterhin offen", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Keine neuen Trainings gefunden").assertDoesNotExist()
        capture("partial-failure")
    }

    @Test fun oldReportExplainsMissingDetails() {
        show(Ready.copy(report = Report.copy(importedSessions = emptyList())))
        compose.onNodeWithText("Für diesen älteren Import sind keine Einzeldetails verfügbar.")
            .assertIsDisplayed()
        capture("legacy-report")
    }

    @Test fun longImportDetailsCanBeScrolled() {
        show(Ready.copy(report = Report.copy(imported = 12,
            importedSessions = (1..12).map {
                ImportedTrainingSession("session-$it", "2026-09-${it.toString().padStart(2, '0')}", 210, 120)
            })))
        compose.onNodeWithText("01.09.2026").performScrollTo().assertIsDisplayed()
        capture("scrolled-details")
    }

    private fun show(state: TrainingSyncState, onSync: () -> Unit = {}, onSettings: () -> Unit = {}) {
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        activity.get().setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                        TrainingImportCard(state, onSync, onSettings)
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        compose.onNode(isRoot()).captureRoboImage("TrainingImportCardScreenshotTest.$name.png")
    }

    private companion object {
        val Ready = TrainingSyncState(configured = true,
            account = TrainingAccount(uid = "test", email = "training@example.test"))
        val Report = TrainingSyncReport(1791208900000, imported = 2, existing = 5, zeroCalories = 1,
            importedSessions = listOf(
                ImportedTrainingSession("first", "2026-09-27", 210, 120),
                ImportedTrainingSession("second", "2026-10-05", 180, 0),
            ))
    }
}

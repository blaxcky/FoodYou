package com.maksimowiczm.foodyou.app.ui.food.quickcapture

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.maksimowiczm.foodyou.food.domain.entity.QuickCaptureFoodName
import com.maksimowiczm.foodyou.food.domain.entity.QuickCaptureLogEntry
import com.maksimowiczm.foodyou.food.domain.entity.QuickCaptureWeightMode
import kotlin.time.Instant
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "de-rDE-w390dp-h844dp-mdpi", application = Application::class)
class QuickCaptureScreenshotTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ActivityController<ComponentActivity>

    private val names =
        listOf(
            QuickCaptureFoodName(1, "Skyr Natur", "skyr natur", 12, instant(40)),
            QuickCaptureFoodName(2, "Apfel", "apfel", 8, instant(30)),
            QuickCaptureFoodName(3, "Haferflocken", "haferflocken", 4, instant(20)),
        )

    @After
    fun tearDown() {
        if (::activity.isInitialized) activity.close()
    }

    @Test
    fun logWithGroupedEntries() {
        show {
            QuickCaptureLog(
                entries =
                    listOf(
                        direct(1, 1, "Skyr Natur", 200.0),
                        direct(2, 1, "Skyr Natur", 150.0),
                        direct(3, 2, "Apfel", 125.5),
                    ),
                aggregate = true,
                onAggregateChange = {},
                onCompleteAfter = { _, _ -> },
                onDelete = {},
                onClearCompleted = {},
                onCopyPrompt = {},
                onTransfer = {},
            )
        }
        capture("log-grouped")
    }

    @Test
    fun pendingAfter() {
        show {
            QuickCaptureLog(
                entries = listOf(beforeAfter(4, 3, "Haferflocken", 420.0)),
                aggregate = false,
                onAggregateChange = {},
                onCompleteAfter = { _, _ -> },
                onDelete = {},
                onClearCompleted = {},
                onCopyPrompt = {},
                onTransfer = {},
            )
        }
        capture("pending-after")
    }

    @Test
    fun completedFilter() {
        show {
            QuickCaptureLog(
                entries = listOf(direct(5, 2, "Apfel", 180.0, completed = true)),
                aggregate = false,
                onAggregateChange = {},
                onCompleteAfter = { _, _ -> },
                onDelete = {},
                onClearCompleted = {},
                onCopyPrompt = {},
                onTransfer = {},
            )
        }
        compose.onNodeWithText("Erledigt").performClick()
        compose.waitForIdle()
        capture("completed")
    }

    @Test
    fun entrySheet() {
        show {
            QuickCaptureEntrySheet(
                names = names,
                error = null,
                onDismiss = {},
                onSave = { _, _, _, _, _ -> },
            )
        }
        capture("entry-sheet")
    }

    @Test
    fun invalidEntryKeepsSheetOpen() {
        show {
            var error by remember { mutableStateOf<QuickCaptureFormError?>(null) }
            QuickCaptureEntrySheet(
                names = names,
                error = error,
                onDismiss = {},
                onSave = { _, _, _, _, _ -> error = QuickCaptureFormError.Name },
            )
        }

        compose.onNodeWithText("Zum Log hinzufügen").performClick()
        compose.onNodeWithText("Neuer Eintrag").assertExists()
        compose.onNodeWithText("Lebensmittelname eingeben").assertExists()
    }

    @Test
    fun successfulEntryClosesSheet() {
        show {
            var visible by remember { mutableStateOf(true) }
            if (visible) {
                QuickCaptureEntrySheet(
                    names = names,
                    error = null,
                    onDismiss = { visible = false },
                    onSave = { _, _, _, _, _ -> visible = false },
                )
            }
        }

        compose.onNode(hasText("Name") and hasSetTextAction()).performTextInput("Skyr Natur")
        compose.onNode(hasText("Gewicht") and hasSetTextAction()).performTextInput("200")
        compose.onNodeWithText("Zum Log hinzufügen").performClick()
        compose.onNodeWithText("Neuer Eintrag").assertDoesNotExist()
    }

    @Test
    fun entryDeleteRequiresConfirmation() {
        var deleted = false
        show {
            QuickCaptureLog(
                entries = listOf(direct(1, 1, "Skyr Natur", 200.0)),
                aggregate = false,
                onAggregateChange = {},
                onCompleteAfter = { _, _ -> },
                onDelete = { deleted = true },
                onClearCompleted = {},
                onCopyPrompt = {},
                onTransfer = {},
            )
        }

        compose.onNodeWithContentDescription("Löschen").performClick()
        compose.runOnIdle { kotlin.test.assertFalse(deleted) }
        compose.onNodeWithText("Abbrechen").performClick()
        compose.runOnIdle { kotlin.test.assertFalse(deleted) }
        compose.onNodeWithContentDescription("Löschen").performClick()
        compose.onNodeWithText("Löschen").performClick()
        compose.runOnIdle { kotlin.test.assertTrue(deleted) }
    }

    @Test
    fun clearingCompletedEntriesRequiresConfirmation() {
        var cleared = false
        show {
            QuickCaptureLog(
                entries = listOf(direct(1, 1, "Skyr Natur", 200.0, completed = true)),
                aggregate = false,
                onAggregateChange = {},
                onCompleteAfter = { _, _ -> },
                onDelete = {},
                onClearCompleted = { cleared = true },
                onCopyPrompt = {},
                onTransfer = {},
            )
        }

        compose.onNodeWithText("Erledigt").performClick()
        compose.onNodeWithText("Alle erledigten löschen").performClick()
        compose.runOnIdle { kotlin.test.assertFalse(cleared) }
        compose.onNodeWithText("Löschen").performClick()
        compose.runOnIdle { kotlin.test.assertTrue(cleared) }
    }

    @Test
    fun floatingActionButtonMatchesContext() {
        show {
            var tab by remember { mutableStateOf(QuickCaptureTab.Log) }
            Surface(Modifier.fillMaxSize()) {
                QuickCaptureFloatingActionButton(
                    selectedTab = tab,
                    cameraOpen = false,
                    onAddEntry = { tab = QuickCaptureTab.Photos },
                    onOpenCamera = { tab = QuickCaptureTab.Library },
                )
            }
        }

        compose.onNodeWithContentDescription("Log-Eintrag hinzufügen").performClick()
        compose.onNodeWithContentDescription("Fotos aufnehmen").performClick()
        compose.onNodeWithContentDescription("Log-Eintrag hinzufügen").assertDoesNotExist()
        compose.onNodeWithContentDescription("Fotos aufnehmen").assertDoesNotExist()
    }

    @Test
    fun photoInbox() {
        show {
            QuickCapturePhotos(
                entries = listOf(pendingPhoto(6), pendingPhoto(7)),
                onPhoto = {},
                onDelete = {},
            )
        }
        capture("photo-inbox")
    }

    @Test
    fun photoDeleteRequiresConfirmation() {
        var deleted = false
        show {
            QuickCapturePhotos(
                entries = listOf(pendingPhoto(6)),
                onPhoto = {},
                onDelete = { deleted = true },
            )
        }

        compose.onNodeWithContentDescription("Löschen").performClick()
        compose.runOnIdle { kotlin.test.assertFalse(deleted) }
        compose.onNodeWithText("Löschen").performClick()
        compose.runOnIdle { kotlin.test.assertTrue(deleted) }
    }

    @Test
    fun library() {
        show { QuickCaptureLibrary(names = names, onRename = { _, _ -> }, onDelete = {}) }
        capture("library")
    }

    @Test
    fun libraryDeleteRequiresConfirmation() {
        var deletedId: Long? = null
        show {
            QuickCaptureLibrary(
                names = listOf(names.first()),
                onRename = { _, _ -> },
                onDelete = { deletedId = it },
            )
        }

        compose.onNodeWithContentDescription("Löschen").performClick()
        compose.runOnIdle { kotlin.test.assertNull(deletedId) }
        compose.onNodeWithText("Löschen").performClick()
        compose.runOnIdle { kotlin.test.assertEquals(1L, deletedId) }
    }

    @Test
    fun photoActions() {
        show {
            TopAppBar(
                title = { Text("Fotos") },
                actions = {
                    QuickCapturePhotoActions(
                        onRotateLeft = {},
                        onRotateRight = {},
                        onDelete = {},
                    )
                },
            )
        }
        capture("photo-actions")
    }

    @Test
    fun deleteConfirmation() {
        show {
            QuickCaptureDeleteConfirmationDialog(
                target = QuickCaptureDeleteTarget.Photo,
                onDismiss = {},
                onConfirm = {},
            )
        }
        capture("delete-confirmation")
    }

    @Test
    @OptIn(ExperimentalRoborazziApi::class)
    fun autocompleteKeepsInputFocusedAndSelectionClosesMenu() {
        var confirmed: String? = null
        show {
            var value by remember { mutableStateOf("") }
            QuickCaptureNameField(
                value = value,
                onValueChange = { value = it },
                names = names,
                onNameConfirmed = {
                    value = it
                    confirmed = it
                },
            )
        }

        val nameField = compose.onNodeWithText("Name")
        nameField.performTextInput("Sky")
        nameField.assertIsFocused()
        val suggestion = compose.onNode(hasText("Skyr Natur") and hasClickAction())
        suggestion.assertExists()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        captureScreenRoboImage("QuickCaptureScreenshotTest.autocomplete.png")

        suggestion.performSemanticsAction(SemanticsActions.OnClick) {
            kotlin.test.assertTrue(it())
        }
        compose.waitForIdle()
        compose.runOnIdle { kotlin.test.assertEquals("Skyr Natur", confirmed) }
        compose.onAllNodesWithText("Skyr Natur").assertCountEquals(1)
    }

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        activity.get().setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { content() }
            }
        }
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        compose.onNode(isRoot()).captureRoboImage("QuickCaptureScreenshotTest.$name.png")
    }

    private fun direct(
        id: Long,
        foodNameId: Long,
        foodName: String,
        weight: Double,
        completed: Boolean = false,
    ) =
        QuickCaptureLogEntry(
            id = id,
            foodNameId = foodNameId,
            foodName = foodName,
            weightMode = QuickCaptureWeightMode.Direct,
            directWeightInGrams = weight,
            beforeWeightInGrams = null,
            afterWeightInGrams = null,
            photoPath = null,
            createdAt = instant(id),
            completedAt = if (completed) instant(100) else null,
        )

    private fun beforeAfter(id: Long, foodNameId: Long, foodName: String, before: Double) =
        QuickCaptureLogEntry(
            id = id,
            foodNameId = foodNameId,
            foodName = foodName,
            weightMode = QuickCaptureWeightMode.BeforeAfter,
            directWeightInGrams = null,
            beforeWeightInGrams = before,
            afterWeightInGrams = null,
            photoPath = null,
            createdAt = instant(id),
            completedAt = null,
        )

    private fun pendingPhoto(id: Long) =
        QuickCaptureLogEntry(
            id = id,
            foodNameId = null,
            foodName = null,
            weightMode = QuickCaptureWeightMode.Direct,
            directWeightInGrams = null,
            beforeWeightInGrams = null,
            afterWeightInGrams = null,
            photoPath = "photo-$id.jpg",
            createdAt = instant(id),
            completedAt = null,
        )

    private fun instant(value: Long) = Instant.fromEpochSeconds(value)
}

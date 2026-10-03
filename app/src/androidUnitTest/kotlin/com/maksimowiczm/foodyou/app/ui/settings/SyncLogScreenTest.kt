package com.maksimowiczm.foodyou.app.ui.settings

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.maksimowiczm.foodyou.common.compose.utility.ClipboardManager
import com.maksimowiczm.foodyou.common.compose.utility.ClipboardManagerProvider
import com.maksimowiczm.foodyou.sync.SyncLogRun
import com.maksimowiczm.foodyou.sync.SyncLogStatus
import com.maksimowiczm.foodyou.sync.SyncLogStep
import kotlin.test.assertContains
import kotlin.test.assertFalse
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "de-rDE-w390dp-h844dp-mdpi")
class SyncLogScreenTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var activity: ActivityController<ComponentActivity>? = null
    @After fun tearDown() {
        activity?.pause()?.stop()?.destroy()
        stopKoin()
    }

    @Test
    fun copiesOnlySelectedCollapsedRunAndKeepsCopyAllAvailable() {
        var copiedText = ""
        val clipboard = object : ClipboardManager {
            override fun copy(label: String, text: String) { copiedText = text }
            override fun paste(): String? = copiedText
        }
        val older = SyncLogRun(1, "Älterer Lauf", 0, 100, SyncLogStatus.Success,
            listOf(SyncLogStep(1, null, "Nur alter Schritt", 0, 0, 100, SyncLogStatus.Success)))
        val newer = SyncLogRun(2, "Neuerer Lauf", 1000, 200, SyncLogStatus.Success)
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        activity!!.get().setContent {
            MaterialTheme {
                ClipboardManagerProvider(clipboard) {
                    SyncLogContent(listOf(older, newer), onBack = {}, onClear = {})
                }
            }
        }

        compose.onNodeWithText("1. Nur alter Schritt").assertDoesNotExist()
        compose.onNodeWithContentDescription("Sync-Lauf Älterer Lauf kopieren").performClick()
        assertContains(copiedText, "Älterer Lauf")
        assertContains(copiedText, "1. Nur alter Schritt")
        assertContains(copiedText, "Gesamtdauer 100 ms")
        assertFalse(copiedText.contains("Neuerer Lauf"))
        compose.onNodeWithText("1. Nur alter Schritt").assertDoesNotExist()

        compose.onNodeWithContentDescription("Alle Sync-Protokolle kopieren").performClick()
        assertContains(copiedText, "Älterer Lauf")
        assertContains(copiedText, "Neuerer Lauf")
    }
}

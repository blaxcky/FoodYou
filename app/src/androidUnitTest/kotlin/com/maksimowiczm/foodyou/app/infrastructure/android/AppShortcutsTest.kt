package com.maksimowiczm.foodyou.app.infrastructure.android

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ShortcutManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class AppShortcutsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun publishesCameraBeforeBarcodeShortcut() {
        publishAppShortcuts(context)

        val shortcuts = context.getSystemService(ShortcutManager::class.java).dynamicShortcuts

        assertEquals(
            listOf(SHORTCUT_QUICK_CAPTURE_CAMERA_ID, SHORTCUT_SCAN_BARCODE_ID),
            shortcuts.sortedBy { it.rank }.map { it.id },
        )
        assertEquals(listOf(0, 1), shortcuts.sortedBy { it.rank }.map { it.rank })
    }

    @Test
    fun cameraShortcutStartsDedicatedTask() {
        val shortcut =
            createAppShortcuts(context).single { it.id == SHORTCUT_QUICK_CAPTURE_CAMERA_ID }
        val intent = assertNotNull(shortcut.intent)

        assertEquals(
            ComponentName(context, QuickCaptureCameraActivity::class.java),
            intent.component,
        )
        assertEquals(ACTION_QUICK_CAPTURE_CAMERA, intent.action)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
    }

    @Test
    fun cameraActivityIsInternalAndExcludedFromRecents() {
        val info =
            context.packageManager.getActivityInfo(
                ComponentName(context, QuickCaptureCameraActivity::class.java),
                0,
            )

        assertFalse(info.exported)
        assertEquals(ActivityInfo.LAUNCH_SINGLE_TASK, info.launchMode)
        assertTrue(info.flags and ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS != 0)
        assertEquals("${context.packageName}.quick_capture_camera", info.taskAffinity)
    }
}

package com.maksimowiczm.foodyou.app.widget

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.maksimowiczm.foodyou.app.infrastructure.android.ACTION_QUICK_CAPTURE_CAMERA
import com.maksimowiczm.foodyou.app.infrastructure.android.ACTION_SCAN_BARCODE
import com.maksimowiczm.foodyou.app.infrastructure.android.MainActivity
import com.maksimowiczm.foodyou.app.infrastructure.android.QuickCaptureCameraActivity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.After
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class CalorieWidgetLaunchIntentTest {
    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun widgetPendingIntentOpensMainActivity() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        calorieWidgetLaunchPendingIntent(context).send()

        val startedIntent =
            assertNotNull(
                shadowOf(context.applicationContext as Application).nextStartedActivity
            )
        assertEquals(ComponentName(context, MainActivity::class.java), startedIntent.component)
        assertEquals(Intent.ACTION_MAIN, startedIntent.action)
        assertTrue(Intent.CATEGORY_LAUNCHER in startedIntent.categories.orEmpty())
        assertTrue(startedIntent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        assertTrue(startedIntent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
    }

    @Test
    fun eatenSectionPendingIntentOpensQuickCaptureCamera() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        calorieWidgetQuickCapturePendingIntent(context).send()

        val startedIntent =
            assertNotNull(
                shadowOf(context.applicationContext as Application).nextStartedActivity
            )
        assertEquals(
            ComponentName(context, QuickCaptureCameraActivity::class.java),
            startedIntent.component,
        )
        assertEquals(ACTION_QUICK_CAPTURE_CAMERA, startedIntent.action)
        assertTrue(startedIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun burnedSectionPendingIntentOpensBarcodeScanner() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        calorieWidgetScanBarcodePendingIntent(context).send()

        val startedIntent =
            assertNotNull(
                shadowOf(context.applicationContext as Application).nextStartedActivity
            )
        assertEquals(ComponentName(context, MainActivity::class.java), startedIntent.component)
        assertEquals(ACTION_SCAN_BARCODE, startedIntent.action)
        assertTrue(startedIntent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        assertTrue(startedIntent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
    }
}

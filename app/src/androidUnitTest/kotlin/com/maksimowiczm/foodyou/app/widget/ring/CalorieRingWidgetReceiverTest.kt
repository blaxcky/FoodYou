package com.maksimowiczm.foodyou.app.widget.ring

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.maksimowiczm.foodyou.app.widget.CalorieWidgetProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.After
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin

@RunWith(AndroidJUnit4::class)
class CalorieRingWidgetReceiverTest {
    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun bothWidgetReceiversAreRegisteredWithProviderMetadata() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packageManager = context.packageManager

        for (receiver in listOf(CalorieWidgetProvider::class.java, CalorieRingWidgetReceiver::class.java)) {
            val info =
                packageManager.getReceiverInfo(
                    ComponentName(context, receiver),
                    PackageManager.GET_META_DATA,
                )
            assertNotNull(info.metaData?.get("android.appwidget.provider"), receiver.name)
        }
    }

    @Test
    fun receiverHandlesDateChangeBroadcasts() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(Intent.ACTION_DATE_CHANGED)

        val receivers =
            context.packageManager.queryBroadcastReceivers(intent, 0).map { it.activityInfo.name }

        assertEquals(
            listOf(CalorieWidgetProvider::class.java.name, CalorieRingWidgetReceiver::class.java.name)
                .sorted(),
            receivers.filter { it.startsWith("com.maksimowiczm.foodyou.app.widget") }.sorted(),
        )
    }
}

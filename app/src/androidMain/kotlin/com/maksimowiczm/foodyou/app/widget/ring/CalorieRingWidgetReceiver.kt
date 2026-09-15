package com.maksimowiczm.foodyou.app.widget.ring

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.maksimowiczm.foodyou.app.widget.ReceiverUpdateLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class CalorieRingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CalorieRingWidget()

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                val applicationContext = context.applicationContext
                val pendingResult = goAsync()
                receiverUpdateLauncher.launch(finish = pendingResult::finish) {
                    CalorieRingWidget.updateAllSuspending(applicationContext)
                }
            }
            else -> super.onReceive(context, intent)
        }
    }

    private companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val receiverUpdateLauncher = ReceiverUpdateLauncher(scope)
    }
}

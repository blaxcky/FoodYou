package com.maksimowiczm.foodyou.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

class CalorieWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        launchUpdate(context) { updater().update(it, appWidgetIds) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        launchUpdate(context) { updater().update(it, intArrayOf(appWidgetId)) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {
                val appWidgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
                if (appWidgetIds == null) {
                    super.onReceive(context, intent)
                } else {
                    launchReceiverUpdate(context) { updater().update(it, appWidgetIds) }
                }
            }
            AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED -> {
                val appWidgetId =
                    intent.getIntExtra(
                        AppWidgetManager.EXTRA_APPWIDGET_ID,
                        AppWidgetManager.INVALID_APPWIDGET_ID,
                    )
                if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                    super.onReceive(context, intent)
                } else {
                    launchReceiverUpdate(context) { updater().update(it, intArrayOf(appWidgetId)) }
                }
            }
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                launchReceiverUpdate(context) { updateAllSuspending(it) }
            }
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                launchReceiverUpdate(context) { updateAllSuspending(it) }
            }
            else -> super.onReceive(context, intent)
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val receiverUpdateLauncher = ReceiverUpdateLauncher(scope)

        fun updateAll(context: Context) {
            launchUpdate(context) { updateAllSuspending(it) }
        }

        fun updateAllValues(context: Context) {
            launchUpdate(context) { updateAllSuspending(it) }
        }

        private fun updater(): CalorieWidgetUpdater = GlobalContext.get().get()

        private fun launchUpdate(context: Context, block: suspend (Context) -> Unit) {
            val applicationContext = context.applicationContext
            scope.launch { block(applicationContext) }
        }

        private fun CalorieWidgetProvider.launchReceiverUpdate(
            context: Context,
            block: suspend (Context) -> Unit,
        ) {
            val applicationContext = context.applicationContext
            val pendingResult = goAsync()
            receiverUpdateLauncher.launch(finish = pendingResult::finish) {
                block(applicationContext)
            }
        }

        private suspend fun updateAllSuspending(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, CalorieWidgetProvider::class.java)
            updater().update(context, appWidgetManager.getAppWidgetIds(component))
        }
    }
}

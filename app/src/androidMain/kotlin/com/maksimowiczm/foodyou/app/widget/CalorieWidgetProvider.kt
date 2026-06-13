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
        scope.launch { updater().update(context, appWidgetIds) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        scope.launch { updater().update(context, intArrayOf(appWidgetId)) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            updateAll(context)
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        fun updateAll(context: Context) {
            scope.launch { updateAllSuspending(context) }
        }

        fun updateAllValues(context: Context) {
            scope.launch { updateAllSuspending(context) }
        }

        private fun updater(): CalorieWidgetUpdater = GlobalContext.get().get()

        private suspend fun updateAllSuspending(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, CalorieWidgetProvider::class.java)
            updater().update(context, appWidgetManager.getAppWidgetIds(component))
        }
    }
}

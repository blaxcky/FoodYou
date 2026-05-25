package com.maksimowiczm.foodyou.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
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

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        fun updateAll(context: Context) {
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

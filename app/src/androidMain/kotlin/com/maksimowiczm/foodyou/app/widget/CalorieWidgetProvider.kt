package com.maksimowiczm.foodyou.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

class CalorieWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            val pendingResult = goAsync()
            scope.launch {
                try {
                    showRefreshInProgress(context)
                    updateAll(context)
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        super.onReceive(context, intent)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        scope.launch { updater().update(context, appWidgetIds) }
    }

    companion object {
        internal const val ACTION_REFRESH =
            "com.maksimowiczm.foodyou.app.widget.action.REFRESH_CALORIES"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        internal fun refreshIntent(context: Context): Intent =
            Intent(context, CalorieWidgetProvider::class.java).setAction(ACTION_REFRESH)

        private fun updater(): CalorieWidgetUpdater = GlobalContext.get().get()

        private suspend fun updateAll(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, CalorieWidgetProvider::class.java)
            updater().update(context, appWidgetManager.getAppWidgetIds(component))
        }

        private fun showRefreshInProgress(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, CalorieWidgetProvider::class.java)
            updater().showRefreshInProgress(context, appWidgetManager.getAppWidgetIds(component))
        }
    }
}

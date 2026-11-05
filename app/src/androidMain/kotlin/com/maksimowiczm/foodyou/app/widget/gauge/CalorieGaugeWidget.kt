package com.maksimowiczm.foodyou.app.widget.gauge

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.maksimowiczm.foodyou.app.widget.CalorieWidgetModel
import com.maksimowiczm.foodyou.app.widget.CalorieWidgetUpdater
import com.maksimowiczm.foodyou.app.widget.ring.CalorieRingWidget
import com.maksimowiczm.foodyou.app.widget.ring.CalorieRingWidgetTheme
import com.maksimowiczm.foodyou.app.widget.ring.toCalorieWidgetModel
import com.maksimowiczm.foodyou.app.widget.ring.write
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

/**
 * The calorie ring of the home screen's goals card as a widget: the Optimized and Diet goals sit on
 * the ring instead of below it as bars. It keeps its model in the widget state like
 * [CalorieRingWidget], so [updateAllSuspending] refreshes an open Glance session too.
 */
internal class CalorieGaugeWidget(
    private val loadModel: suspend () -> CalorieWidgetModel = {
        GlobalContext.get().get<CalorieWidgetUpdater>().loadModel()
    }
) : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        runCatching { loadModel() }
            .onFailure { Log.w(TAG, "Failed to load widget model", it) }
            .onSuccess { model ->
                runCatching { writeState(context, id, model) }
                    .onFailure { Log.w(TAG, "Failed to store widget model", it) }
            }
        provideContent {
            val model =
                currentState<Preferences>().toCalorieWidgetModel() ?: CalorieRingWidget.emptyModel()
            CalorieRingWidgetTheme { CalorieGaugeWidgetContent(model) }
        }
    }

    suspend fun updateAllSuspending(context: Context) {
        runCatching {
                val ids = GlanceAppWidgetManager(context).getGlanceIds(CalorieGaugeWidget::class.java)
                if (ids.isEmpty()) return@runCatching
                val model = loadModel()
                ids.forEach { id ->
                    writeState(context, id, model)
                    update(context, id)
                }
            }
            .onFailure { Log.w(TAG, "Failed to update widgets", it) }
    }

    private suspend fun writeState(context: Context, id: GlanceId, model: CalorieWidgetModel) {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { preferences ->
            preferences.toMutablePreferences().apply { write(model) }
        }
    }

    companion object {
        private const val TAG = "CalorieGaugeWidget"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        fun requestUpdateAll(context: Context) {
            val applicationContext = context.applicationContext
            scope.launch { updateAllSuspending(applicationContext) }
        }

        suspend fun updateAllSuspending(context: Context) {
            CalorieGaugeWidget().updateAllSuspending(context)
        }
    }
}

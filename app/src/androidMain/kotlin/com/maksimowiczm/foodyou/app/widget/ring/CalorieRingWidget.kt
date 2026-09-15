package com.maksimowiczm.foodyou.app.widget.ring

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
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.toKotlinLocalDate
import org.koin.core.context.GlobalContext

/**
 * The model lives in the widget state: [updateAllSuspending] writes it and calls [update], which
 * recomposes the content even while a Glance session is still open. Loading only inside
 * [provideGlance] would leave an open session stuck with the model it started with.
 */
internal class CalorieRingWidget(
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
            val model = currentState<Preferences>().toCalorieWidgetModel() ?: emptyModel()
            CalorieRingWidgetTheme { CalorieRingWidgetContent(model) }
        }
    }

    suspend fun updateAllSuspending(context: Context) {
        runCatching {
                val ids = GlanceAppWidgetManager(context).getGlanceIds(CalorieRingWidget::class.java)
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
        private const val TAG = "CalorieRingWidget"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        fun requestUpdateAll(context: Context) {
            val applicationContext = context.applicationContext
            scope.launch { updateAllSuspending(applicationContext) }
        }

        suspend fun updateAllSuspending(context: Context) {
            CalorieRingWidget().updateAllSuspending(context)
        }

        internal fun emptyModel(): CalorieWidgetModel =
            CalorieWidgetModel(
                date = LocalDate.now().toKotlinLocalDate(),
                countedSteps = 0,
                eatenKcal = 0,
                burnedKcal = 0,
                netKcal = 0,
                normalGoalKcal = 0,
                normalLeftKcal = 0,
                optimizedGoalKcal = null,
                optimizedLeftKcal = null,
                dietGoalKcal = null,
                dietLeftKcal = null,
            )
    }
}

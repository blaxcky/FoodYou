package com.maksimowiczm.foodyou.app.widget.ring

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import com.maksimowiczm.foodyou.app.widget.CalorieWidgetModel
import com.maksimowiczm.foodyou.app.widget.CalorieWidgetUpdater
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.toKotlinLocalDate
import org.koin.core.context.GlobalContext

internal class CalorieRingWidget(
    private val loadModel: suspend () -> CalorieWidgetModel = {
        GlobalContext.get().get<CalorieWidgetUpdater>().loadModel()
    }
) : GlanceAppWidget() {

    override val sizeMode: SizeMode =
        SizeMode.Responsive(setOf(CalorieRingWidgetSizes.Compact, CalorieRingWidgetSizes.Tall))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val model =
            runCatching { loadModel() }
                .onFailure { Log.w(TAG, "Failed to load widget model", it) }
                .getOrElse { emptyModel() }
        provideContent { CalorieRingWidgetTheme { CalorieRingWidgetContent(model) } }
    }

    companion object {
        private const val TAG = "CalorieRingWidget"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        fun requestUpdateAll(context: Context) {
            val applicationContext = context.applicationContext
            scope.launch { updateAllSuspending(applicationContext) }
        }

        suspend fun updateAllSuspending(context: Context) {
            runCatching { CalorieRingWidget().updateAll(context) }
                .onFailure { Log.w(TAG, "Failed to update widgets", it) }
        }

        private fun emptyModel(): CalorieWidgetModel =
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

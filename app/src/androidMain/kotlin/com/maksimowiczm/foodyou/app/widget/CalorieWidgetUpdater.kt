package com.maksimowiczm.foodyou.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import com.maksimowiczm.foodyou.R
import com.maksimowiczm.foodyou.activity.domain.repository.ActivityRepository
import com.maksimowiczm.foodyou.app.ui.home.goals.GoalEnergyOptimizationDay
import com.maksimowiczm.foodyou.app.ui.home.goals.startOfWeek
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.food.NutritionFactsField
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.fooddiary.domain.usecase.ObserveDiaryMealsUseCase
import com.maksimowiczm.foodyou.goals.domain.repository.GoalsRepository
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
import java.time.format.DateTimeFormatter
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.toJavaLocalDate

internal class CalorieWidgetUpdater(
    private val observeDiaryMealsUseCase: ObserveDiaryMealsUseCase,
    private val goalsRepository: GoalsRepository,
    private val activityRepository: ActivityRepository,
    private val settingsRepository: UserPreferencesRepository<Settings>,
    private val dateProvider: DateProvider,
) {

    suspend fun update(context: Context, appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return

        val manager = AppWidgetManager.getInstance(context)
        val model = loadModel()
        appWidgetIds.forEach { appWidgetId ->
            manager.updateAppWidget(
                appWidgetId,
                createRemoteViews(context, manager, appWidgetId, model),
            )
        }
    }

    fun showRefreshInProgress(context: Context, appWidgetIds: IntArray) {
        // Keep the refresh state intentionally static. Some launchers reject reflected
        // RemoteViews actions such as setRotation and replace the widget with an error view.
    }

    private suspend fun loadModel(): CalorieWidgetModel {
        val settings = settingsRepository.observe().first()
        val today = dateProvider.now().date
        val facts = observeDiaryMealsUseCase.observeNutritionFacts(today).first()
        val goal = goalsRepository.observeDailyGoals(today).first()
        val activity =
            activityRepository
                .observeDailySummary(today, settings.stepsCaloriesPerStepKcal)
                .first()
        val previousDays =
            previousWeekDates(today).map { date ->
                val previousFacts = observeDiaryMealsUseCase.observeNutritionFacts(date).first()
                val previousGoal = goalsRepository.observeDailyGoals(date).first()
                val previousActivity =
                    activityRepository
                        .observeDailySummary(date, settings.stepsCaloriesPerStepKcal)
                        .first()

                GoalEnergyOptimizationDay(
                    consumedEnergyKcal = previousFacts.energy.value ?: 0.0,
                    baseEnergyGoalKcal = previousGoal[NutritionFactsField.Energy],
                    burnedEnergyKcal = previousActivity.totalEnergyKcal,
                )
            }

        return calorieWidgetModel(
            today = today,
            eatenKcal = facts.energy.value ?: 0.0,
            burnedKcal = activity.totalEnergyKcal,
            baseGoalKcal = goal[NutritionFactsField.Energy],
            dietEnergyDeficitKcal = settings.dietEnergyDeficitKcal,
            previousDays = previousDays,
        )
    }

    private fun createRemoteViews(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        model: CalorieWidgetModel,
    ): RemoteViews {
        val layout = layout(manager, appWidgetId)
        return RemoteViews(context.packageName, layout).apply {
            setTextViewText(R.id.widget_calories_date, context.formatDate(model.date))
            setTextViewText(R.id.widget_calories_eaten, context.number(model.eatenKcal))
            setTextViewText(R.id.widget_calories_burned, context.number(model.burnedKcal))
            setTextViewText(R.id.widget_calories_left_normal, context.number(model.normalLeftKcal))
            setTextViewText(
                R.id.widget_calories_left_optimized,
                context.number(model.optimizedLeftKcal),
            )
            setTextViewText(
                R.id.widget_calories_left_diet,
                model.dietLeftKcal?.let { context.number(it) } ?: "--",
            )
            setInt(
                R.id.widget_calories_left_diet,
                "setTextColor",
                context.getColor(
                    if (model.dietLeftKcal == null) {
                        R.color.widget_calories_muted_text
                    } else {
                        R.color.widget_calories_text
                    }
                ),
            )
            setViewVisibility(
                R.id.widget_calories_diet_disabled,
                if (model.dietLeftKcal == null) View.VISIBLE else View.GONE,
            )
            setViewVisibility(
                R.id.widget_calories_diet_unit,
                if (model.dietLeftKcal == null) View.GONE else View.VISIBLE,
            )
            setOnClickPendingIntent(R.id.widget_calories_refresh, refreshPendingIntent(context))
        }
    }

    private fun layout(manager: AppWidgetManager, appWidgetId: Int): Int =
        if (isLargeWidget(manager, appWidgetId)) {
            R.layout.widget_calories_large
        } else {
            R.layout.widget_calories_medium
        }

    private fun isLargeWidget(manager: AppWidgetManager, appWidgetId: Int): Boolean {
        val options = manager.getAppWidgetOptions(appWidgetId)
        return options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) >= 160
    }

    private fun refreshPendingIntent(context: Context): PendingIntent {
        val flags =
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_IMMUTABLE
                } else {
                    0
                }
        return PendingIntent.getBroadcast(
            context,
            0,
            CalorieWidgetProvider.refreshIntent(context),
            flags,
        )
    }
}

private fun previousWeekDates(today: LocalDate): List<LocalDate> {
    val weekStart = today.startOfWeek()
    return List(today.dayOfWeek.ordinal) { weekStart.plus(it, DateTimeUnit.DAY) }
}

private fun Context.number(value: Int): String = NumberFormat.getIntegerInstance().format(value)

private fun Context.formatDate(date: LocalDate): String {
    val locale = Locale.getDefault()
    val formattedDate =
        DateTimeFormatter.ofPattern(
                if (locale.language == Locale.GERMAN.language) {
                    "d. MMM"
                } else {
                    "MMM d"
                },
                locale,
            )
            .format(date.toJavaLocalDate())
    return getString(R.string.widget_calories_today_format, formattedDate)
}

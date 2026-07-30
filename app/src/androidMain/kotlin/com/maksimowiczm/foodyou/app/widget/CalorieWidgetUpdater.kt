package com.maksimowiczm.foodyou.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import com.maksimowiczm.foodyou.R
import com.maksimowiczm.foodyou.activity.domain.repository.ActivityRepository
import com.maksimowiczm.foodyou.app.infrastructure.android.MainActivity
import com.maksimowiczm.foodyou.app.ui.home.goals.GoalEnergyOptimizationDay
import com.maksimowiczm.foodyou.app.ui.home.goals.startOfWeek
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.food.NutritionFactsField
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.fooddiary.domain.usecase.ObserveDiaryMealsUseCase
import com.maksimowiczm.foodyou.goals.domain.repository.GoalsRepository
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
import com.maksimowiczm.foodyou.settings.domain.entity.effectiveDietEnergyDeficitKcal
import com.maksimowiczm.foodyou.settings.domain.entity.effectiveTodayEnergyGoalAdjustment
import com.maksimowiczm.foodyou.settings.domain.entity.lockedDaySurplus
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
                    date = date,
                    consumedEnergyKcal = previousFacts.energy.value ?: 0.0,
                    baseEnergyGoalKcal = previousGoal[NutritionFactsField.Energy],
                    burnedEnergyKcal = previousActivity.totalEnergyKcal,
                    dietEnergyDeficitKcal =
                        settings.effectiveDietEnergyDeficitKcal(date) ?: 0.0,
                    lockedSurplusKcal = settings.lockedDaySurplus(date)?.surplusKcal,
                )
            }
        val plannedFutureDays =
            futureWeekDates(today).map { date ->
                val futureFacts = observeDiaryMealsUseCase.observeNutritionFacts(date).first()
                val futureGoal = goalsRepository.observeDailyGoals(date).first()
                val futureActivity =
                    activityRepository
                        .observeDailySummary(date, settings.stepsCaloriesPerStepKcal)
                        .first()

                GoalEnergyOptimizationDay(
                    date = date,
                    consumedEnergyKcal = futureFacts.energy.value ?: 0.0,
                    baseEnergyGoalKcal = futureGoal[NutritionFactsField.Energy],
                    burnedEnergyKcal = futureActivity.totalEnergyKcal,
                    dietEnergyDeficitKcal =
                        settings.effectiveDietEnergyDeficitKcal(date) ?: 0.0,
                    lockedSurplusKcal = settings.lockedDaySurplus(date)?.surplusKcal,
                )
            }

        return calorieWidgetModel(
            today = today,
            eatenKcal = facts.energy.value ?: 0.0,
            burnedKcal = activity.totalEnergyKcal,
            baseGoalKcal = goal[NutritionFactsField.Energy],
            dietEnergyDeficitKcal = settings.effectiveDietEnergyDeficitKcal(today),
            todayEnergyGoalReductionKcal =
                settings.effectiveTodayEnergyGoalAdjustment(today, today)?.reductionKcal,
            previousDays = previousDays,
            plannedFutureDays = plannedFutureDays,
        )
    }

    private fun createRemoteViews(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        model: CalorieWidgetModel,
    ): RemoteViews =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            RemoteViews(
                mapOf(
                    CalorieWidgetLayoutSelector.mediumSize to
                        createRemoteViews(context, R.layout.widget_calories_medium, model),
                    CalorieWidgetLayoutSelector.largeSize to
                        createRemoteViews(context, R.layout.widget_calories_large, model),
                )
            )
        } else {
            createRemoteViews(context, layout(manager, appWidgetId), model)
        }

    private fun createRemoteViews(context: Context, layout: Int, model: CalorieWidgetModel) =
        RemoteViews(context.packageName, layout).apply { setValues(context, model) }

    private fun RemoteViews.setValues(context: Context, model: CalorieWidgetModel) {
        setOnClickPendingIntent(
            R.id.widget_calories_root,
            calorieWidgetLaunchPendingIntent(context),
        )
        setTextViewText(R.id.widget_calories_date, context.formatDate(model.date))
        setTextViewText(R.id.widget_calories_eaten, context.number(model.eatenKcal))
        setTextViewText(R.id.widget_calories_burned, context.number(model.burnedKcal))
        setTextViewText(R.id.widget_calories_left_normal, context.number(model.normalLeftKcal))
        setTextViewText(
            R.id.widget_calories_left_optimized,
            model.optimizedLeftKcal?.let { context.number(it) } ?: "-",
        )
        setTextViewText(
            R.id.widget_calories_left_diet,
            model.dietLeftKcal?.let { context.number(it) } ?: "--",
        )
        setLeftColors(
            context = context,
            cardId = R.id.widget_calories_left_normal_card,
            valueId = R.id.widget_calories_left_normal,
            value = model.normalLeftKcal,
        )
        if (model.optimizedLeftKcal == null) {
            setInt(
                R.id.widget_calories_left_optimized,
                "setTextColor",
                context.getColor(R.color.widget_calories_muted_text),
            )
            setInt(
                R.id.widget_calories_left_optimized_card,
                "setBackgroundResource",
                R.drawable.widget_calories_left_optimized,
            )
        } else {
            setLeftColors(
                context = context,
                cardId = R.id.widget_calories_left_optimized_card,
                valueId = R.id.widget_calories_left_optimized,
                value = model.optimizedLeftKcal,
            )
        }
        if (model.dietLeftKcal == null) {
            setInt(
                R.id.widget_calories_left_diet,
                "setTextColor",
                context.getColor(R.color.widget_calories_muted_text),
            )
            setInt(
                R.id.widget_calories_left_diet_card,
                "setBackgroundResource",
                R.drawable.widget_calories_left_diet,
            )
        } else {
            setLeftColors(
                context = context,
                cardId = R.id.widget_calories_left_diet_card,
                valueId = R.id.widget_calories_left_diet,
                value = model.dietLeftKcal,
            )
        }
        setViewVisibility(
            R.id.widget_calories_diet_disabled,
            if (model.dietLeftKcal == null) View.VISIBLE else View.GONE,
        )
        setViewVisibility(
            R.id.widget_calories_diet_unit,
            if (model.dietLeftKcal == null) View.GONE else View.VISIBLE,
        )
    }

    private fun RemoteViews.setLeftColors(
        context: Context,
        cardId: Int,
        valueId: Int,
        value: Int,
    ) {
        val isOpen = value >= 0
        setInt(
            valueId,
            "setTextColor",
            context.getColor(
                if (isOpen) R.color.widget_calories_green else R.color.widget_calories_red
            ),
        )
        setInt(
            cardId,
            "setBackgroundResource",
            if (isOpen) {
                R.drawable.widget_calories_left_normal
            } else {
                R.drawable.widget_calories_left_negative
            },
        )
    }

    private fun layout(manager: AppWidgetManager, appWidgetId: Int): Int =
        CalorieWidgetLayoutSelector.layout(manager.getAppWidgetOptions(appWidgetId))
}

internal fun calorieWidgetLaunchPendingIntent(context: Context): PendingIntent =
    PendingIntent.getActivity(
        context,
        0,
        calorieWidgetLaunchIntent(context),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

internal fun calorieWidgetLaunchIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_MAIN
        addCategory(Intent.CATEGORY_LAUNCHER)
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

private fun previousWeekDates(today: LocalDate): List<LocalDate> {
    val weekStart = today.startOfWeek()
    return List(today.dayOfWeek.ordinal) { weekStart.plus(it, DateTimeUnit.DAY) }
}

private fun futureWeekDates(today: LocalDate): List<LocalDate> {
    val weekStart = today.startOfWeek()
    return List(6 - today.dayOfWeek.ordinal) {
        weekStart.plus(today.dayOfWeek.ordinal + it + 1, DateTimeUnit.DAY)
    }
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

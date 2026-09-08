package com.maksimowiczm.foodyou.app.widget

import com.maksimowiczm.foodyou.activity.domain.entity.DailyActivitySummary
import com.maksimowiczm.foodyou.activity.domain.repository.ActivityRepository
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.fooddiary.domain.repository.MealRepository
import com.maksimowiczm.foodyou.fooddiary.domain.usecase.ObserveDiaryMealsUseCase
import com.maksimowiczm.foodyou.goals.domain.entity.DailyGoal
import com.maksimowiczm.foodyou.goals.domain.repository.GoalsRepository
import com.maksimowiczm.foodyou.settings.domain.entity.AppLaunchInfo
import com.maksimowiczm.foodyou.settings.domain.entity.EnergyFormat
import com.maksimowiczm.foodyou.settings.domain.entity.GoalDisplayMode
import com.maksimowiczm.foodyou.settings.domain.entity.HomeCard
import com.maksimowiczm.foodyou.settings.domain.entity.NutrientsOrder
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
import java.lang.reflect.Proxy
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.junit.Test

class CalorieWidgetUpdaterTest {
    @Test
    fun loadsTodayCountedStepsAfterExclusionsAndRefreshesThem() = runTest {
        val today = LocalDate(2026, 9, 8)
        var summary = DailyActivitySummary(10_000, 1_568, 8_432, 34.0, 10.0, 44.0)
        val observedDates = mutableListOf<LocalDate>()
        val dateProvider = stub<DateProvider> { method, _ ->
            when (method) {
                "now" -> LocalDateTime(2026, 9, 8, 12, 0)
                else -> error("Unexpected date call: $method")
            }
        }
        val meals = stub<MealRepository> { method, _ ->
            check(method == "observeMeals")
            flowOf(emptyList<Nothing>())
        }
        val activities = stub<ActivityRepository> { method, arguments ->
            check(method == "observeDailySummary")
            val date = arguments[0] as LocalDate
            observedDates += date
            flowOf(if (date == today) summary else DailyActivitySummary(99_999, 0, 99_999, 0.0, 0.0, 0.0))
        }
        val updater = CalorieWidgetUpdater(
            observeDiaryMealsUseCase = ObserveDiaryMealsUseCase(
                meals, unused(), unused(), unused(), dateProvider,
            ),
            goalsRepository = stub<GoalsRepository> { method, _ ->
                check(method == "observeDailyGoals")
                flowOf(DailyGoal.defaultGoals)
            },
            activityRepository = activities,
            settingsRepository = stub<UserPreferencesRepository<Settings>> { method, _ ->
                check(method == "observe")
                flowOf(defaultSettings())
            },
            dateProvider = dateProvider,
        )

        assertEquals(8_432L, updater.loadModel().countedSteps)
        assertEquals(1, observedDates.count { it == today })
        summary = summary.copy(excludedSteps = 10_000, countedSteps = 0)
        assertEquals(0L, updater.loadModel().countedSteps)
        summary = DailyActivitySummary(0, 0, 0, 0.0, 0.0, 0.0)
        assertEquals(0L, updater.loadModel().countedSteps)
        summary = DailyActivitySummary(14_000, 1_655, 12_345, 0.0, 0.0, 0.0)
        assertEquals(12_345L, updater.loadModel().countedSteps)
    }

    private fun defaultSettings(): Settings =
        Settings(
            lastRememberedVersion = null,
            hidePreviewDialog = false,
            showTranslationWarning = false,
            nutrientsOrder = NutrientsOrder.defaultOrder,
            secureScreen = false,
            homeCardOrder = HomeCard.defaultOrder,
            expandGoalCard = false,
            goalDisplayMode = GoalDisplayMode.Normal,
            dietEnergyDeficitKcal = null,
            onboardingFinished = true,
            energyFormat = EnergyFormat.DEFAULT,
            appLaunchInfo =
                AppLaunchInfo(
                    firstLaunch = null,
                    firstLaunchCurrentVersion = null,
                    launchesCount = 0,
                ),
            stepsCaloriesPerStepKcal = null,
            healthConnectStepsEnabled = true,
            healthConnectStepsLastSyncedEpochSeconds = null,
        )

    private inline fun <reified T> unused(): T = stub { method, _ ->
        error("Unexpected dependency call: $method")
    }

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> stub(
        crossinline answer: (String, Array<out Any?>) -> Any?,
    ): T = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
        answer(method.name, args ?: emptyArray())
    } as T
}

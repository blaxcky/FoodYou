package com.maksimowiczm.foodyou.app.ui.home.goals

import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.activity.domain.entity.DailyActivitySummary
import com.maksimowiczm.foodyou.activity.domain.entity.DailyStepSummary
import com.maksimowiczm.foodyou.activity.domain.entity.ManualActivityEntry
import com.maksimowiczm.foodyou.activity.domain.entity.ManualActivityEntryId
import com.maksimowiczm.foodyou.activity.domain.entity.StepExclusionPeriod
import com.maksimowiczm.foodyou.activity.domain.repository.ActivityRepository
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.food.NutrientValue
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferences
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFood
import com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntry
import com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntryId
import com.maksimowiczm.foodyou.fooddiary.domain.entity.ManualDiaryEntry
import com.maksimowiczm.foodyou.fooddiary.domain.entity.ManualDiaryEntryId
import com.maksimowiczm.foodyou.fooddiary.domain.entity.Meal
import com.maksimowiczm.foodyou.fooddiary.domain.entity.MealsCardsLayout
import com.maksimowiczm.foodyou.fooddiary.domain.entity.MealsPreferences
import com.maksimowiczm.foodyou.fooddiary.domain.repository.FoodDiaryEntryRepository
import com.maksimowiczm.foodyou.fooddiary.domain.repository.ManualDiaryEntryRepository
import com.maksimowiczm.foodyou.fooddiary.domain.repository.MealRepository
import com.maksimowiczm.foodyou.fooddiary.domain.usecase.ObserveDiaryMealsUseCase
import com.maksimowiczm.foodyou.goals.domain.entity.DailyGoal
import com.maksimowiczm.foodyou.goals.domain.entity.MacronutrientGoal
import com.maksimowiczm.foodyou.goals.domain.entity.WeeklyGoals
import com.maksimowiczm.foodyou.goals.domain.repository.GoalsRepository
import com.maksimowiczm.foodyou.settings.domain.entity.AppLaunchInfo
import com.maksimowiczm.foodyou.settings.domain.entity.EnergyFormat
import com.maksimowiczm.foodyou.settings.domain.entity.GoalDisplayMode
import com.maksimowiczm.foodyou.settings.domain.entity.HomeCard
import com.maksimowiczm.foodyou.settings.domain.entity.LockedDaySurplus
import com.maksimowiczm.foodyou.settings.domain.entity.NutrientsOrder
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
import com.maksimowiczm.foodyou.app.ui.home.goals.GoalDisplayMode as CardGoalDisplayMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone

class GoalsViewModelTest {
    private var viewModel: GoalsViewModel? = null

    @Test
    fun hidingModeSwitchingUsesNormalAndRestoresSavedModeWithoutChangingGoals() = runViewModelTest {
        for (savedMode in listOf(GoalDisplayMode.Optimized, GoalDisplayMode.Diet)) {
            val repository = InMemoryPreferencesRepository(
                defaultSettings().copy(goalDisplayMode = savedMode, dietEnergyDeficitKcal = 500.0)
            )
            val model = createViewModel(settingsRepository = repository)
            model.setDate(Today)
            val original = model.model.first { it != null }!!
            assertEquals(CardGoalDisplayMode.valueOf(savedMode.name), original.goalDisplayMode)
            for (supplemental in listOf(true, false)) {
                repository.update {
                    copy(goalCardModeSwitchingEnabled = false, supplementalGoalsEnabled = supplemental)
                }
                val hidden = model.model.first {
                    it != null && !it.goalCardModeSwitchingEnabled && it.supplementalGoalsEnabled == supplemental
                }!!
                assertEquals(CardGoalDisplayMode.Normal, hidden.goalDisplayMode)
                assertEquals(
                    original.goalDisplaySummaries.first { it.mode == CardGoalDisplayMode.Normal }.energyGoal,
                    hidden.energyGoal,
                )
                assertEquals(original.goalDisplaySummaries, hidden.goalDisplaySummaries)
                assertEquals(savedMode, repository.observe().first().goalDisplayMode)
            }
            repository.update { copy(goalCardModeSwitchingEnabled = true) }
            val restored = model.model.first { it?.goalCardModeSwitchingEnabled == true }!!
            assertEquals(original.goalDisplayMode, restored.goalDisplayMode)
            assertEquals(original.goalDisplaySummaries, restored.goalDisplaySummaries)
            assertEquals(false, restored.supplementalGoalsEnabled)
            model.viewModelScope.cancel()
        }
    }

    @Test
    fun setDatePreloadsWeekModelWithoutUiCollector() = runViewModelTest {
        val viewModel = createViewModel()

        viewModel.setDate(Today)
        advanceUntilIdle()

        assertNotNull(viewModel.weekModel.value)
    }

    @Test
    fun preloadedWeekModelKeepsDailyRowsAndTotals() = runViewModelTest {
        val viewModel = createViewModel()

        viewModel.setDate(Today)
        advanceUntilIdle()

        val model = assertNotNull(viewModel.weekModel.value)
        assertEquals(
            listOf(
                WeekDaySummaryModel(LocalDate(2026, 7, 13), energy = 400, goal = 2100),
                WeekDaySummaryModel(LocalDate(2026, 7, 14), energy = 500, goal = 2200),
                WeekDaySummaryModel(LocalDate(2026, 7, 15), energy = 600, goal = 2300),
            ),
            model.days,
        )
        assertEquals(1_500, model.totalEnergy)
        assertEquals(6_600, model.totalGoal)
        assertEquals(Today, model.today)
    }

    @Test
    fun lockedDayUsesSimulatedWeeklyEnergyAndExactDifference() = runViewModelTest {
        val viewModel =
            createViewModel(
                defaultSettings().copy(
                    lockedDaySurpluses =
                        listOf(LockedDaySurplus(LocalDate(2026, 7, 14), 500.0))
                )
            )

        viewModel.setDate(Today)
        advanceUntilIdle()

        val model = assertNotNull(viewModel.weekModel.value)
        assertEquals(
            WeekDaySummaryModel(
                date = LocalDate(2026, 7, 14),
                energy = 2_700,
                goal = 2_200,
                locked = true,
            ),
            model.days[1],
        )
        assertEquals(3_700, model.totalEnergy)
        assertEquals(6_600, model.totalGoal)
    }

    @Test
    fun lockEditAndUnlockPreserveConfiguredDefault() = runViewModelTest {
        val repository =
            InMemoryPreferencesRepository(defaultSettings().copy(defaultLockedDaySurplusKcal = 650.0))
        val viewModel = createViewModel(settingsRepository = repository)
        val date = LocalDate(2026, 7, 17)

        viewModel.lockDay(date, 500.0)
        advanceUntilIdle()
        viewModel.lockDay(date, 250.0)
        advanceUntilIdle()

        assertEquals(650.0, repository.observe().first().defaultLockedDaySurplusKcal)
        assertEquals(
            listOf(LockedDaySurplus(date, 250.0)),
            repository.observe().first().lockedDaySurpluses,
        )

        viewModel.unlockDay(date)
        advanceUntilIdle()
        assertEquals(emptyList(), repository.observe().first().lockedDaySurpluses)
    }

    private fun runViewModelTest(block: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            block()
        } finally {
            viewModel?.viewModelScope?.cancel()
            viewModel = null
            advanceUntilIdle()
            Dispatchers.resetMain()
        }
    }

    private fun createViewModel(
        settings: Settings = defaultSettings(),
        settingsRepository: InMemoryPreferencesRepository<Settings> =
            InMemoryPreferencesRepository(settings),
    ): GoalsViewModel {
        val dateProvider = FixedDateProvider(Today)
        return GoalsViewModel(
                settingsRepository = settingsRepository,
                observeDiaryMealsUseCase =
                    ObserveDiaryMealsUseCase(
                        mealRepository = SingleMealRepository,
                        mealsPreferencesRepository =
                            InMemoryPreferencesRepository(
                                MealsPreferences(
                                    layout = MealsCardsLayout.default,
                                    useTimeBasedSorting = false,
                                    ignoreAllDayMeals = false,
                                )
                            ),
                        foodEntryRepository = EmptyFoodDiaryEntryRepository,
                        manualEntryRepository = EnergyDiaryEntryRepository,
                        dateProvider = dateProvider,
                    ),
                goalsRepository = FixedGoalsRepository,
                activityRepository = FixedActivityRepository,
                dateProvider = dateProvider,
            )
            .also { viewModel = it }
    }

    private class InMemoryPreferencesRepository<P : UserPreferences>(initialValue: P) :
        UserPreferencesRepository<P> {
        private val state = MutableStateFlow(initialValue)

        override fun observe(): Flow<P> = state

        override suspend fun update(transform: P.() -> P) {
            state.value = state.value.transform()
        }
    }

    private class FixedDateProvider(private val date: LocalDate) : DateProvider {
        override fun nowInstant(): Instant = Instant.parse("2026-07-15T12:00:00Z")

        override fun observeInstant(interval: Duration): Flow<Instant> = flowOf(nowInstant())

        override fun observeDate(timeZone: TimeZone): Flow<LocalDate> = flowOf(date)
    }

    private object FixedGoalsRepository : GoalsRepository {
        override suspend fun updateWeeklyGoals(weeklyGoals: WeeklyGoals) = error("Not used")

        override fun observeWeeklyGoals(): Flow<WeeklyGoals> = error("Not used")

        override fun observeDailyGoals(date: LocalDate): Flow<DailyGoal> =
            flowOf(
                DailyGoal(
                    macronutrientGoal =
                        MacronutrientGoal.Manual(
                            energyKcal = 2_000.0,
                            proteinsGrams = 100.0,
                            fatsGrams = 70.0,
                            carbohydratesGrams = 250.0,
                        ),
                    map = emptyMap(),
                )
            )
    }

    private object FixedActivityRepository : ActivityRepository {
        override fun observeDailySummary(
            date: LocalDate,
            kcalPerStep: Double?,
        ): Flow<DailyActivitySummary> {
            val energy = (date.day - 12) * 100.0
            return flowOf(DailyActivitySummary(0, 0, 0, 0.0, energy, energy))
        }

        override fun observeManualEntry(id: ManualActivityEntryId): Flow<ManualActivityEntry?> =
            error("Not used")

        override fun observeManualEntries(date: LocalDate): Flow<List<ManualActivityEntry>> =
            error("Not used")

        override suspend fun createManualEntry(entry: ManualActivityEntry): ManualActivityEntryId =
            error("Not used")

        override suspend fun updateManualEntry(entry: ManualActivityEntry) = error("Not used")

        override suspend fun deleteManualEntry(id: ManualActivityEntryId) = error("Not used")

        override suspend fun upsertStepSummary(summary: DailyStepSummary) = error("Not used")

        override fun observeStepExclusionPeriods(date: LocalDate): Flow<List<StepExclusionPeriod>> =
            error("Not used")

        override suspend fun replaceStepExclusionPeriods(
            date: LocalDate,
            periods: List<StepExclusionPeriod>,
        ) = error("Not used")
    }

    private object SingleMealRepository : MealRepository {
        private val meal = Meal(1, "Meal", LocalTime(0, 0), LocalTime(0, 0), 0)

        override fun observeMeal(mealId: Long): Flow<Meal?> = flowOf(meal)

        override fun observeMeals(): Flow<List<Meal>> = flowOf(listOf(meal))

        override suspend fun insertMealWithLastRank(name: String, from: LocalTime, to: LocalTime) =
            error("Not used")

        override suspend fun deleteMeal(mealId: Long) = error("Not used")

        override suspend fun updateMeal(id: Long, name: String, from: LocalTime, to: LocalTime) =
            error("Not used")

        override suspend fun reorderMeals(order: List<Long>) = error("Not used")
    }

    private object EnergyDiaryEntryRepository : ManualDiaryEntryRepository {
        override fun observeAll(mealId: Long, date: LocalDate): Flow<List<ManualDiaryEntry>> {
            val energy = (date.day - 9) * 100.0
            val timestamp = LocalDateTime(date, LocalTime(12, 0))
            return flowOf(
                listOf(
                    ManualDiaryEntry(
                        id = ManualDiaryEntryId(date.day.toLong()),
                        mealId = mealId,
                        date = date,
                        name = "Energy",
                        nutritionFacts =
                            NutritionFacts(energy = NutrientValue.Complete(energy)),
                        createdAt = timestamp,
                        updatedAt = timestamp,
                    )
                )
            )
        }

        override fun observe(id: ManualDiaryEntryId): Flow<ManualDiaryEntry?> = error("Not used")

        override suspend fun insert(
            name: String,
            mealId: Long,
            date: LocalDate,
            nutritionFacts: NutritionFacts,
            createdAt: LocalDateTime,
        ): ManualDiaryEntryId = error("Not used")

        override suspend fun update(entry: ManualDiaryEntry) = error("Not used")

        override suspend fun delete(id: ManualDiaryEntryId) = error("Not used")
    }

    private object EmptyFoodDiaryEntryRepository : FoodDiaryEntryRepository {
        override fun observeAll(mealId: Long, date: LocalDate): Flow<List<FoodDiaryEntry>> =
            flowOf(emptyList())

        override fun observe(id: FoodDiaryEntryId): Flow<FoodDiaryEntry?> = error("Not used")

        override suspend fun insert(
            measurement: Measurement,
            mealId: Long,
            date: LocalDate,
            food: DiaryFood,
            createdAt: LocalDateTime,
        ): FoodDiaryEntryId = error("Not used")

        override suspend fun update(entry: FoodDiaryEntry) = error("Not used")

        override suspend fun delete(id: FoodDiaryEntryId) = error("Not used")
    }

    private companion object {
        val Today = LocalDate(2026, 7, 15)

        fun defaultSettings() =
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
                appLaunchInfo = AppLaunchInfo(null, null, 0),
                stepsCaloriesPerStepKcal = null,
                healthConnectStepsEnabled = false,
                healthConnectStepsLastSyncedEpochSeconds = null,
            )
    }
}

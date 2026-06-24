package com.maksimowiczm.foodyou.app.ui.home.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.activity.domain.repository.ActivityRepository
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.food.NutritionFactsField
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.fooddiary.domain.usecase.ObserveDiaryMealsUseCase
import com.maksimowiczm.foodyou.goals.domain.repository.GoalsRepository
import com.maksimowiczm.foodyou.settings.domain.entity.GoalDisplayMode as SettingsGoalDisplayMode
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus

internal class GoalsViewModel(
    private val settingsRepository: UserPreferencesRepository<Settings>,
    private val observeDiaryMealsUseCase: ObserveDiaryMealsUseCase,
    private val goalsRepository: GoalsRepository,
    private val activityRepository: ActivityRepository,
    private val dateProvider: DateProvider,
) : ViewModel() {

    private val dateState = MutableStateFlow<LocalDate?>(null)

    fun setDate(date: LocalDate) {
        dateState.value = date
    }

    private val _expandGoalsCard = settingsRepository.observe().map { it.expandGoalCard }
    val expandGoalsCard: StateFlow<Boolean> =
        _expandGoalsCard.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(2_000),
            initialValue = runBlocking { _expandGoalsCard.first() },
        )

    fun setExpandGoalsCard(expand: Boolean) {
        viewModelScope.launch { settingsRepository.update { copy(expandGoalCard = expand) } }
    }

    fun setGoalDisplayMode(goalDisplayMode: SettingsGoalDisplayMode) {
        viewModelScope.launch {
            settingsRepository.update { copy(goalDisplayMode = goalDisplayMode) }
        }
    }

    val model: StateFlow<DaySummaryModel?> =
        combine(dateState.filterNotNull(), dateProvider.observeDate(), settingsRepository.observe()) {
                selectedDate,
                today,
                settings ->
                Triple(selectedDate, today, settings)
            }
            .flatMapLatest { (date, today, settings) ->
                val currentWeek = date.startOfWeek() == today.startOfWeek()
                val dietEnergyDeficitKcal = settings.dietEnergyDeficitKcal?.takeIf { it > 0.0 }
                val availableGoalDisplayModes =
                    if (currentWeek) {
                        if (dietEnergyDeficitKcal != null) {
                            listOf(GoalDisplayMode.Normal, GoalDisplayMode.Optimized, GoalDisplayMode.Diet)
                        } else {
                            listOf(GoalDisplayMode.Normal, GoalDisplayMode.Optimized)
                        }
                    } else {
                        listOf(GoalDisplayMode.Normal)
                    }
                val goalDisplayMode =
                    when {
                        !currentWeek -> GoalDisplayMode.Normal
                        settings.goalDisplayMode == SettingsGoalDisplayMode.Optimized ->
                            GoalDisplayMode.Optimized
                        settings.goalDisplayMode == SettingsGoalDisplayMode.Diet &&
                            dietEnergyDeficitKcal != null -> GoalDisplayMode.Diet
                        else -> GoalDisplayMode.Normal
                    }
                val selectedDay =
                    combine(
                        observeDiaryMealsUseCase.observeNutritionFacts(date),
                        goalsRepository.observeDailyGoals(date),
                        activityRepository.observeDailySummary(
                            date,
                            settings.stepsCaloriesPerStepKcal,
                        ),
                    ) { facts, goal, activity ->
                        val consumedEnergy = facts.energy.value ?: 0.0
                        val burnedEnergy = activity.totalEnergyKcal
                        val baseEnergyGoal = goal[NutritionFactsField.Energy]

                        SelectedGoalDay(
                            consumedEnergy = consumedEnergy,
                            burnedEnergy = burnedEnergy,
                            baseEnergyGoal = baseEnergyGoal,
                            proteins = facts.proteins.value?.roundToInt() ?: 0,
                            proteinsGoal = goal[NutritionFactsField.Proteins].roundToInt(),
                            carbohydrates = facts.carbohydrates.value?.roundToInt() ?: 0,
                            carbohydratesGoal =
                                goal[NutritionFactsField.Carbohydrates].roundToInt(),
                            fats = facts.fats.value?.roundToInt() ?: 0,
                            fatsGoal = goal[NutritionFactsField.Fats].roundToInt(),
                        )
                    }
                val previousDays =
                    if (availableGoalDisplayModes.any { it != GoalDisplayMode.Normal }) {
                        val weekStart = today.startOfWeek()
                        List(today.dayOfWeek.isoDayNumber - 1) {
                            weekStart.plus(it, DateTimeUnit.DAY)
                        }
                    } else {
                        emptyList()
                    }
                val plannedFutureDays =
                    if (availableGoalDisplayModes.any { it != GoalDisplayMode.Normal }) {
                        List(7 - today.dayOfWeek.isoDayNumber) {
                            today.plus(it + 1, DateTimeUnit.DAY)
                        }
                    } else {
                        emptyList()
                    }
                val previousDaySummaries =
                    if (previousDays.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        combine(
                            previousDays.map { previousDate ->
                                combine(
                                    observeDiaryMealsUseCase.observeNutritionFacts(previousDate),
                                    goalsRepository.observeDailyGoals(previousDate),
                                    activityRepository.observeDailySummary(
                                        previousDate,
                                        settings.stepsCaloriesPerStepKcal,
                                    ),
                                ) { facts, goal, activity ->
                                    GoalEnergyOptimizationDay(
                                        date = previousDate,
                                        consumedEnergyKcal = facts.energy.value ?: 0.0,
                                        baseEnergyGoalKcal = goal[NutritionFactsField.Energy],
                                        burnedEnergyKcal = activity.totalEnergyKcal,
                                    )
                                }
                            }
                        ) {
                            it.toList()
                        }
                    }

                val plannedFutureDaySummaries =
                    if (plannedFutureDays.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        combine(
                            plannedFutureDays.map { futureDate ->
                                combine(
                                    observeDiaryMealsUseCase.observeNutritionFacts(futureDate),
                                    goalsRepository.observeDailyGoals(futureDate),
                                    activityRepository.observeDailySummary(
                                        futureDate,
                                        settings.stepsCaloriesPerStepKcal,
                                    ),
                                ) { facts, goal, activity ->
                                    GoalEnergyOptimizationDay(
                                        date = futureDate,
                                        consumedEnergyKcal = facts.energy.value ?: 0.0,
                                        baseEnergyGoalKcal = goal[NutritionFactsField.Energy],
                                        burnedEnergyKcal = activity.totalEnergyKcal,
                                    )
                                }
                            }
                        ) { it.toList() }
                    }

                combine(selectedDay, previousDaySummaries, plannedFutureDaySummaries) {
                        day,
                        previous,
                        plannedFuture ->
                    val goalDisplaySummaries =
                        availableGoalDisplayModes.map { mode ->
                            mode.summary(
                                selectedDate = date,
                                today = today,
                                baseEnergyGoalKcal = day.baseEnergyGoal,
                                dietEnergyDeficitKcal = dietEnergyDeficitKcal,
                                previousDays = previous,
                                plannedFutureDays = plannedFuture,
                            )
                        }
                    val selectedGoalDisplaySummary =
                        goalDisplaySummaries.firstOrNull { it.mode == goalDisplayMode }
                            ?: goalDisplaySummaries.first()

                    DaySummaryModel(
                        energy = roundedEnergyKcal(day.consumedEnergy),
                        burnedEnergy = roundedEnergyKcal(day.burnedEnergy),
                        netEnergy = roundedNetEnergyKcal(day.consumedEnergy, day.burnedEnergy),
                        energyGoal = selectedGoalDisplaySummary.energyGoal,
                        showEnergyGoalValue = selectedGoalDisplaySummary.showEnergyGoalValue,
                        goalDisplayMode = goalDisplayMode,
                        goalDisplaySummaries = goalDisplaySummaries,
                        dietGoalDisplayModeEnabled = dietEnergyDeficitKcal != null,
                        proteins = day.proteins,
                        proteinsGoal = day.proteinsGoal,
                        carbohydrates = day.carbohydrates,
                        carbohydratesGoal = day.carbohydratesGoal,
                        fats = day.fats,
                        fatsGoal = day.fatsGoal,
                    )
                }
            }
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(2_000),
                initialValue = null,
            )

    val weekModel: StateFlow<WeekSummaryModel?> =
        combine(dateState.filterNotNull(), dateProvider.observeDate(), settingsRepository.observe()) {
                selectedDate,
                today,
                settings ->
                Triple(selectedDate, today, settings.stepsCaloriesPerStepKcal)
            }
            .flatMapLatest { (selectedDate, today, kcalPerStep) ->
                val dates = selectedDate.weekDatesUntil(today)
                if (dates.isEmpty()) {
                    return@flatMapLatest flowOf(
                        WeekSummaryModel(
                            days = emptyList(),
                            totalEnergy = 0,
                            totalGoal = 0,
                            today = today,
                        )
                    )
                }
                val dayFlows =
                    dates.map { date ->
                        combine(
                            observeDiaryMealsUseCase.observeNutritionFacts(date),
                            goalsRepository.observeDailyGoals(date),
                            activityRepository.observeDailySummary(date, kcalPerStep),
                        ) { facts, goal, activity ->
                            val consumedEnergy = facts.energy.value ?: 0.0
                            val baseGoal = goal[NutritionFactsField.Energy]
                            val burnedEnergy = activity.totalEnergyKcal
                            WeekDaySummaryModel(
                                date = date,
                                energy = roundedEnergyKcal(consumedEnergy),
                                goal =
                                    roundedEnergyKcal(baseGoal) + roundedEnergyKcal(burnedEnergy),
                            )
                        }
                    }

                combine(dayFlows) { days ->
                    val summaries = days.toList()
                    WeekSummaryModel(
                        days = summaries,
                        totalEnergy = summaries.sumOf { it.energy },
                        totalGoal = summaries.sumOf { it.goal },
                        today = today,
                    )
                }
            }
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(2_000),
                initialValue = null,
            )
}

internal fun LocalDate.weekDatesUntil(today: LocalDate): List<LocalDate> {
    val weekStart = startOfWeek()
    return List(7) { weekStart.plus(it, DateTimeUnit.DAY) }.takeWhile { it <= today }
}

private data class SelectedGoalDay(
    val consumedEnergy: Double,
    val burnedEnergy: Double,
    val baseEnergyGoal: Double,
    val proteins: Int,
    val proteinsGoal: Int,
    val carbohydrates: Int,
    val carbohydratesGoal: Int,
    val fats: Int,
    val fatsGoal: Int,
)

internal fun percentageEnergyGoalKcal(energyGoalKcal: Double, baseEnergyGoalKcal: Double): Int =
    if (energyGoalKcal > 0.0) {
        roundedEnergyKcal(energyGoalKcal)
    } else {
        roundedEnergyKcal(baseEnergyGoalKcal)
    }

private fun GoalDisplayMode.summary(
    selectedDate: LocalDate,
    today: LocalDate,
    baseEnergyGoalKcal: Double,
    dietEnergyDeficitKcal: Double?,
    previousDays: List<GoalEnergyOptimizationDay>,
    plannedFutureDays: List<GoalEnergyOptimizationDay>,
): GoalDisplaySummaryModel {
    val energyGoal =
        if (this != GoalDisplayMode.Normal) {
            adjustedEnergyGoalKcal(
                selectedDate = selectedDate,
                today = today,
                baseEnergyGoalKcal = baseEnergyGoalKcal,
                dailyEnergyDeficitKcal =
                    if (this == GoalDisplayMode.Diet) {
                        dietEnergyDeficitKcal ?: 0.0
                    } else {
                        0.0
                    },
                previousDays = previousDays,
                plannedFutureDays = plannedFutureDays,
            )
        } else {
            baseEnergyGoalKcal
        }

    val showEnergyGoalValue =
        when (this) {
            GoalDisplayMode.Normal -> true
            GoalDisplayMode.Optimized,
            GoalDisplayMode.Diet ->
                energyGoalDiffersFromBase(
                    baseEnergyGoalKcal = baseEnergyGoalKcal,
                    adjustedEnergyGoalKcal = energyGoal,
                )
        }

    return GoalDisplaySummaryModel(
        mode = this,
        energyGoal = roundedEnergyKcal(energyGoal),
        showEnergyGoalValue = showEnergyGoalValue,
        percentageEnergyGoal =
            percentageEnergyGoalKcal(
                energyGoalKcal = energyGoal,
                baseEnergyGoalKcal = baseEnergyGoalKcal,
            ),
    )
}

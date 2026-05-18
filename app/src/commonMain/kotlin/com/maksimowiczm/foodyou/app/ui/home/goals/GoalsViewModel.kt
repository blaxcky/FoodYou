package com.maksimowiczm.foodyou.app.ui.home.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.activity.domain.repository.ActivityRepository
import com.maksimowiczm.foodyou.activity.domain.usecase.calculateNetEnergyKcal
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.food.NutritionFactsField
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.fooddiary.domain.usecase.ObserveDiaryMealsUseCase
import com.maksimowiczm.foodyou.goals.domain.repository.GoalsRepository
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

    fun toggleOptimizedGoalDisplay() {
        viewModelScope.launch {
            settingsRepository.update {
                copy(optimizedGoalDisplayEnabled = !optimizedGoalDisplayEnabled)
            }
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
                val optimizedDisplay = settings.optimizedGoalDisplayEnabled && currentWeek
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
                    if (optimizedDisplay) {
                        val weekStart = date.startOfWeek()
                        List(date.dayOfWeek.isoDayNumber - 1) { weekStart.plus(it, DateTimeUnit.DAY) }
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

                combine(selectedDay, previousDaySummaries) { day, previous ->
                    val energyGoal =
                        if (optimizedDisplay) {
                            optimizedEnergyGoalKcal(
                                selectedDate = date,
                                today = today,
                                baseEnergyGoalKcal = day.baseEnergyGoal,
                                previousDays = previous,
                            )
                        } else {
                            day.baseEnergyGoal
                        }

                    DaySummaryModel(
                        energy = day.consumedEnergy.roundToInt(),
                        burnedEnergy = day.burnedEnergy.roundToInt(),
                        netEnergy =
                            calculateNetEnergyKcal(day.consumedEnergy, day.burnedEnergy)
                                .roundToInt(),
                        energyGoal = energyGoal.roundToInt(),
                        optimizedGoalDisplayEnabled = optimizedDisplay,
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
                val dates = selectedDate.weekDates(today)
                val dayFlows =
                    dates.map { date ->
                        combine(
                            observeDiaryMealsUseCase.observeNutritionFacts(date),
                            goalsRepository.observeDailyGoals(date),
                            activityRepository.observeDailySummary(date, kcalPerStep),
                        ) { facts, goal, activity ->
                            val consumedEnergy = facts.energy.value ?: 0.0
                            val baseGoal = goal[NutritionFactsField.Energy]
                            val adjustedGoal = baseGoal + activity.totalEnergyKcal
                            WeekDaySummaryModel(
                                date = date,
                                energy = consumedEnergy.roundToInt(),
                                goal = adjustedGoal.roundToInt(),
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

private fun LocalDate.weekDates(today: LocalDate): List<LocalDate> {
    val weekStart = startOfWeek()
    val currentWeekStart = today.startOfWeek()
    val dayCount =
        if (weekStart == currentWeekStart) {
            today.dayOfWeek.isoDayNumber
        } else {
            7
        }

    return List(dayCount) { weekStart.plus(it, DateTimeUnit.DAY) }
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

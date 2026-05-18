package com.maksimowiczm.foodyou.app.ui.home.goals

import com.maksimowiczm.foodyou.activity.domain.repository.ActivityRepository
import com.maksimowiczm.foodyou.activity.domain.usecase.calculateNetEnergyKcal
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
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

    val model: StateFlow<DaySummaryModel?> =
        dateState
            .filterNotNull()
            .flatMapLatest { date ->
                combine(
                    observeDiaryMealsUseCase.observeNutritionFacts(date),
                    goalsRepository.observeDailyGoals(date),
                    settingsRepository.observe().flatMapLatest { settings ->
                        activityRepository.observeDailySummary(
                            date,
                            settings.stepsCaloriesPerStepKcal,
                        )
                    },
                ) { facts, goal, activity ->
                    val consumedEnergy = facts.energy.value ?: 0.0
                    val burnedEnergy = activity.totalEnergyKcal

                    DaySummaryModel(
                        energy = consumedEnergy.roundToInt(),
                        burnedEnergy = burnedEnergy.roundToInt(),
                        netEnergy = calculateNetEnergyKcal(consumedEnergy, burnedEnergy).roundToInt(),
                        energyGoal = goal[NutritionFactsField.Energy].roundToInt(),
                        proteins = facts.proteins.value?.roundToInt() ?: 0,
                        proteinsGoal = goal[NutritionFactsField.Proteins].roundToInt(),
                        carbohydrates = facts.carbohydrates.value?.roundToInt() ?: 0,
                        carbohydratesGoal = goal[NutritionFactsField.Carbohydrates].roundToInt(),
                        fats = facts.fats.value?.roundToInt() ?: 0,
                        fatsGoal = goal[NutritionFactsField.Fats].roundToInt(),
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
                            val netEnergy =
                                calculateNetEnergyKcal(consumedEnergy, activity.totalEnergyKcal)
                            WeekDaySummaryModel(
                                date = date,
                                energy = netEnergy.roundToInt(),
                                goal = goal[NutritionFactsField.Energy].roundToInt(),
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

private fun LocalDate.startOfWeek(): LocalDate =
    minus(dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)

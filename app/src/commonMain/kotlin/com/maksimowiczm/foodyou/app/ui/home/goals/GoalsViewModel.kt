package com.maksimowiczm.foodyou.app.ui.home.goals

import com.maksimowiczm.foodyou.activity.domain.repository.ActivityRepository
import com.maksimowiczm.foodyou.activity.domain.usecase.calculateNetEnergyKcal
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.common.domain.food.NutritionFactsField
import com.maksimowiczm.foodyou.common.domain.food.sum
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.fooddiary.domain.usecase.ObserveDiaryMealsUseCase
import com.maksimowiczm.foodyou.goals.domain.repository.GoalsRepository
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate

internal class GoalsViewModel(
    private val settingsRepository: UserPreferencesRepository<Settings>,
    private val observeDiaryMealsUseCase: ObserveDiaryMealsUseCase,
    private val goalsRepository: GoalsRepository,
    private val activityRepository: ActivityRepository,
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
                    observeDiaryMealsUseCase.observe(date),
                    goalsRepository.observeDailyGoals(date),
                    settingsRepository.observe().flatMapLatest { settings ->
                        activityRepository.observeDailySummary(
                            date,
                            settings.stepsCaloriesPerStepKcal,
                        )
                    },
                ) { meals, goal, activity ->
                    val facts = meals.map { it.nutritionFacts }.sum()
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
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(2_000),
                initialValue = null,
            )
}

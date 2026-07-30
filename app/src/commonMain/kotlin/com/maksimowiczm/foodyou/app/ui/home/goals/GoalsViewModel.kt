package com.maksimowiczm.foodyou.app.ui.home.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.activity.domain.repository.ActivityRepository
import com.maksimowiczm.foodyou.app.widget.updateCalorieWidgetValues
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.food.NutritionFactsField
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.fooddiary.domain.usecase.ObserveDiaryMealsUseCase
import com.maksimowiczm.foodyou.goals.domain.repository.GoalsRepository
import com.maksimowiczm.foodyou.settings.domain.entity.GoalDisplayMode as SettingsGoalDisplayMode
import com.maksimowiczm.foodyou.settings.domain.entity.LockedDaySurplus
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
import com.maksimowiczm.foodyou.settings.domain.entity.TodayEnergyGoalAdjustment
import com.maksimowiczm.foodyou.settings.domain.entity.effectiveDietEnergyDeficitKcal
import com.maksimowiczm.foodyou.settings.domain.entity.effectiveTodayEnergyGoalAdjustment
import com.maksimowiczm.foodyou.settings.domain.entity.lockedDaySurplus
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

    fun setTodayEnergyGoalReduction(reductionKcal: Double) {
        if (reductionKcal <= 0.0) return
        viewModelScope.launch {
            val today = dateProvider.now().date
            settingsRepository.update {
                copy(
                    todayEnergyGoalAdjustment =
                        TodayEnergyGoalAdjustment(date = today, reductionKcal = reductionKcal)
                )
            }
            updateCalorieWidgetValues()
        }
    }

    fun resetTodayEnergyGoalReduction() {
        viewModelScope.launch {
            settingsRepository.update { copy(todayEnergyGoalAdjustment = null) }
            updateCalorieWidgetValues()
        }
    }

    private val _lockedDaySettings =
        settingsRepository.observe().map {
            LockedDaySettings(
                defaultSurplusKcal = it.defaultLockedDaySurplusKcal,
                lockedDays = it.lockedDaySurpluses,
            )
        }
    val lockedDaySettings: StateFlow<LockedDaySettings> =
        _lockedDaySettings.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(2_000),
            initialValue = runBlocking { _lockedDaySettings.first() },
        )

    fun lockDay(date: LocalDate, surplusKcal: Double) {
        if (!surplusKcal.isFinite() || surplusKcal < 0.0) return
        viewModelScope.launch {
            settingsRepository.update {
                copy(
                    lockedDaySurpluses =
                        lockedDaySurpluses
                            .filterNot { it.date == date }
                            .plus(LockedDaySurplus(date, surplusKcal))
                            .sortedBy { it.date }
                )
            }
            updateCalorieWidgetValues()
        }
    }

    fun unlockDay(date: LocalDate) {
        viewModelScope.launch {
            settingsRepository.update {
                copy(lockedDaySurpluses = lockedDaySurpluses.filterNot { it.date == date })
            }
            updateCalorieWidgetValues()
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
                val dietEnergyDeficitKcal = settings.effectiveDietEnergyDeficitKcal(date)
                val availableGoalDisplayModes =
                    availableGoalDisplayModes(
                        currentWeek = currentWeek,
                        dietEnergyDeficitKcal = dietEnergyDeficitKcal,
                    )
                val goalDisplayMode =
                    settings.goalDisplayMode.selectedGoalDisplayMode(
                        currentWeek = currentWeek,
                        dietEnergyDeficitKcal = dietEnergyDeficitKcal,
                    )
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
                val calculationDates =
                    goalCalculationDates(selectedDate = date, today = today).takeIf {
                        availableGoalDisplayModes.any { mode -> mode != GoalDisplayMode.Normal }
                    }
                val previousDays = calculationDates?.previousDays.orEmpty()
                val plannedFutureDays = calculationDates?.plannedFutureDays.orEmpty()
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
                                        dietEnergyDeficitKcal =
                                            settings.effectiveDietEnergyDeficitKcal(previousDate)
                                                ?: 0.0,
                                        lockedSurplusKcal =
                                            settings.lockedDaySurplus(previousDate)?.surplusKcal,
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
                                        dietEnergyDeficitKcal =
                                            settings.effectiveDietEnergyDeficitKcal(futureDate)
                                                ?: 0.0,
                                        lockedSurplusKcal =
                                            settings.lockedDaySurplus(futureDate)?.surplusKcal,
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
                                calculationDate = calculationDates?.calculationDate ?: today,
                                baseEnergyGoalKcal = day.baseEnergyGoal,
                                dietEnergyDeficitKcal = dietEnergyDeficitKcal,
                                previousDays = previous,
                                plannedFutureDays = plannedFuture,
                            )
                        }
                    val selectedGoalDisplaySummary =
                        goalDisplaySummaries.firstOrNull { it.mode == goalDisplayMode }
                            ?: goalDisplaySummaries.first()
                    val netEnergy =
                        roundedNetEnergyKcal(day.consumedEnergy, day.burnedEnergy)
                    val todayAdjustment =
                        settings
                            .effectiveTodayEnergyGoalAdjustment(date, today)
                            ?.takeIf { it.reductionKcal <= day.baseEnergyGoal }
                    val baseEnergyGoal = roundedEnergyKcal(day.baseEnergyGoal)
                    val todayValues =
                        todayEnergyGoalValues(
                            baseGoalKcal = day.baseEnergyGoal,
                            netEnergyKcal = netEnergy,
                            reductionKcal = todayAdjustment?.reductionKcal,
                        )

                    DaySummaryModel(
                        energy = roundedEnergyKcal(day.consumedEnergy),
                        burnedEnergy = roundedEnergyKcal(day.burnedEnergy),
                        netEnergy = netEnergy,
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
                        todayEnergyGoal = todayValues?.goalKcal,
                        todayRemainingEnergy = todayValues?.remainingKcal,
                        todayEnergyGoalReductionKcal = todayAdjustment?.reductionKcal,
                        todayEnergyGoalEditable = date == today && baseEnergyGoal > 0,
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
                Triple(selectedDate, today, settings)
            }
            .flatMapLatest { (selectedDate, today, settings) ->
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
                            activityRepository.observeDailySummary(
                                date,
                                settings.stepsCaloriesPerStepKcal,
                            ),
                        ) { facts, goal, activity ->
                            val consumedEnergy = facts.energy.value ?: 0.0
                            val baseGoal = goal[NutritionFactsField.Energy]
                            val burnedEnergy = activity.totalEnergyKcal
                            val lockedSurplus = settings.lockedDaySurplus(date)?.surplusKcal
                            val goalWithActivity =
                                roundedEnergyKcal(baseGoal) + roundedEnergyKcal(burnedEnergy)
                            WeekDaySummaryModel(
                                date = date,
                                energy =
                                    lockedSurplus?.let {
                                        goalWithActivity + roundedEnergyKcal(it)
                                    } ?: roundedEnergyKcal(consumedEnergy),
                                goal = goalWithActivity,
                                locked = lockedSurplus != null,
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
                started = SharingStarted.Eagerly,
                initialValue = null,
            )
}

internal data class LockedDaySettings(
    val defaultSurplusKcal: Double,
    val lockedDays: List<LockedDaySurplus>,
)

internal data class TodayEnergyGoalValues(val goalKcal: Int, val remainingKcal: Int)

internal fun todayEnergyGoalValues(
    baseGoalKcal: Double,
    netEnergyKcal: Int,
    reductionKcal: Double?,
): TodayEnergyGoalValues? =
    reductionKcal
        ?.takeIf { it > 0.0 && it <= baseGoalKcal }
        ?.let {
            val goal = roundedEnergyKcal(baseGoalKcal - it)
            TodayEnergyGoalValues(goalKcal = goal, remainingKcal = goal - netEnergyKcal)
        }

internal fun LocalDate.weekDatesUntil(today: LocalDate): List<LocalDate> {
    val weekStart = startOfWeek()
    return List(7) { weekStart.plus(it, DateTimeUnit.DAY) }.takeWhile { it <= today }
}

internal data class GoalCalculationDates(
    val calculationDate: LocalDate,
    val previousDays: List<LocalDate>,
    val plannedFutureDays: List<LocalDate>,
)

internal fun goalCalculationDates(selectedDate: LocalDate, today: LocalDate): GoalCalculationDates {
    val futureDateInCurrentWeek =
        selectedDate > today && selectedDate.startOfWeek() == today.startOfWeek()
    val calculationDate =
        if (selectedDate < today || futureDateInCurrentWeek) selectedDate else today
    val sharedWeek = selectedDate.startOfWeek() == calculationDate.startOfWeek()
    val previousDays =
        if (sharedWeek) {
            val weekStart = calculationDate.startOfWeek()
            List(calculationDate.dayOfWeek.isoDayNumber - 1) {
                weekStart.plus(it, DateTimeUnit.DAY)
            }
        } else {
            emptyList()
        }
    val plannedFutureDays =
        if (sharedWeek && selectedDate >= today) {
            val firstPlannedDate =
                if (futureDateInCurrentWeek) calculationDate
                else calculationDate.plus(1, DateTimeUnit.DAY)
            List(8 - firstPlannedDate.dayOfWeek.isoDayNumber) {
                firstPlannedDate.plus(it, DateTimeUnit.DAY)
            }
        } else {
            emptyList()
        }

    return GoalCalculationDates(
        calculationDate = calculationDate,
        previousDays = previousDays,
        plannedFutureDays = plannedFutureDays,
    )
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

internal fun availableGoalDisplayModes(
    currentWeek: Boolean,
    dietEnergyDeficitKcal: Double?,
): List<GoalDisplayMode> =
    buildList {
        add(GoalDisplayMode.Normal)
        if (currentWeek) add(GoalDisplayMode.Optimized)
        if (dietEnergyDeficitKcal != null) add(GoalDisplayMode.Diet)
    }

internal fun SettingsGoalDisplayMode.selectedGoalDisplayMode(
    currentWeek: Boolean,
    dietEnergyDeficitKcal: Double?,
): GoalDisplayMode =
    when {
        this == SettingsGoalDisplayMode.Optimized && currentWeek -> GoalDisplayMode.Optimized
        this == SettingsGoalDisplayMode.Diet && dietEnergyDeficitKcal != null ->
            GoalDisplayMode.Diet
        else -> GoalDisplayMode.Normal
    }

internal fun percentageEnergyGoalKcal(energyGoalKcal: Double, baseEnergyGoalKcal: Double): Int =
    if (energyGoalKcal > 0.0) {
        roundedEnergyKcal(energyGoalKcal)
    } else {
        roundedEnergyKcal(baseEnergyGoalKcal)
    }

private fun GoalDisplayMode.summary(
    selectedDate: LocalDate,
    calculationDate: LocalDate,
    baseEnergyGoalKcal: Double,
    dietEnergyDeficitKcal: Double?,
    previousDays: List<GoalEnergyOptimizationDay>,
    plannedFutureDays: List<GoalEnergyOptimizationDay>,
): GoalDisplaySummaryModel {
    val energyGoal =
        if (this != GoalDisplayMode.Normal) {
            adjustedEnergyGoalKcal(
                selectedDate = selectedDate,
                calculationDate = calculationDate,
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

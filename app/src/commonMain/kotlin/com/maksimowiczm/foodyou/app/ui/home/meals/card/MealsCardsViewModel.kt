package com.maksimowiczm.foodyou.app.ui.home.meals.card

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.measurement.from
import com.maksimowiczm.foodyou.common.domain.measurement.rawValue
import com.maksimowiczm.foodyou.common.domain.measurement.type
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.common.extension.now
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryEntry
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFoodProduct
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFoodRecipe
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryMeal
import com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntry
import com.maksimowiczm.foodyou.fooddiary.domain.entity.ManualDiaryEntry
import com.maksimowiczm.foodyou.fooddiary.domain.entity.MealsPreferences
import com.maksimowiczm.foodyou.fooddiary.domain.repository.FoodDiaryEntryRepository
import com.maksimowiczm.foodyou.fooddiary.domain.repository.ManualDiaryEntryRepository
import com.maksimowiczm.foodyou.fooddiary.domain.usecase.ObserveDiaryMealsUseCase
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate

internal class MealsCardsViewModel(
    private val observeDiaryMealsUseCase: ObserveDiaryMealsUseCase,
    private val foodEntryRepository: FoodDiaryEntryRepository,
    private val manualEntryRepository: ManualDiaryEntryRepository,
    private val dateProvider: DateProvider,
    private val productRepository: ProductRepository,
    mealsPreferencesRepository: UserPreferencesRepository<MealsPreferences>,
) : ViewModel() {
    private val dateState = MutableStateFlow<LocalDate?>(null)

    val diaryMeals: StateFlow<List<MealModel>?> =
        dateState
            .filterNotNull()
            .flatMapLatest { date -> observeDiaryMealsUseCase.observe(date) }
            .map { list -> list.map { it.toMealModel(productRepository) } }
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(60_000),
                initialValue = null,
            )

    private val _layout = mealsPreferencesRepository.observe().map { it.layout }
    val layout =
        _layout.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(2_000),
            initialValue = runBlocking { _layout.first() },
        )

    fun setDate(date: LocalDate) {
        viewModelScope.launch { dateState.value = date }
    }

    fun onDeleteEntry(model: MealEntryModel) {
        viewModelScope.launch {
            when (model) {
                is FoodMealEntryModel -> foodEntryRepository.delete(model.id)
                is ManualMealEntryModel -> manualEntryRepository.delete(model.id)
            }
        }
    }

    fun onAddToEntry(model: MealEntryModel, amount: Double) {
        if (amount <= 0.0) return

        viewModelScope.launch {
            val now = dateProvider.now()
            when (model) {
                is FoodMealEntryModel -> {
                    val entry = foodEntryRepository.observe(model.id).firstOrNull() ?: return@launch
                    val updatedMeasurement =
                        Measurement.from(
                            type = entry.measurement.type,
                            rawValue = entry.measurement.rawValue + amount,
                        )
                    foodEntryRepository.update(
                        entry.copy(measurement = updatedMeasurement, updatedAt = now)
                    )
                }

                is ManualMealEntryModel -> {
                    val entry =
                        manualEntryRepository.observe(model.id).firstOrNull() ?: return@launch
                    manualEntryRepository.update(
                        entry.copy(
                            nutritionFacts = entry.nutritionFacts * (1.0 + amount),
                            updatedAt = now,
                        )
                    )
                }
            }
        }
    }
}

private suspend fun DiaryMeal.toMealModel(productRepository: ProductRepository): MealModel =
    MealModel(
        id = meal.id,
        name = meal.name,
        from = meal.from,
        to = meal.to,
        isAllDay = meal.from == meal.to,
        foods = entries.map { it.toMealEntryModel(productRepository) },
        energy = nutritionFacts.energy.value?.roundToInt() ?: 0,
        proteins = nutritionFacts.proteins.value ?: 0.0,
        carbohydrates = nutritionFacts.carbohydrates.value ?: 0.0,
        fats = nutritionFacts.fats.value ?: 0.0,
    )

private suspend fun DiaryEntry.toMealEntryModel(
    productRepository: ProductRepository
): MealEntryModel =
    when (this) {
        is FoodDiaryEntry ->
            FoodMealEntryModel(
                id = id,
                editableProductId =
                    (food as? DiaryFoodProduct)?.editableProductId(productRepository),
                name = food.name,
                energy = nutritionFacts.energy.value?.roundToInt(),
                proteins = nutritionFacts.proteins.value,
                carbohydrates = nutritionFacts.carbohydrates.value,
                fats = nutritionFacts.fats.value,
                measurement = measurement,
                weight = weight,
                isLiquid = food.isLiquid,
                isRecipe = food is DiaryFoodRecipe,
                totalWeight = food.totalWeight,
                servingWeight = food.servingWeight,
            )

        is ManualDiaryEntry ->
            ManualMealEntryModel(
                id = id,
                name = name,
                energy = nutritionFacts.energy.value?.roundToInt(),
                proteins = nutritionFacts.proteins.value,
                carbohydrates = nutritionFacts.carbohydrates.value,
                fats = nutritionFacts.fats.value,
            )
    }

private suspend fun DiaryFoodProduct.editableProductId(
    productRepository: ProductRepository
): FoodId.Product? {
    val sourceUrl = source.url?.takeIf { it.isNotBlank() } ?: return null
    return productRepository.getProductBySource(source.type, sourceUrl)?.id
}

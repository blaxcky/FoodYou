package com.maksimowiczm.foodyou.app.ui.home.meals.card

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.app.widget.updateCalorieWidgetValues
import com.maksimowiczm.foodyou.common.domain.database.TransactionProvider
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.event.EventBus
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.measurement.MeasurementType
import com.maksimowiczm.foodyou.common.domain.measurement.from
import com.maksimowiczm.foodyou.common.domain.measurement.rawValue
import com.maksimowiczm.foodyou.common.domain.measurement.type
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.common.extension.now
import com.maksimowiczm.foodyou.common.result.onError
import com.maksimowiczm.foodyou.common.result.onSuccess
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.Product
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository
import com.maksimowiczm.foodyou.food.domain.usecase.ObserveMeasurementSuggestionsUseCase
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryEntry
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFoodProduct
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFoodRecipe
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryMeal
import com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntry
import com.maksimowiczm.foodyou.fooddiary.domain.entity.ManualDiaryEntry
import com.maksimowiczm.foodyou.fooddiary.domain.entity.MealsPreferences
import com.maksimowiczm.foodyou.fooddiary.domain.event.FoodDiaryEntryCreatedEvent
import com.maksimowiczm.foodyou.fooddiary.domain.repository.FoodDiaryEntryRepository
import com.maksimowiczm.foodyou.fooddiary.domain.repository.MealRepository
import com.maksimowiczm.foodyou.fooddiary.domain.repository.ManualDiaryEntryRepository
import com.maksimowiczm.foodyou.fooddiary.domain.usecase.CreateFoodDiaryEntryUseCase
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate

internal class MealsCardsViewModel(
    private val observeDiaryMealsUseCase: ObserveDiaryMealsUseCase,
    private val foodEntryRepository: FoodDiaryEntryRepository,
    private val manualEntryRepository: ManualDiaryEntryRepository,
    private val mealRepository: MealRepository,
    private val transactionProvider: TransactionProvider,
    private val dateProvider: DateProvider,
    private val productRepository: ProductRepository,
    private val observeMeasurementSuggestionsUseCase: ObserveMeasurementSuggestionsUseCase,
    private val createFoodDiaryEntryUseCase: CreateFoodDiaryEntryUseCase,
    private val eventBus: EventBus,
    mealsPreferencesRepository: UserPreferencesRepository<MealsPreferences>,
) : ViewModel() {
    private val dateState = MutableStateFlow<LocalDate?>(null)
    private val _selectedEntries = MutableStateFlow<Set<MealEntrySelectionKey>>(emptySet())
    private val selectedQuickCaptureProductId = MutableStateFlow<FoodId.Product?>(null)
    val selectedEntries: StateFlow<Set<MealEntrySelectionKey>> = _selectedEntries

    val diaryMeals: StateFlow<List<MealModel>?> =
        dateState
            .filterNotNull()
            .flatMapLatest { date -> observeDiaryMealsUseCase.observe(date) }
            .map { list -> list.map { it.toMealModel(productRepository) } }
            .map { meals ->
                val visibleKeys = meals.flatMap { meal -> meal.foods }.map { it.selectionKey }.toSet()
                _selectedEntries.value = _selectedEntries.value.intersect(visibleKeys)
                meals
            }
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

    val quickCaptureProducts: StateFlow<List<QuickCaptureProductModel>> =
        productRepository
            .observeQuickCaptureProducts()
            .map { list -> list.map { it.toQuickCaptureProductModel() } }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(2_000),
                initialValue = emptyList(),
            )

    val selectedQuickCaptureMeasurement: StateFlow<QuickCaptureMeasurementModel?> =
        selectedQuickCaptureProductId
            .flatMapLatest { id ->
                if (id == null) {
                    flowOf(null)
                } else {
                    observeMeasurementSuggestionsUseCase.observeLatestOrDefault(id).map {
                        QuickCaptureMeasurementModel(productId = id, measurement = it)
                    }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(2_000),
                initialValue = null,
            )

    fun setDate(date: LocalDate) {
        viewModelScope.launch {
            if (dateState.value != date) {
                clearSelection()
            }
            dateState.value = date
        }
    }

    fun enterSelection(model: MealEntryModel) {
        _selectedEntries.value = setOf(model.selectionKey)
    }

    fun toggleSelection(model: MealEntryModel) {
        val key = model.selectionKey
        _selectedEntries.value =
            if (key in _selectedEntries.value) {
                _selectedEntries.value - key
            } else {
                _selectedEntries.value + key
            }
    }

    fun clearSelection() {
        _selectedEntries.value = emptySet()
    }

    fun deleteSelectedEntries() {
        val selected = _selectedEntries.value
        if (selected.isEmpty()) return

        viewModelScope.launch {
            transactionProvider.withTransaction {
                selected.forEach { key ->
                    when (key) {
                        is MealEntrySelectionKey.Food -> foodEntryRepository.delete(key.id)
                        is MealEntrySelectionKey.Manual -> manualEntryRepository.delete(key.id)
                    }
                }
            }
            updateCalorieWidgetValues()
            clearSelection()
        }
    }

    fun moveSelectedEntries(targetMealId: Long) {
        val selected = _selectedEntries.value
        if (selected.isEmpty()) return

        viewModelScope.launch {
            val targetMeal = mealRepository.observeMeal(targetMealId).firstOrNull() ?: return@launch
            val now = dateProvider.now()
            transactionProvider.withTransaction {
                selected.forEach { key ->
                    when (key) {
                        is MealEntrySelectionKey.Food -> {
                            val entry = foodEntryRepository.observe(key.id).firstOrNull()
                            if (entry != null) {
                                foodEntryRepository.update(
                                    entry.copy(mealId = targetMeal.id, updatedAt = now)
                                )
                            }
                        }

                        is MealEntrySelectionKey.Manual -> {
                            val entry = manualEntryRepository.observe(key.id).firstOrNull()
                            if (entry != null) {
                                manualEntryRepository.update(
                                    entry.copy(mealId = targetMeal.id, updatedAt = now)
                                )
                            }
                        }
                    }
                }
            }
            clearSelection()
        }
    }

    fun onDeleteEntry(model: MealEntryModel) {
        viewModelScope.launch {
            when (model) {
                is FoodMealEntryModel -> foodEntryRepository.delete(model.id)
                is ManualMealEntryModel -> manualEntryRepository.delete(model.id)
            }
            updateCalorieWidgetValues()
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
            updateCalorieWidgetValues()
        }
    }

    fun selectQuickCaptureProduct(product: QuickCaptureProductModel?) {
        selectedQuickCaptureProductId.value = product?.id
    }

    fun createQuickCaptureEntry(
        product: QuickCaptureProductModel,
        measurement: Measurement,
        mealId: Long,
    ) {
        val date = dateState.value ?: return
        viewModelScope.launch {
            createFoodDiaryEntryUseCase
                .createDiaryEntry(
                    measurement = measurement,
                    mealId = mealId,
                    date = date,
                    food = product.toDiaryProduct(),
                )
                .onSuccess {
                    eventBus.publish(
                        FoodDiaryEntryCreatedEvent(
                            foodId = product.id,
                            timestamp = dateProvider.nowInstant(),
                            measurement = measurement,
                        )
                    )
                    updateCalorieWidgetValues()
                }
                .onError {
                    error("Failed to create quick-capture diary entry for product ${product.id}")
                }
        }
    }
}

internal data class QuickCaptureProductModel(
    val id: FoodId.Product,
    val name: String,
    val brand: String?,
    val isLiquid: Boolean,
    val totalWeight: Double?,
    val servingWeight: Double?,
    val product: Product,
) {
    val headline: String = product.headline

    val possibleMeasurementTypes: List<MeasurementType> =
        buildList {
            if (isLiquid) {
                add(MeasurementType.Milliliter)
            } else {
                add(MeasurementType.Gram)
            }
            if (servingWeight != null) {
                add(MeasurementType.Serving)
            }
            if (totalWeight != null) {
                add(MeasurementType.Package)
            }
        }
}

internal data class QuickCaptureMeasurementModel(
    val productId: FoodId.Product,
    val measurement: Measurement,
)

private fun Product.toQuickCaptureProductModel(): QuickCaptureProductModel =
    QuickCaptureProductModel(
        id = id,
        name = name,
        brand = brand,
        isLiquid = isLiquid,
        totalWeight = totalWeight,
        servingWeight = servingWeight,
        product = this,
    )

private fun QuickCaptureProductModel.toDiaryProduct(): DiaryFoodProduct =
    DiaryFoodProduct(
        id = id,
        name = headline,
        nutritionFacts = product.nutritionFacts,
        servingWeight = servingWeight,
        totalWeight = totalWeight,
        isLiquid = isLiquid,
        source = product.source,
        note = product.note,
    )

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
                mealId = mealId,
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
                mealId = mealId,
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

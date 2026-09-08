package com.maksimowiczm.foodyou.app.ui.home.meals.card

import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.common.domain.database.TransactionProvider
import com.maksimowiczm.foodyou.common.domain.database.TransactionScope
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.event.EventBus
import com.maksimowiczm.foodyou.common.domain.event.IntegrationEvent
import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.common.domain.food.NutrientValue
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.Product
import com.maksimowiczm.foodyou.food.domain.entity.ProductPortion
import com.maksimowiczm.foodyou.food.domain.entity.Recipe
import com.maksimowiczm.foodyou.food.domain.entity.RecipeIngredient
import com.maksimowiczm.foodyou.food.domain.repository.FoodMeasurementSuggestionRepository
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository
import com.maksimowiczm.foodyou.food.domain.repository.RecipeRepository
import com.maksimowiczm.foodyou.food.domain.usecase.ObserveFoodUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.ObserveMeasurementSuggestionsUseCase
import com.maksimowiczm.foodyou.fooddiary.domain.entity.CollapsedMealCard
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFood
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFoodProduct
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFoodRecipe
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFoodRecipeIngredient
import com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntry
import com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntryId
import com.maksimowiczm.foodyou.fooddiary.domain.entity.ManualDiaryEntry
import com.maksimowiczm.foodyou.fooddiary.domain.entity.ManualDiaryEntryId
import com.maksimowiczm.foodyou.fooddiary.domain.entity.Meal
import com.maksimowiczm.foodyou.fooddiary.domain.entity.MealsCardsLayout
import com.maksimowiczm.foodyou.fooddiary.domain.entity.MealsPreferences
import com.maksimowiczm.foodyou.fooddiary.domain.event.FoodDiaryEntryCreatedEvent
import com.maksimowiczm.foodyou.fooddiary.domain.repository.FoodDiaryEntryRepository
import com.maksimowiczm.foodyou.fooddiary.domain.repository.ManualDiaryEntryRepository
import com.maksimowiczm.foodyou.fooddiary.domain.repository.MealRepository
import com.maksimowiczm.foodyou.fooddiary.domain.usecase.CreateFoodDiaryEntryUseCase
import com.maksimowiczm.foodyou.fooddiary.domain.usecase.ObserveDiaryMealsUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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

class MealsCardsViewModelTest {
    private val viewModels = mutableListOf<MealsCardsViewModel>()

    @Test
    fun toggleMealCollapsedPersistsAndReturnsToExpanded() = runViewModelTest {
        val preferencesRepository = FakeMealsPreferencesRepository()
        val viewModel = createViewModel(preferencesRepository = preferencesRepository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.collapsedMealIds.collect()
        }
        val date = LocalDate(2026, 6, 17)
        viewModel.setDate(date)
        advanceUntilIdle()

        assertTrue(viewModel.collapsedMealIds.value.isEmpty())

        viewModel.toggleMealCollapsed(mealId = 1)
        advanceUntilIdle()

        assertEquals(setOf(1L), viewModel.collapsedMealIds.value)
        assertEquals(
            setOf(CollapsedMealCard(date = date, mealId = 1)),
            preferencesRepository.value.collapsedMealCards,
        )

        viewModel.toggleMealCollapsed(mealId = 1)
        advanceUntilIdle()

        assertTrue(viewModel.collapsedMealIds.value.isEmpty())
        assertTrue(preferencesRepository.value.collapsedMealCards.isEmpty())
    }

    @Test
    fun collapsedMealsAreScopedToSelectedDate() = runViewModelTest {
        val preferencesRepository = FakeMealsPreferencesRepository()
        val viewModel = createViewModel(preferencesRepository = preferencesRepository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.collapsedMealIds.collect()
        }
        val firstDate = LocalDate(2026, 6, 17)
        val secondDate = LocalDate(2026, 6, 18)

        viewModel.setDate(firstDate)
        viewModel.toggleMealCollapsed(mealId = 1)
        advanceUntilIdle()
        viewModel.setDate(secondDate)
        advanceUntilIdle()
        assertTrue(viewModel.collapsedMealIds.value.isEmpty())

        viewModel.toggleMealCollapsed(mealId = 2)
        advanceUntilIdle()
        assertEquals(setOf(2L), viewModel.collapsedMealIds.value)

        viewModel.setDate(firstDate)
        advanceUntilIdle()
        assertEquals(setOf(1L), viewModel.collapsedMealIds.value)
    }

    @Test
    fun collapsedMealsAreRestoredFromPreferences() = runViewModelTest {
        val date = LocalDate(2026, 6, 17)
        val preferencesRepository =
            FakeMealsPreferencesRepository(
                collapsedMealCards =
                    setOf(
                        CollapsedMealCard(date = date, mealId = 1),
                        CollapsedMealCard(date = LocalDate(2026, 6, 18), mealId = 2),
                    )
            )
        val viewModel = createViewModel(preferencesRepository = preferencesRepository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.collapsedMealIds.collect()
        }

        viewModel.setDate(date)
        advanceUntilIdle()

        assertEquals(setOf(1L), viewModel.collapsedMealIds.value)
    }

    @Test
    fun selectionToggleHandlesFoodAndManualEntries() = runViewModelTest {
        val viewModel = createViewModel()
        val food = foodModel(id = 1)
        val manual = manualModel(id = 1)

        viewModel.enterSelection(food)
        assertEquals(setOf(MealEntrySelectionKey.Food(food.id)), viewModel.selectedEntries.value)

        viewModel.toggleSelection(manual)
        assertEquals(
            setOf(MealEntrySelectionKey.Food(food.id), MealEntrySelectionKey.Manual(manual.id)),
            viewModel.selectedEntries.value,
        )

        viewModel.toggleSelection(food)
        assertEquals(setOf(MealEntrySelectionKey.Manual(manual.id)), viewModel.selectedEntries.value)
    }

    @Test
    fun deleteSelectedEntriesDeletesFoodAndManualEntriesAndClearsSelection() = runViewModelTest {
        val foodEntries = FakeFoodDiaryEntryRepository()
        val manualEntries = FakeManualDiaryEntryRepository()
        val viewModel =
            createViewModel(foodEntryRepository = foodEntries, manualEntryRepository = manualEntries)
        val food = foodModel(id = 2)
        val manual = manualModel(id = 3)

        viewModel.enterSelection(food)
        viewModel.toggleSelection(manual)
        viewModel.deleteSelectedEntries()
        advanceUntilIdle()

        assertEquals(listOf(food.id), foodEntries.deleted)
        assertEquals(listOf(manual.id), manualEntries.deleted)
        assertTrue(viewModel.selectedEntries.value.isEmpty())
    }

    @Test
    fun moveSelectedEntriesUpdatesFoodAndManualEntriesAndClearsSelection() = runViewModelTest {
        val foodEntries = FakeFoodDiaryEntryRepository()
        val manualEntries = FakeManualDiaryEntryRepository()
        val now = LocalDateTime(2026, 6, 17, 12, 30)
        val viewModel =
            createViewModel(
                foodEntryRepository = foodEntries,
                manualEntryRepository = manualEntries,
                dateProvider = FixedDateProvider(now),
            )
        val food = foodEntry(id = 4, mealId = 1)
        val manual = manualEntry(id = 5, mealId = 1)
        foodEntries.entries[food.id] = food
        manualEntries.entries[manual.id] = manual

        viewModel.enterSelection(foodModel(id = food.id.value, mealId = food.mealId))
        viewModel.toggleSelection(manualModel(id = manual.id.value, mealId = manual.mealId))
        viewModel.moveSelectedEntries(targetMealId = 2)
        advanceUntilIdle()

        assertEquals(2, foodEntries.entries.getValue(food.id).mealId)
        assertEquals(now, foodEntries.entries.getValue(food.id).updatedAt)
        assertEquals(2, manualEntries.entries.getValue(manual.id).mealId)
        assertEquals(now, manualEntries.entries.getValue(manual.id).updatedAt)
        assertTrue(viewModel.selectedEntries.value.isEmpty())
    }

    @Test
    fun moveSelectedEntriesWithInvalidMealDoesNotUpdateEntries() = runViewModelTest {
        val foodEntries = FakeFoodDiaryEntryRepository()
        val manualEntries = FakeManualDiaryEntryRepository()
        val viewModel =
            createViewModel(
                foodEntryRepository = foodEntries,
                manualEntryRepository = manualEntries,
                mealRepository = FakeMealRepository(meals = listOf(Meal(1, "Breakfast", LocalTime(6, 0), LocalTime(10, 0), 0))),
            )
        val food = foodEntry(id = 6, mealId = 1)
        foodEntries.entries[food.id] = food

        viewModel.enterSelection(foodModel(id = food.id.value, mealId = food.mealId))
        viewModel.moveSelectedEntries(targetMealId = 999)
        advanceUntilIdle()

        assertEquals(food, foodEntries.entries.getValue(food.id))
        assertTrue(manualEntries.entries.isEmpty())
        assertEquals(setOf(MealEntrySelectionKey.Food(food.id)), viewModel.selectedEntries.value)
    }

    @Test
    fun createQuickCaptureEntryCreatesEntryForSelectedMealAndDateAndPublishesEvent() =
        runViewModelTest {
            val foodEntries = FakeFoodDiaryEntryRepository()
            val eventBus = RecordingEventBus()
            val dateProvider = FixedDateProvider(LocalDateTime(2026, 6, 17, 8, 0))
            val product =
                product(
                    id = 10,
                    name = "Olive oil",
                    isLiquid = true,
                    packageWeight = 500.0,
                    servingWeight = 15.0,
                )
            val viewModel =
                createViewModel(
                    foodEntryRepository = foodEntries,
                    dateProvider = dateProvider,
                    productRepository = FakeProductRepository(listOf(product)),
                    eventBus = eventBus,
                )
            val measurement = Measurement.Milliliter(12.0)

            viewModel.setDate(LocalDate(2026, 6, 18))
            viewModel.createQuickCaptureEntry(
                product = product.toQuickCaptureProductModel(),
                measurement = measurement,
                mealId = 2,
            )
            advanceUntilIdle()

            val inserted = foodEntries.inserted.single()
            assertEquals(measurement, inserted.measurement)
            assertEquals(2, inserted.mealId)
            assertEquals(LocalDate(2026, 6, 18), inserted.date)
            assertEquals(product.id, (inserted.food as DiaryFoodProduct).id)

            val event = eventBus.published.single() as FoodDiaryEntryCreatedEvent
            assertEquals(product.id, event.foodId)
            assertEquals(measurement, event.measurement)
            assertEquals(dateProvider.nowInstant(), event.timestamp)
        }

    @Test
    fun additionsConvertIntoCurrentStoredUnit() = runViewModelTest {
        val repository = FakeFoodDiaryEntryRepository()
        val viewModel = createViewModel(foodEntryRepository = repository)
        val original = foodEntry(1, 1)
        val product = (original.food as DiaryFoodProduct).copy(servingWeight = 25.0, totalWeight = 200.0)
        val cases = listOf(
            Triple(Measurement.Gram(100.0), Measurement.Serving(3.0), Measurement.Gram(175.0)),
            Triple(Measurement.Serving(2.0), Measurement.Gram(75.0), Measurement.Serving(5.0)),
            Triple(Measurement.Package(1.0), Measurement.Serving(2.0), Measurement.Package(1.25)),
            Triple(Measurement.Gram(100.0), Measurement.Package(0.5), Measurement.Gram(200.0)),
            Triple(Measurement.Serving(2.0), Measurement.Serving(1.5), Measurement.Serving(3.5)),
        )
        for ((current, addition, expected) in cases) {
            repository.entries[original.id] = original.copy(food = product, measurement = current)
            // The dialog model deliberately contains stale amounts and no reference weights.
            viewModel.onAddToEntry(foodModel(1), EntryAddition.Food(addition))
            advanceUntilIdle()
            assertEquals(expected, repository.entries.getValue(original.id).measurement)
        }
        repository.entries[original.id] = original.copy(
            food = product.copy(isLiquid = true), measurement = Measurement.Milliliter(100.0))
        viewModel.onAddToEntry(foodModel(1), EntryAddition.Food(Measurement.Serving(1.5)))
        advanceUntilIdle()
        assertEquals(Measurement.Milliliter(137.5), repository.entries.getValue(original.id).measurement)
    }

    @Test
    fun recipeServingsUseStoredRecipeWeight() = runViewModelTest {
        val repository = FakeFoodDiaryEntryRepository()
        val viewModel = createViewModel(foodEntryRepository = repository)
        val original = foodEntry(1, 1)
        val recipe = DiaryFoodRecipe(
            id = FoodId.Recipe(1), name = "Recipe", servings = 4,
            ingredients = listOf(DiaryFoodRecipeIngredient(original.food, Measurement.Gram(800.0))),
            isLiquid = false, note = null,
        )
        repository.entries[original.id] = original.copy(food = recipe, measurement = Measurement.Serving(1.0))
        viewModel.onAddToEntry(foodModel(1).copy(isRecipe = true), EntryAddition.Food(Measurement.Gram(100.0)))
        advanceUntilIdle()
        assertEquals(Measurement.Serving(1.5), repository.entries.getValue(original.id).measurement)
        viewModel.onAddToEntry(foodModel(1).copy(isRecipe = true), EntryAddition.Food(Measurement.Serving(2.0)))
        advanceUntilIdle()
        assertEquals(Measurement.Serving(3.5), repository.entries.getValue(original.id).measurement)
    }

    @Test
    fun optionsExcludeInvalidReferencesButKeepNamedPortions() {
        val portion = ProductPortion(label = "Scheibe", amount = 30.0, unit = ProductPortion.Unit.Gram)
        for (weight in listOf(null, 0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            val options = foodModel(1).copy(servingWeight = weight, totalWeight = weight, portions = listOf(portion)).additionOptions()
            assertEquals(2, options.size)
            assertEquals(Measurement.Gram(45.0), options.last().measurementForInput(1.5))
        }
    }

    @Test
    fun invalidAdditionsAndMissingConversionsDoNotUpdateEntry() = runViewModelTest {
        val repository = FakeFoodDiaryEntryRepository()
        val viewModel = createViewModel(foodEntryRepository = repository)
        val original = foodEntry(1, 1)
        repository.entries[original.id] = original
        val invalid = listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)
            .map { Measurement.Gram(it) } + listOf(Measurement.Serving(1.0), Measurement.Package(1.0), Measurement.Milliliter(1.0))
        for (addition in invalid) {
            viewModel.onAddToEntry(foodModel(1), EntryAddition.Food(addition))
            advanceUntilIdle()
            assertEquals(original, repository.entries[original.id])
        }
        for (weight in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            val entry = original.copy(food = (original.food as DiaryFoodProduct).copy(servingWeight = weight))
            assertEquals(null, entry.measurementWithAddition(Measurement.Serving(1.0)))
        }
        val overflowingEntry = original.copy(measurement = Measurement.Gram(Double.MAX_VALUE))
        assertEquals(null, overflowingEntry.measurementWithAddition(Measurement.Gram(Double.MAX_VALUE)))
    }

    @Test
    fun namedPortionsAreResolvedOnceAndDeletedProductsKeepDiaryUnits() = runViewModelTest {
        val repository = FakeFoodDiaryEntryRepository()
        val source = FoodSource(FoodSource.Type.User, url = "product/1")
        val portion = ProductPortion(label = "Scheibe", amount = 30.0, unit = ProductPortion.Unit.Gram)
        val product = product(1).copy(source = source, portions = listOf(portion))
        val products = FakeProductRepository(listOf(product))
        val original = foodEntry(1, 1)
        repository.entries[original.id] = original.copy(food = (original.food as DiaryFoodProduct).copy(source = source, servingWeight = 25.0))
        val deletedProductEntry = foodEntry(2, 1)
        repository.entries[deletedProductEntry.id] = deletedProductEntry.copy(
            measurement = Measurement.Package(1.0),
            food = (deletedProductEntry.food as DiaryFoodProduct).copy(
                source = source.copy(url = "deleted/2"), servingWeight = 25.0, totalWeight = 250.0,
            ),
        )
        val viewModel = createViewModel(foodEntryRepository = repository, productRepository = products)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.diaryMeals.collect() }
        viewModel.setDate(original.date)
        advanceUntilIdle()
        val models = viewModel.diaryMeals.value!!.flatMap { it.foods }.filterIsInstance<FoodMealEntryModel>()
        assertEquals(listOf(portion), models.first().portions)
        assertEquals(product.id, models.first().editableProductId)
        assertEquals(2, products.sourceLookups)
        assertEquals(Measurement.Package(1.0), models.last().measurement)
        assertTrue(models.last().portions.isEmpty())
        val option = models.first().additionOptions().filterIsInstance<com.maksimowiczm.foodyou.app.ui.food.component.MeasurementPickerOption.Portion>().single()
        viewModel.onAddToEntry(models.first(), EntryAddition.Food(option.measurementForInput(1.5)))
        advanceUntilIdle()
        assertEquals(Measurement.Gram(145.0), repository.entries.getValue(original.id).measurement)
    }

    @Test
    fun manualAdditionKeepsFactorBehaviorAndRejectsNonFiniteValues() = runViewModelTest {
        val repository = FakeManualDiaryEntryRepository()
        val viewModel = createViewModel(manualEntryRepository = repository)
        val original = manualEntry(1, 1).copy(nutritionFacts = NutritionFacts(energy = NutrientValue.Incomplete(100.0)))
        repository.entries[original.id] = original
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, 0.0, -1.0)) {
            viewModel.onAddToEntry(manualModel(1), EntryAddition.Manual(value))
            advanceUntilIdle()
            assertEquals(original, repository.entries[original.id])
        }
        viewModel.onAddToEntry(manualModel(1), EntryAddition.Manual(1.5))
        advanceUntilIdle()
        assertEquals(original.nutritionFacts * 2.5, repository.entries.getValue(original.id).nutritionFacts)
    }

    private fun runViewModelTest(block: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            block()
        } finally {
            viewModels.forEach { it.viewModelScope.cancel() }
            viewModels.clear()
            advanceUntilIdle()
            Dispatchers.resetMain()
        }
    }

    private fun createViewModel(
        foodEntryRepository: FakeFoodDiaryEntryRepository = FakeFoodDiaryEntryRepository(),
        manualEntryRepository: FakeManualDiaryEntryRepository = FakeManualDiaryEntryRepository(),
        mealRepository: MealRepository =
            FakeMealRepository(
                meals =
                    listOf(
                        Meal(1, "Breakfast", LocalTime(6, 0), LocalTime(10, 0), 0),
                        Meal(2, "Lunch", LocalTime(11, 0), LocalTime(14, 0), 1),
                    )
            ),
        dateProvider: DateProvider = FixedDateProvider(LocalDateTime(2026, 6, 17, 8, 0)),
        productRepository: ProductRepository = FakeProductRepository(),
        eventBus: EventBus = RecordingEventBus(),
        preferencesRepository: FakeMealsPreferencesRepository =
            FakeMealsPreferencesRepository(),
    ): MealsCardsViewModel {
        return MealsCardsViewModel(
            observeDiaryMealsUseCase =
                ObserveDiaryMealsUseCase(
                    mealRepository = mealRepository,
                    mealsPreferencesRepository = preferencesRepository,
                    foodEntryRepository = foodEntryRepository,
                    manualEntryRepository = manualEntryRepository,
                    dateProvider = dateProvider,
                ),
            foodEntryRepository = foodEntryRepository,
            manualEntryRepository = manualEntryRepository,
            mealRepository = mealRepository,
            transactionProvider = ImmediateTransactionProvider,
            dateProvider = dateProvider,
            productRepository = productRepository,
            observeMeasurementSuggestionsUseCase =
                ObserveMeasurementSuggestionsUseCase(
                    observeFoodUseCase =
                        ObserveFoodUseCase(
                            productRepository = productRepository,
                            recipeRepository = EmptyRecipeRepository,
                        ),
                    repository = FakeFoodMeasurementSuggestionRepository(),
                ),
            createFoodDiaryEntryUseCase =
                CreateFoodDiaryEntryUseCase(
                    mealRepository = mealRepository,
                    entryRepository = foodEntryRepository,
                    transactionProvider = ImmediateTransactionProvider,
                    dateProvider = dateProvider,
                    logger = NoopLogger,
                ),
            eventBus = eventBus,
            mealsPreferencesRepository = preferencesRepository,
        ).also { viewModels += it }
    }

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

    private fun foodModel(id: Long, mealId: Long = 1) =
        FoodMealEntryModel(
            id = FoodDiaryEntryId(id),
            mealId = mealId,
            editableProductId = null,
            name = "Food $id",
            energy = 100,
            proteins = 1.0,
            carbohydrates = 2.0,
            fats = 3.0,
            measurement = Measurement.Gram(100.0),
            weight = 100.0,
            isLiquid = false,
            isRecipe = false,
            servingWeight = null,
            totalWeight = null,
        )

    private fun manualModel(id: Long, mealId: Long = 1) =
        ManualMealEntryModel(
            id = ManualDiaryEntryId(id),
            mealId = mealId,
            name = "Manual $id",
            energy = 100,
            proteins = 1.0,
            carbohydrates = 2.0,
            fats = 3.0,
        )

    private fun foodEntry(id: Long, mealId: Long) =
        FoodDiaryEntry(
            id = FoodDiaryEntryId(id),
            mealId = mealId,
            date = LocalDate(2026, 6, 17),
            measurement = Measurement.Gram(100.0),
            food =
                DiaryFoodProduct(
                    id = FoodId.Product(id),
                    name = "Food $id",
                    nutritionFacts = NutritionFacts.Empty,
                    servingWeight = null,
                    totalWeight = null,
                    isLiquid = false,
                    source = FoodSource(FoodSource.Type.User),
                    note = null,
                ),
            createdAt = LocalDateTime(2026, 6, 17, 8, 0),
            updatedAt = LocalDateTime(2026, 6, 17, 8, 0),
        )

    private fun manualEntry(id: Long, mealId: Long) =
        ManualDiaryEntry(
            id = ManualDiaryEntryId(id),
            mealId = mealId,
            date = LocalDate(2026, 6, 17),
            name = "Manual $id",
            nutritionFacts = NutritionFacts.Empty,
            createdAt = LocalDateTime(2026, 6, 17, 8, 0),
            updatedAt = LocalDateTime(2026, 6, 17, 8, 0),
        )

    private fun product(
        id: Long,
        name: String = "Product $id",
        brand: String? = null,
        isLiquid: Boolean = false,
        packageWeight: Double? = null,
        servingWeight: Double? = null,
    ) =
        Product(
            id = FoodId.Product(id),
            name = name,
            brand = brand,
            barcode = null,
            note = null,
            isLiquid = isLiquid,
            packageWeight = packageWeight,
            servingWeight = servingWeight,
            source = FoodSource(FoodSource.Type.User),
            nutritionFacts = NutritionFacts.Empty,
        )

    private class FakeFoodDiaryEntryRepository : FoodDiaryEntryRepository {
        data class Inserted(
            val measurement: Measurement,
            val mealId: Long,
            val date: LocalDate,
            val food: DiaryFood,
            val createdAt: LocalDateTime,
        )

        val entries = mutableMapOf<FoodDiaryEntryId, FoodDiaryEntry>()
        val deleted = mutableListOf<FoodDiaryEntryId>()
        val inserted = mutableListOf<Inserted>()

        override fun observe(id: FoodDiaryEntryId): Flow<FoodDiaryEntry?> = flowOf(entries[id])

        override fun observeAll(mealId: Long, date: LocalDate): Flow<List<FoodDiaryEntry>> =
            flowOf(entries.values.filter { it.mealId == mealId && it.date == date })

        override suspend fun insert(
            measurement: Measurement,
            mealId: Long,
            date: LocalDate,
            food: DiaryFood,
            createdAt: LocalDateTime,
        ): FoodDiaryEntryId {
            inserted += Inserted(measurement, mealId, date, food, createdAt)
            return FoodDiaryEntryId(inserted.size.toLong())
        }

        override suspend fun update(entry: FoodDiaryEntry) {
            entries[entry.id] = entry
        }

        override suspend fun delete(id: FoodDiaryEntryId) {
            deleted += id
            entries -= id
        }
    }

    private class FakeManualDiaryEntryRepository : ManualDiaryEntryRepository {
        val entries = mutableMapOf<ManualDiaryEntryId, ManualDiaryEntry>()
        val deleted = mutableListOf<ManualDiaryEntryId>()

        override fun observe(id: ManualDiaryEntryId): Flow<ManualDiaryEntry?> = flowOf(entries[id])

        override fun observeAll(mealId: Long, date: LocalDate): Flow<List<ManualDiaryEntry>> =
            flowOf(entries.values.filter { it.mealId == mealId && it.date == date })

        override suspend fun insert(
            name: String,
            mealId: Long,
            date: LocalDate,
            nutritionFacts: NutritionFacts,
            createdAt: LocalDateTime,
        ): ManualDiaryEntryId = error("Not used")

        override suspend fun update(entry: ManualDiaryEntry) {
            entries[entry.id] = entry
        }

        override suspend fun delete(id: ManualDiaryEntryId) {
            deleted += id
            entries -= id
        }
    }

    private class FakeMealRepository(private val meals: List<Meal>) : MealRepository {
        override fun observeMeal(mealId: Long): Flow<Meal?> = flowOf(meals.firstOrNull { it.id == mealId })

        override fun observeMeals(): Flow<List<Meal>> = flowOf(meals)

        override suspend fun insertMealWithLastRank(name: String, from: LocalTime, to: LocalTime) =
            Unit

        override suspend fun deleteMeal(mealId: Long) = Unit

        override suspend fun updateMeal(id: Long, name: String, from: LocalTime, to: LocalTime) =
            Unit

        override suspend fun reorderMeals(order: List<Long>) = Unit
    }

    private class FakeMealsPreferencesRepository(
        collapsedMealCards: Set<CollapsedMealCard> = emptySet()
    ) : UserPreferencesRepository<MealsPreferences> {
        private val state =
            MutableStateFlow(
                MealsPreferences(
                    layout = MealsCardsLayout.Vertical,
                    useTimeBasedSorting = false,
                    ignoreAllDayMeals = false,
                    collapsedMealCards = collapsedMealCards,
                )
            )
        val value: MealsPreferences
            get() = state.value

        override fun observe(): Flow<MealsPreferences> = state

        override suspend fun update(transform: MealsPreferences.() -> MealsPreferences) {
            state.value = state.value.transform()
        }
    }

    private class FixedDateProvider(private val now: LocalDateTime) : DateProvider {
        override fun nowInstant(): Instant = Instant.parse("2026-06-17T10:30:00Z")

        override fun now(timeZone: TimeZone): LocalDateTime = now

        override fun observeInstant(interval: Duration): Flow<Instant> = flowOf(nowInstant())

        override fun observeDate(timeZone: TimeZone): Flow<LocalDate> = flowOf(now.date)
    }

    private object ImmediateTransactionProvider : TransactionProvider {
        override suspend fun <T> withTransaction(block: suspend TransactionScope<T>.() -> T): T =
            block(
                object : TransactionScope<T> {
                    override suspend fun rollback(result: T) = Unit
                }
            )
    }

    private class FakeProductRepository(products: List<Product> = emptyList()) :
        ProductRepository {
        private val products = products.associateBy { it.id }

        override fun observeProduct(id: FoodId.Product): Flow<Product?> = flowOf(products[id])

        override fun observeProductByBarcode(barcode: String): Flow<Product?> = flowOf(null)

        override suspend fun getProductByBarcode(barcode: String): Product? = null

        var sourceLookups = 0
        override suspend fun getProductBySource(type: FoodSource.Type, url: String): Product? {
            sourceLookups++
            return products.values.firstOrNull { it.source.type == type && it.source.url == url }
        }

        override fun observeProducts(limit: Int, offset: Int): Flow<List<Product>> =
            flowOf(products.values.toList().drop(offset).take(limit))

        override fun observeQuickCaptureProducts(): Flow<List<Product>> =
            flowOf(products.values.filter { it.isQuickCapture })

        override fun observeProductsBySource(
            type: FoodSource.Type,
            limit: Int,
            offset: Int,
        ): Flow<List<Product>> = flowOf(emptyList())

        override fun observeProductCountBySource(type: FoodSource.Type): Flow<Int> = flowOf(0)

        override suspend fun insertProduct(
            name: String,
            brand: String?,
            barcode: String?,
            note: String?,
            isLiquid: Boolean,
            packageWeight: Double?,
            servingWeight: Double?,
            source: FoodSource,
            nutritionFacts: NutritionFacts,
        ): FoodId.Product = error("Not used")

        override suspend fun insertUniqueProduct(
            name: String,
            brand: String?,
            barcode: String?,
            note: String?,
            isLiquid: Boolean,
            packageWeight: Double?,
            servingWeight: Double?,
            source: FoodSource,
            nutritionFacts: NutritionFacts,
        ): FoodId.Product? = error("Not used")

        override suspend fun updateProduct(product: Product) = Unit

        override suspend fun replaceProductPortions(
            productId: FoodId.Product,
            sourceType: FoodSource.Type,
            portions: List<ProductPortion>,
        ) = Unit

        override suspend fun deleteProduct(product: Product) = Unit

        override suspend fun deleteProductsBySource(type: FoodSource.Type): Int = 0
    }

    private object EmptyRecipeRepository : RecipeRepository {
        override fun observeRecipe(recipeId: FoodId.Recipe): Flow<Recipe?> = flowOf(null)

        override suspend fun insertRecipe(
            name: String,
            servings: Int,
            note: String?,
            isLiquid: Boolean,
            ingredients: List<RecipeIngredient>,
        ): FoodId.Recipe = error("Not used")

        override suspend fun updateRecipe(recipe: Recipe) = Unit

        override suspend fun deleteRecipe(recipe: Recipe) = Unit
    }

    private class FakeFoodMeasurementSuggestionRepository : FoodMeasurementSuggestionRepository {
        override suspend fun insert(foodId: FoodId, measurement: Measurement) = Unit

        override suspend fun findLatestByProductIdAndType(
            productId: FoodId.Product,
            type: com.maksimowiczm.foodyou.common.domain.measurement.MeasurementType,
        ): Measurement? = null

        override fun observeByFoodId(foodId: FoodId, limit: Int): Flow<List<Measurement>> =
            flowOf(emptyList())
    }

    private class RecordingEventBus : EventBus {
        override val events: Flow<IntegrationEvent> = MutableSharedFlow()
        val published = mutableListOf<IntegrationEvent>()

        override fun publish(integrationEvent: IntegrationEvent) {
            published += integrationEvent
        }
    }

    private object NoopLogger : Logger {
        override fun d(tag: String, throwable: Throwable?, message: () -> String) = Unit

        override fun w(tag: String, throwable: Throwable?, message: () -> String) = Unit

        override fun e(tag: String, throwable: Throwable?, message: () -> String) = Unit

        override fun i(tag: String, throwable: Throwable?, message: () -> String) = Unit
    }
}

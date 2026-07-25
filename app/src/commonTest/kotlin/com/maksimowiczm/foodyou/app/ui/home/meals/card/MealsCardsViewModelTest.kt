package com.maksimowiczm.foodyou.app.ui.home.meals.card

import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.common.domain.database.TransactionProvider
import com.maksimowiczm.foodyou.common.domain.database.TransactionScope
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.event.EventBus
import com.maksimowiczm.foodyou.common.domain.event.IntegrationEvent
import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.food.domain.entity.ProductPortion
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.Product
import com.maksimowiczm.foodyou.food.domain.entity.Recipe
import com.maksimowiczm.foodyou.food.domain.entity.RecipeIngredient
import com.maksimowiczm.foodyou.food.domain.repository.FoodMeasurementSuggestionRepository
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository
import com.maksimowiczm.foodyou.food.domain.repository.RecipeRepository
import com.maksimowiczm.foodyou.food.domain.usecase.ObserveFoodUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.ObserveMeasurementSuggestionsUseCase
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFood
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFoodProduct
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
import kotlinx.coroutines.flow.flowOf
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
    ): MealsCardsViewModel {
        val preferencesRepository = FakeMealsPreferencesRepository()
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

    private class FakeMealsPreferencesRepository : UserPreferencesRepository<MealsPreferences> {
        override fun observe(): Flow<MealsPreferences> =
            flowOf(
                MealsPreferences(
                    layout = MealsCardsLayout.Vertical,
                    useTimeBasedSorting = false,
                    ignoreAllDayMeals = false,
                )
            )

        override suspend fun update(transform: MealsPreferences.() -> MealsPreferences) = Unit
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

        override suspend fun getProductBySource(type: FoodSource.Type, url: String): Product? = null

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

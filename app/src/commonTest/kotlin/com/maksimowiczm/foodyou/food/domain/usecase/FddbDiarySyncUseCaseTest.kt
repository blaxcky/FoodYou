package com.maksimowiczm.foodyou.food.domain.usecase

import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.database.TransactionProvider
import com.maksimowiczm.foodyou.common.domain.database.TransactionScope
import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.food.domain.entity.FddbDiaryEntry
import com.maksimowiczm.foodyou.food.domain.entity.ProductPortion
import com.maksimowiczm.foodyou.food.domain.entity.FddbProduct
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.Product
import com.maksimowiczm.foodyou.food.domain.repository.FddbCredentialsRepository
import com.maksimowiczm.foodyou.food.domain.repository.FddbDiaryGateway
import com.maksimowiczm.foodyou.food.domain.repository.FddbDiarySyncEntryRepository
import com.maksimowiczm.foodyou.food.domain.repository.FddbProductGateway
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFood
import com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntryId
import com.maksimowiczm.foodyou.fooddiary.domain.entity.Meal
import com.maksimowiczm.foodyou.fooddiary.domain.repository.FoodDiaryEntryRepository
import com.maksimowiczm.foodyou.fooddiary.domain.repository.MealRepository
import com.maksimowiczm.foodyou.fooddiary.domain.usecase.CreateFoodDiaryEntryUseCase
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

class FddbDiarySyncUseCaseTest {
    @Test
    fun skipsAlreadySyncedEntriesAndDummyProducts() = runBlocking {
        val syncEntries = FakeFddbDiarySyncEntryRepository(existing = setOf("1"))
        val entries =
            listOf(
                diaryEntry("1", "existing_food"),
                diaryEntry("2", "dummy_food", productName = "Dummy Food"),
            )
        val useCase = useCase(syncEntries = syncEntries, diaryGateway = FakeFddbDiaryGateway(entries))

        val result = useCase.sync(LocalDate(2026, 5, 25))

        assertEquals(FddbDiarySyncResult(imported = 0, skipped = 2, failed = 0), result)
        assertEquals(setOf("1", "2"), syncEntries.ids)
    }

    @Test
    fun usesExistingLocalProductByFddbSourceUrl() = runBlocking {
        val source = "https://fddb.info/db/de/lebensmittel/local_food/index.html"
        val productRepository = FakeProductRepository(existing = listOf(product(id = 1, sourceUrl = source)))
        val foodEntries = FakeFoodDiaryEntryRepository()
        val useCase =
            useCase(
                productRepository = productRepository,
                diaryGateway = FakeFddbDiaryGateway(listOf(diaryEntry("1", "local_food"))),
                foodEntryRepository = foodEntries,
            )

        val result = useCase.sync(LocalDate(2026, 5, 25))

        assertEquals(1, result.imported)
        assertEquals(emptyList(), productRepository.insertedNames)
        assertEquals(Measurement.Gram(100.0), foodEntries.inserted.single().measurement)
    }

    @Test
    fun addsPortionsForExistingProductByFddbSourceUrl() = runBlocking {
        val source = "https://fddb.info/db/de/lebensmittel/local_food/index.html"
        val portions = listOf(ProductPortion("Stück", 12.0, ProductPortion.Unit.Gram))
        val productRepository = FakeProductRepository(existing = listOf(product(id = 1, sourceUrl = source)))
        val useCase =
            useCase(
                productRepository = productRepository,
                diaryGateway = FakeFddbDiaryGateway(listOf(diaryEntry("1", "local_food"))),
                productGateway = FakeFddbProductGateway(portions = portions),
            )

        val result = useCase.sync(LocalDate(2026, 5, 25))

        assertEquals(1, result.imported)
        assertEquals(portions, productRepository.products.single().portions)
    }

    @Test
    fun addsPortionsForExistingProductByBarcode() = runBlocking {
        val portions = listOf(ProductPortion("Stück", 12.0, ProductPortion.Unit.Gram))
        val productRepository =
            FakeProductRepository(existing = listOf(product(id = 1, barcode = "barcode-remote_food")))
        val useCase =
            useCase(
                productRepository = productRepository,
                diaryGateway = FakeFddbDiaryGateway(listOf(diaryEntry("1", "remote_food"))),
                productGateway = FakeFddbProductGateway(portions = portions),
            )

        val result = useCase.sync(LocalDate(2026, 5, 25))

        assertEquals(1, result.imported)
        assertEquals(portions, productRepository.products.single().portions)
        assertEquals(emptyList(), productRepository.insertedNames)
    }

    @Test
    fun ignoresDuplicatePortionLabelsWhenSyncingProduct() = runBlocking {
        val source = "https://fddb.info/db/de/lebensmittel/local_food/index.html"
        val productRepository = FakeProductRepository(existing = listOf(product(id = 1, sourceUrl = source)))
        val portions =
            listOf(
                ProductPortion("Stück", 12.0, ProductPortion.Unit.Gram),
                ProductPortion(" stück ", 20.0, ProductPortion.Unit.Gram),
                ProductPortion("Portion", 100.0, ProductPortion.Unit.Gram),
            )
        val useCase =
            useCase(
                productRepository = productRepository,
                diaryGateway = FakeFddbDiaryGateway(listOf(diaryEntry("1", "local_food"))),
                productGateway = FakeFddbProductGateway(portions = portions),
            )

        val result = useCase.sync(LocalDate(2026, 5, 25))

        assertEquals(1, result.imported)
        assertEquals(0, result.failed)
        assertEquals(
            listOf(
                ProductPortion("Stück", 12.0, ProductPortion.Unit.Gram),
                ProductPortion("Portion", 100.0, ProductPortion.Unit.Gram),
            ),
            productRepository.products.single().portions,
        )
    }

    @Test
    fun importsMissingProductAndCreatesDiaryEntry() = runBlocking {
        val productRepository = FakeProductRepository()
        val foodEntries = FakeFoodDiaryEntryRepository()
        val useCase =
            useCase(
                productRepository = productRepository,
                diaryGateway = FakeFddbDiaryGateway(listOf(diaryEntry("1", "remote_food"))),
                productGateway = FakeFddbProductGateway(),
                foodEntryRepository = foodEntries,
            )

        val result = useCase.sync(LocalDate(2026, 5, 25))

        assertEquals(1, result.imported)
        assertEquals(listOf("Imported remote_food"), productRepository.insertedNames)
        assertEquals("Imported remote_food (FDDB)", foodEntries.inserted.single().food.name)
    }

    @Test
    fun mapsRawFddbGramPortionToMetricMeasurement() = runBlocking {
        val portions = listOf(ProductPortion("Stück", 150.0, ProductPortion.Unit.Gram))
        val foodEntries = FakeFoodDiaryEntryRepository()
        val useCase =
            useCase(
                diaryGateway =
                    FakeFddbDiaryGateway(
                        listOf(
                            diaryEntry(
                                id = "1",
                                slug = "apple",
                                productName = "1 Stück Apfel",
                                measurement = null,
                                portionLabelAndProductName = "Stück Apfel",
                            )
                        )
                    ),
                productGateway = FakeFddbProductGateway(portions = portions),
                foodEntryRepository = foodEntries,
            )

        val result = useCase.sync(LocalDate(2026, 5, 25))

        assertEquals(1, result.imported)
        assertEquals(Measurement.Gram(150.0), foodEntries.inserted.single().measurement)
    }

    @Test
    fun mapsRawFddbPortionQuantityAndMilliliterUnit() = runBlocking {
        val portions = listOf(ProductPortion("Glas", 200.0, ProductPortion.Unit.Milliliter))
        val foodEntries = FakeFoodDiaryEntryRepository()
        val useCase =
            useCase(
                diaryGateway =
                    FakeFddbDiaryGateway(
                        listOf(
                            diaryEntry(
                                id = "1",
                                slug = "juice",
                                productName = "2 Glas Saft",
                                measurement = null,
                                portionLabelAndProductName = "Glas Saft",
                            )
                        )
                    ),
                productGateway = FakeFddbProductGateway(isLiquid = true, portions = portions),
                foodEntryRepository = foodEntries,
            )

        val result = useCase.sync(LocalDate(2026, 5, 25))

        assertEquals(1, result.imported)
        assertEquals(Measurement.Milliliter(400.0), foodEntries.inserted.single().measurement)
    }

    @Test
    fun usesLongestRawFddbPortionLabelPrefix() = runBlocking {
        val portions =
            listOf(
                ProductPortion("Dose", 50.0, ProductPortion.Unit.Gram),
                ProductPortion("Dose klein", 120.0, ProductPortion.Unit.Gram),
            )
        val foodEntries = FakeFoodDiaryEntryRepository()
        val useCase =
            useCase(
                diaryGateway =
                    FakeFddbDiaryGateway(
                        listOf(
                            diaryEntry(
                                id = "1",
                                slug = "beans",
                                productName = "1 Dose klein Bohnen",
                                measurement = null,
                                portionLabelAndProductName = "Dose klein Bohnen",
                            )
                        )
                    ),
                productGateway = FakeFddbProductGateway(portions = portions),
                foodEntryRepository = foodEntries,
            )

        val result = useCase.sync(LocalDate(2026, 5, 25))

        assertEquals(1, result.imported)
        assertEquals(Measurement.Gram(120.0), foodEntries.inserted.single().measurement)
    }

    @Test
    fun recordsFailedRawFddbPortionMappingAndContinues() = runBlocking {
        val foodEntries = FakeFoodDiaryEntryRepository()
        val useCase =
            useCase(
                diaryGateway =
                    FakeFddbDiaryGateway(
                        listOf(
                            diaryEntry(
                                id = "1",
                                slug = "apple",
                                productName = "1 Stück Apfel",
                                measurement = null,
                                portionLabelAndProductName = "Stück Apfel",
                            ),
                            diaryEntry(id = "2", slug = "banana"),
                        )
                    ),
                productGateway = FakeFddbProductGateway(portions = emptyList()),
                foodEntryRepository = foodEntries,
            )

        val result = useCase.sync(LocalDate(2026, 5, 25))

        assertEquals(1, result.imported)
        assertEquals(1, result.failed)
        assertEquals(Measurement.Gram(100.0), foodEntries.inserted.single().measurement)
        val errorMessage = result.errorMessage ?: error("Expected debug message")
        assertContains(errorMessage, "Entry ID: 1")
        assertContains(errorMessage, "FDDB portion could not be mapped")
        assertContains(errorMessage, "Portion: 1.0 Stück Apfel")
    }

    @Test
    fun recordsFailedProductImports() = runBlocking {
        val useCase =
            useCase(
                diaryGateway = FakeFddbDiaryGateway(listOf(diaryEntry("1", "broken_food"))),
                productGateway = FakeFddbProductGateway(failingSlug = "broken_food"),
            )

        val result = useCase.sync(LocalDate(2026, 5, 25))

        assertEquals(0, result.imported)
        assertEquals(0, result.skipped)
        assertEquals(1, result.failed)
        val errorMessage = result.errorMessage ?: error("Expected debug message")
        assertContains(errorMessage, "Entry ID: 1")
        assertContains(errorMessage, "Product URL: https://fddb.info/db/de/lebensmittel/broken_food/index.html")
        assertContains(errorMessage, "Product: 100 g Food")
        assertContains(errorMessage, "IllegalStateException")
        assertContains(errorMessage, "Failed")
        assertContains(errorMessage, "FakeFddbProductGateway.getProduct")
    }

    @Test
    fun recordsFailedDiaryEntryCreation() = runBlocking {
        val useCase =
            useCase(
                diaryGateway =
                    FakeFddbDiaryGateway(
                        listOf(
                            diaryEntry(
                                id = "1",
                                slug = "package_food",
                                productName = "Package Food",
                                measurement = Measurement.Package(1.0),
                            )
                        )
                    )
            )

        val result = useCase.sync(LocalDate(2026, 5, 25))

        assertEquals(0, result.imported)
        assertEquals(0, result.skipped)
        assertEquals(1, result.failed)
        val errorMessage = result.errorMessage ?: error("Expected debug message")
        assertContains(errorMessage, "Entry ID: 1")
        assertContains(errorMessage, "Product URL: https://fddb.info/db/de/lebensmittel/package_food/index.html")
        assertContains(errorMessage, "Diary entry could not be created")
        assertContains(errorMessage, "InvalidMeasurement")
    }

    @Test
    fun recordsFailedMealMapping() = runBlocking {
        val useCase =
            useCase(
                diaryGateway = FakeFddbDiaryGateway(listOf(diaryEntry("1", "remote_food"))),
                mealRepository = EmptyMealRepository,
            )

        val result = useCase.sync(LocalDate(2026, 5, 25))

        assertEquals(0, result.imported)
        assertEquals(0, result.skipped)
        assertEquals(1, result.failed)
        val errorMessage = result.errorMessage ?: error("Expected debug message")
        assertContains(errorMessage, "Entry ID: 1")
        assertContains(errorMessage, "Meal could not be mapped")
    }

    private fun useCase(
        productRepository: FakeProductRepository = FakeProductRepository(),
        diaryGateway: FddbDiaryGateway = FakeFddbDiaryGateway(emptyList()),
        productGateway: FddbProductGateway = FakeFddbProductGateway(),
        syncEntries: FakeFddbDiarySyncEntryRepository = FakeFddbDiarySyncEntryRepository(),
        foodEntryRepository: FakeFoodDiaryEntryRepository = FakeFoodDiaryEntryRepository(),
        mealRepository: MealRepository = FakeMealRepository,
    ) =
        FddbDiarySyncUseCase(
            credentialsRepository = FakeFddbCredentialsRepository,
            diaryGateway = diaryGateway,
            productGateway = productGateway,
            productRepository = productRepository,
            syncEntryRepository = syncEntries,
            mealRepository = mealRepository,
            createFoodDiaryEntryUseCase =
                CreateFoodDiaryEntryUseCase(
                    mealRepository = mealRepository,
                    entryRepository = foodEntryRepository,
                    transactionProvider = ImmediateTransactionProvider,
                    dateProvider = FixedDateProvider,
                    logger = NoopLogger,
                ),
            transactionProvider = ImmediateTransactionProvider,
            dateProvider = FixedDateProvider,
        )

    private class FakeFddbDiaryGateway(private val entries: List<FddbDiaryEntry>) : FddbDiaryGateway {
        override suspend fun login(username: String, password: String) = Unit

        override suspend fun getLastSevenDays(referenceDate: LocalDate): List<FddbDiaryEntry> = entries
    }

    private class FakeFddbProductGateway(
        private val failingSlug: String? = null,
        private val isLiquid: Boolean = false,
        private val portions: List<ProductPortion> = emptyList(),
    ) : FddbProductGateway {
        override suspend fun getProduct(url: String): FddbProduct {
            if (failingSlug != null && url.contains(failingSlug)) error("Failed")
            val slug = url.substringAfterLast("lebensmittel/").substringBefore("/")
            return FddbProduct(
                name = "Imported $slug",
                brand = "FDDB",
                barcode = "barcode-$slug",
                isLiquid = isLiquid,
                packageWeight = null,
                servingWeight = null,
                portions = portions,
                nutritionFacts = NutritionFacts.Empty,
            )
        }
    }

    private class FakeFddbDiarySyncEntryRepository(existing: Set<String> = emptySet()) :
        FddbDiarySyncEntryRepository {
        val ids = existing.toMutableSet()

        override suspend fun contains(fddbEntryId: String): Boolean = fddbEntryId in ids

        override suspend fun add(fddbEntryId: String, syncedAt: Instant) {
            ids += fddbEntryId
        }
    }

    private class FakeProductRepository(existing: List<Product> = emptyList()) : ProductRepository {
        val products = existing.toMutableList()
        val insertedNames = mutableListOf<String>()
        private var nextId = 100L

        override fun observeProduct(id: FoodId.Product): Flow<Product?> =
            flowOf(products.firstOrNull { it.id == id })

        override fun observeProductByBarcode(barcode: String): Flow<Product?> =
            flowOf(products.firstOrNull { it.barcode == barcode })

        override suspend fun getProductByBarcode(barcode: String): Product? =
            products.firstOrNull { it.barcode == barcode }

        override suspend fun getProductBySource(type: FoodSource.Type, url: String): Product? =
            products.firstOrNull { it.source.type == type && it.source.url == url }

        override fun observeProducts(limit: Int, offset: Int): Flow<List<Product>> =
            flowOf(products.drop(offset).take(limit))

        override fun observeProductsBySource(
            type: FoodSource.Type,
            limit: Int,
            offset: Int,
        ): Flow<List<Product>> =
            flowOf(products.filter { it.source.type == type }.drop(offset).take(limit))

        override fun observeProductCountBySource(type: FoodSource.Type): Flow<Int> =
            flowOf(products.count { it.source.type == type })

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
        ): FoodId.Product {
            val id = FoodId.Product(nextId++)
            products +=
                product(
                    id = id.id,
                    name = name,
                    brand = brand,
                    barcode = barcode,
                    isLiquid = isLiquid,
                    packageWeight = packageWeight,
                    servingWeight = servingWeight,
                    source = source,
                    nutritionFacts = nutritionFacts,
                )
            insertedNames += name
            return id
        }

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
        ): FoodId.Product? = insertProduct(name, brand, barcode, note, isLiquid, packageWeight, servingWeight, source, nutritionFacts)

        override suspend fun updateProduct(product: Product) {
            products.replaceAll { if (it.id == product.id) product else it }
        }

        override suspend fun replaceProductPortions(
            productId: FoodId.Product,
            sourceType: FoodSource.Type,
            portions: List<ProductPortion>,
        ) {
            products.replaceAll { product ->
                if (product.id == productId) product.copy(portions = portions) else product
            }
        }

        override suspend fun deleteProduct(product: Product) = Unit

        override suspend fun deleteProductsBySource(type: FoodSource.Type): Int {
            val count = products.count { it.source.type == type }
            products.removeAll { it.source.type == type }
            return count
        }
    }

    private class FakeFoodDiaryEntryRepository : FoodDiaryEntryRepository {
        data class Inserted(val measurement: Measurement, val food: DiaryFood)

        val inserted = mutableListOf<Inserted>()

        override fun observe(id: FoodDiaryEntryId) = emptyFlow<com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntry?>()

        override fun observeAll(mealId: Long, date: LocalDate) = emptyFlow<List<com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntry>>()

        override suspend fun insert(
            measurement: Measurement,
            mealId: Long,
            date: LocalDate,
            food: DiaryFood,
            createdAt: LocalDateTime,
        ): FoodDiaryEntryId {
            inserted += Inserted(measurement, food)
            return FoodDiaryEntryId(inserted.size.toLong())
        }

        override suspend fun update(entry: com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntry) = Unit

        override suspend fun delete(id: FoodDiaryEntryId) = Unit
    }

    private object FakeMealRepository : MealRepository {
        private val meals = listOf(Meal(1, "Morgens", LocalTime(0, 0), LocalTime(12, 0), 0))

        override fun observeMeal(mealId: Long): Flow<Meal?> = flowOf(meals.firstOrNull { it.id == mealId })

        override fun observeMeals(): Flow<List<Meal>> = flowOf(meals)

        override suspend fun insertMealWithLastRank(name: String, from: LocalTime, to: LocalTime) = Unit

        override suspend fun deleteMeal(mealId: Long) = Unit

        override suspend fun updateMeal(id: Long, name: String, from: LocalTime, to: LocalTime) = Unit

        override suspend fun reorderMeals(order: List<Long>) = Unit
    }

    private object EmptyMealRepository : MealRepository {
        override fun observeMeal(mealId: Long): Flow<Meal?> = flowOf(null)

        override fun observeMeals(): Flow<List<Meal>> = flowOf(emptyList())

        override suspend fun insertMealWithLastRank(name: String, from: LocalTime, to: LocalTime) = Unit

        override suspend fun deleteMeal(mealId: Long) = Unit

        override suspend fun updateMeal(id: Long, name: String, from: LocalTime, to: LocalTime) = Unit

        override suspend fun reorderMeals(order: List<Long>) = Unit
    }

    private object FakeFddbCredentialsRepository : FddbCredentialsRepository {
        override suspend fun store(login: String, password: String) = Unit

        override suspend fun clear() = Unit

        override fun hasCredentials(): Flow<Boolean> = flowOf(true)

        override suspend fun loadCredentials(): Pair<String, String> = "user" to "pass"
    }

    private object ImmediateTransactionProvider : TransactionProvider {
        override suspend fun <T> withTransaction(block: suspend TransactionScope<T>.() -> T): T =
            block(
                object : TransactionScope<T> {
                    override suspend fun rollback(result: T) = Unit
                }
            )
    }

    private object FixedDateProvider : DateProvider {
        override fun nowInstant(): Instant = Instant.parse("2026-05-25T00:00:00Z")

        override fun observeInstant(interval: Duration): Flow<Instant> = emptyFlow()

        override fun observeDate(timeZone: kotlinx.datetime.TimeZone): Flow<LocalDate> = emptyFlow()
    }

    private object NoopLogger : Logger {
        override fun d(tag: String, throwable: Throwable?, message: () -> String) = Unit

        override fun w(tag: String, throwable: Throwable?, message: () -> String) = Unit

        override fun e(tag: String, throwable: Throwable?, message: () -> String) = Unit

        override fun i(tag: String, throwable: Throwable?, message: () -> String) = Unit
    }

    private companion object {
        fun diaryEntry(
            id: String,
            slug: String,
            productName: String = "100 g Food",
            measurement: Measurement? = Measurement.Gram(100.0),
            portionLabelAndProductName: String? = null,
        ) =
            FddbDiaryEntry(
                entryId = id,
                date = LocalDate(2026, 5, 25),
                mealName = "Morgens",
                productName = productName,
                productUrl = "https://fddb.info/db/de/lebensmittel/$slug/index.html",
                measurement = measurement,
                portionMeasurement =
                    portionLabelAndProductName?.let {
                        com.maksimowiczm.foodyou.food.domain.entity.FddbDiaryPortionMeasurement(
                            quantity = productName.substringBefore(' ').toDouble(),
                            labelAndProductName = it,
                        )
                    },
            )

        fun product(
            id: Long,
            name: String = "Local Food",
            brand: String? = "Brand",
            barcode: String? = null,
            isLiquid: Boolean = false,
            packageWeight: Double? = null,
            servingWeight: Double? = null,
            sourceUrl: String? = null,
            source: FoodSource = FoodSource(FoodSource.Type.FDDB, sourceUrl),
            nutritionFacts: NutritionFacts = NutritionFacts.Empty,
        ) =
            Product(
                id = FoodId.Product(id),
                name = name,
                brand = brand,
                barcode = barcode,
                note = null,
                isLiquid = isLiquid,
                packageWeight = packageWeight,
                servingWeight = servingWeight,
                source = source,
                nutritionFacts = nutritionFacts,
            )
    }
}

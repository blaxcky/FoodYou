package com.maksimowiczm.foodyou.food.domain.usecase

import com.maksimowiczm.foodyou.common.domain.database.TransactionProvider
import com.maksimowiczm.foodyou.common.domain.database.TransactionScope
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.common.domain.food.NutrientValue
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.food.domain.entity.FddbImportQueueItem
import com.maksimowiczm.foodyou.food.domain.entity.FddbPortion
import com.maksimowiczm.foodyou.food.domain.entity.FoodHistory
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.FddbProduct
import com.maksimowiczm.foodyou.food.domain.entity.Product
import com.maksimowiczm.foodyou.food.domain.repository.FddbAccessBlockedException
import com.maksimowiczm.foodyou.food.domain.repository.FddbImportQueueRepository
import com.maksimowiczm.foodyou.food.domain.repository.FddbProductGateway
import com.maksimowiczm.foodyou.food.domain.repository.FoodHistoryRepository
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate

class ImportFddbProductsUseCaseTest {
    @Test
    fun addsDeduplicatedLinksToQueue() = runBlocking {
        val queue = FakeFddbImportQueueRepository()
        val useCase = AddFddbLinksToQueueUseCase(queue, FixedDateProvider)
        val text = "${Url(1)} ${Url(1)}, ${Url(2)}"

        val count = useCase(text)

        assertEquals(2, count)
        assertEquals(listOf(Url(1), Url(2)), queue.items.map { it.url })
    }

    @Test
    fun doesNotAddExistingQueueUrlsAgain() = runBlocking {
        val queue = FakeFddbImportQueueRepository()
        val useCase = AddFddbLinksToQueueUseCase(queue, FixedDateProvider)

        useCase("${Url(1)} ${Url(2)}")
        useCase("${Url(1)} ${Url(2)}")

        assertEquals(listOf(Url(1), Url(2)), queue.items.map { it.url })
    }

    @Test
    fun importsMultipleLinksSequentially() = runBlocking {
        val gateway = FakeFddbProductGateway()
        val repository = FakeProductRepository()
        val queue = FakeFddbImportQueueRepository()
        val useCase = useCase(gateway, repository, queue)
        val links = "${Url(1)}\ntext ${Url(2)}"

        val progress = useCase.import(links).toList().last()

        assertEquals(listOf(Url(1), Url(2)), gateway.requests)
        assertEquals(2, progress.imported)
        assertEquals(2, repository.products.size)
        assertEquals(emptyList(), queue.items)
    }

    @Test
    fun skipsExistingBarcode() = runBlocking {
        val gateway = FakeFddbProductGateway()
        val repository =
            FakeProductRepository(existingProduct = product(id = 1, barcode = "1234567890123"))
        val queue = FakeFddbImportQueueRepository()
        val useCase = useCase(gateway, repository, queue)

        val result = useCase.import(Url(1)).toList().last().results.single()

        val skipped = assertIs<FddbImportResult.Skipped>(result)
        assertEquals(FddbSkipReason.BarcodeExists, skipped.reason)
        assertEquals(1, repository.products.size)
        assertEquals(emptyList(), queue.items)
    }

    @Test
    fun fillsMissingWeightsForExistingBarcode() = runBlocking {
        val gateway = FakeFddbProductGateway(packageWeight = 200.0, servingWeight = 12.0)
        val existing = product(id = 1, barcode = "1234567890123")
        val repository = FakeProductRepository(existingProduct = existing)
        val queue = FakeFddbImportQueueRepository()
        val useCase = useCase(gateway, repository, queue)

        val result = useCase.import(Url(1)).toList().last().results.single()

        val skipped = assertIs<FddbImportResult.Skipped>(result)
        assertEquals(FddbSkipReason.UpdatedWeights, skipped.reason)
        assertEquals(200.0, repository.products.single().packageWeight)
        assertEquals(12.0, repository.products.single().servingWeight)
        assertEquals(emptyList(), queue.items)
    }

    @Test
    fun storesPortionsForImportedProduct() = runBlocking {
        val portions = listOf(FddbPortion("Stück", 12.0, FddbPortion.Unit.Gram))
        val gateway = FakeFddbProductGateway(portions = portions)
        val repository = FakeProductRepository()
        val useCase = useCase(gateway, repository, FakeFddbImportQueueRepository())

        val result = useCase.import(Url(1)).toList().last().results.single()

        val imported = assertIs<FddbImportResult.Imported>(result)
        assertEquals(Url(1), imported.link)
        assertEquals(portions, repository.products.single().portions)
    }

    @Test
    fun addsPortionsForExistingBarcode() = runBlocking {
        val portions = listOf(FddbPortion("Stück", 12.0, FddbPortion.Unit.Gram))
        val gateway = FakeFddbProductGateway(portions = portions)
        val existing = product(id = 1, barcode = "1234567890123")
        val repository = FakeProductRepository(existingProduct = existing)
        val useCase = useCase(gateway, repository, FakeFddbImportQueueRepository())

        val result = useCase.import(Url(1)).toList().last().results.single()

        val skipped = assertIs<FddbImportResult.Skipped>(result)
        assertEquals(FddbSkipReason.UpdatedWeights, skipped.reason)
        assertEquals(portions, repository.products.single().portions)
    }

    @Test
    fun doesNotOverwriteExistingWeightsForExistingBarcode() = runBlocking {
        val gateway = FakeFddbProductGateway(packageWeight = 200.0, servingWeight = 12.0)
        val existing =
            product(
                id = 1,
                barcode = "1234567890123",
                packageWeight = 150.0,
                servingWeight = 10.0,
        )
        val repository = FakeProductRepository(existingProduct = existing)
        val useCase = useCase(gateway, repository, FakeFddbImportQueueRepository())

        val result = useCase.import(Url(1)).toList().last().results.single()

        val skipped = assertIs<FddbImportResult.Skipped>(result)
        assertEquals(FddbSkipReason.BarcodeExists, skipped.reason)
        assertEquals(150.0, repository.products.single().packageWeight)
        assertEquals(10.0, repository.products.single().servingWeight)
    }

    @Test
    fun failedLinksDoNotBlockFollowingLinks() = runBlocking {
        val gateway = FakeFddbProductGateway(failingUrl = Url(1))
        val repository = FakeProductRepository()
        val queue = FakeFddbImportQueueRepository()
        val useCase = useCase(gateway, repository, queue)

        val progress = useCase.import("${Url(1)} ${Url(2)}").toList().last()

        assertEquals(1, progress.failed)
        assertEquals(1, progress.imported)
        assertEquals(1, repository.products.size)
        assertEquals(listOf(Url(1)), queue.items.map { it.url })
        assertEquals("Failed", queue.items.single().lastError)
    }

    @Test
    fun repeatedRateLimitStopsImportAndKeepsRemainingQueue() = runBlocking {
        val gateway = FakeFddbProductGateway(blockedUrl = Url(1))
        val repository = FakeProductRepository()
        val queue = FakeFddbImportQueueRepository()
        queue.add(Url(1), FixedDateProvider.nowInstant())
        queue.add(Url(2), FixedDateProvider.nowInstant())
        val useCase = useCase(gateway, repository, queue)

        val progress = useCase.importQueue().toList().last()

        assertEquals(listOf(Url(1), Url(1)), gateway.requests)
        assertEquals(1, progress.failed)
        assertEquals(true, progress.isFinished)
        assertEquals(listOf(Url(1), Url(2)), queue.items.map { it.url })
        assertEquals("Blocked", queue.items.first().lastError)
    }

    private fun useCase(
        gateway: FddbProductGateway,
        repository: ProductRepository,
        queue: FddbImportQueueRepository,
    ) =
        ImportFddbProductsUseCase(
            fddbProductGateway = gateway,
            queueRepository = queue,
            productRepository = repository,
            historyRepository = FakeFoodHistoryRepository(),
            transactionProvider = ImmediateTransactionProvider,
            dateProvider = FixedDateProvider,
            requestDelayMillis = 0,
            defaultBlockedCooldownMillis = 0,
        )

    private class FakeFddbProductGateway(
        private val failingUrl: String? = null,
        private val blockedUrl: String? = null,
        private val packageWeight: Double? = null,
        private val servingWeight: Double? = null,
        private val portions: List<FddbPortion> = emptyList(),
    ) : FddbProductGateway {
        val requests = mutableListOf<String>()

        override suspend fun getProduct(url: String): FddbProduct {
            requests += url
            if (url == failingUrl) {
                error("Failed")
            }
            if (url == blockedUrl) {
                throw FddbAccessBlockedException("Blocked", retryAfterMillis = 0)
            }

            return FddbProduct(
                name = "Product $url",
                brand = "Brand",
                barcode = if (url.contains("product_1")) "1234567890123" else "1234567890124",
                isLiquid = false,
                packageWeight = packageWeight,
                servingWeight = servingWeight,
                portions = portions,
                nutritionFacts =
                    NutritionFacts(
                        energy = NutrientValue.Complete(100.0),
                        proteins = NutrientValue.Complete(10.0),
                        carbohydrates = NutrientValue.Complete(20.0),
                        fats = NutrientValue.Complete(5.0),
                    ),
            )
        }
    }

    private class FakeFddbImportQueueRepository : FddbImportQueueRepository {
        private val flow = MutableStateFlow<List<FddbImportQueueItem>>(emptyList())
        private var nextId = 1L
        val items: List<FddbImportQueueItem>
            get() = flow.value

        override fun observeQueue(): Flow<List<FddbImportQueueItem>> = flow

        override suspend fun getQueue(): List<FddbImportQueueItem> = flow.value

        override suspend fun add(url: String, createdAt: Instant) {
            if (flow.value.any { it.url == url }) {
                return
            }

            flow.value =
                flow.value +
                    FddbImportQueueItem(
                        id = nextId++,
                        url = url,
                        createdAt = createdAt,
                        lastAttemptedAt = null,
                        lastError = null,
                    )
        }

        override suspend fun delete(id: Long) {
            flow.value = flow.value.filterNot { it.id == id }
        }

        override suspend fun markAttempt(id: Long, attemptedAt: Instant, error: String?) {
            flow.value =
                flow.value.map {
                    if (it.id == id) {
                        it.copy(lastAttemptedAt = attemptedAt, lastError = error)
                    } else {
                        it
                    }
                }
        }
    }

    private class FakeProductRepository(existingProduct: Product? = null) : ProductRepository {
        val products = mutableListOf<Product>()
        private var nextId = 1L

        init {
            if (existingProduct != null) {
                products += existingProduct
                nextId = existingProduct.id.id + 1
            }
        }

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
                    packageWeight = packageWeight,
                    servingWeight = servingWeight,
                    source = source,
                    nutritionFacts = nutritionFacts,
                )
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
        ): FoodId.Product? {
            if (
                products.any {
                    it.name == name && it.brand == brand && it.barcode == barcode && it.source.type == source.type
                }
            ) {
                return null
            }

            return insertProduct(
                name = name,
                brand = brand,
                barcode = barcode,
                note = note,
                isLiquid = isLiquid,
                packageWeight = packageWeight,
                servingWeight = servingWeight,
                source = source,
                nutritionFacts = nutritionFacts,
            )
        }

        override suspend fun updateProduct(product: Product) {
            products.replaceAll { if (it.id == product.id) product else it }
        }

        override suspend fun replaceProductPortions(
            productId: FoodId.Product,
            sourceType: FoodSource.Type,
            portions: List<FddbPortion>,
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

    private class FakeFoodHistoryRepository : FoodHistoryRepository {
        override suspend fun insert(foodId: FoodId, history: FoodHistory) = Unit

        override fun observeFoodHistory(foodId: FoodId): Flow<List<FoodHistory>> = emptyFlow()
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
        override fun nowInstant(): Instant = Instant.parse("2026-05-24T00:00:00Z")

        override fun observeInstant(interval: Duration): Flow<Instant> = emptyFlow()

        override fun observeDate(timeZone: kotlinx.datetime.TimeZone): Flow<LocalDate> = emptyFlow()
    }

    private companion object {
        fun Url(index: Int) =
            "https://fddb.info/db/de/lebensmittel/product_$index/index.html"

        fun product(
            id: Long,
            name: String = "Product",
            brand: String? = "Brand",
            barcode: String? = null,
            packageWeight: Double? = null,
            servingWeight: Double? = null,
            source: FoodSource = FoodSource(FoodSource.Type.FDDB),
            nutritionFacts: NutritionFacts = NutritionFacts.Empty,
            portions: List<FddbPortion> = emptyList(),
        ) =
            Product(
                id = FoodId.Product(id),
                name = name,
                brand = brand,
                barcode = barcode,
                note = null,
                isLiquid = false,
                packageWeight = packageWeight,
                servingWeight = servingWeight,
                portions = portions,
                source = source,
                nutritionFacts = nutritionFacts,
            )
    }
}

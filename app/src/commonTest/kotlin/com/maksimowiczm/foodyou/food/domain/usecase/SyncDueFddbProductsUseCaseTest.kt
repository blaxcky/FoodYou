package com.maksimowiczm.foodyou.food.domain.usecase

import com.maksimowiczm.foodyou.common.domain.database.TransactionProvider
import com.maksimowiczm.foodyou.common.domain.database.TransactionScope
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.common.domain.food.NutrientValue
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.food.domain.entity.FddbPortion
import com.maksimowiczm.foodyou.food.domain.entity.FddbProduct
import com.maksimowiczm.foodyou.food.domain.entity.FddbProductSyncQueueItem
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.Product
import com.maksimowiczm.foodyou.food.domain.repository.FddbAccessBlockedException
import com.maksimowiczm.foodyou.food.domain.repository.FddbProductGateway
import com.maksimowiczm.foodyou.food.domain.repository.FddbProductSyncStatusRepository
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

class SyncDueFddbProductsUseCaseTest {
    @Test
    fun syncsAtMostTwoDueProducts() = runBlocking {
        val statusRepository = FakeFddbProductSyncStatusRepository(dueProducts = dueItems(1, 2, 3))
        val gateway = FakeFddbProductGateway()
        val useCase = useCase(statusRepository = statusRepository, gateway = gateway)

        val result = useCase.sync(limit = 2)

        assertEquals(SyncDueFddbProductsResult(synced = 2, failed = 0, blocked = false), result)
        assertEquals(listOf(Url(1), Url(2)), gateway.requestedUrls)
        assertEquals(listOf(FoodId.Product(1), FoodId.Product(2)), statusRepository.successes)
    }

    @Test
    fun continuesWithSecondProductAfterRegularFailure() = runBlocking {
        val statusRepository = FakeFddbProductSyncStatusRepository(dueProducts = dueItems(1, 2))
        val gateway = FakeFddbProductGateway(failingUrls = setOf(Url(1)))
        val useCase = useCase(statusRepository = statusRepository, gateway = gateway)

        val result = useCase.sync(limit = 2)

        assertEquals(SyncDueFddbProductsResult(synced = 1, failed = 1, blocked = false), result)
        assertEquals(listOf(Url(1), Url(2)), gateway.requestedUrls)
        assertEquals(listOf(FoodId.Product(2)), statusRepository.successes)
        assertEquals(mapOf(FoodId.Product(1) to "Network or parse failed"), statusRepository.failures)
    }

    @Test
    fun stopsCurrentRunAfterBlockedAccess() = runBlocking {
        val statusRepository = FakeFddbProductSyncStatusRepository(dueProducts = dueItems(1, 2))
        val gateway = FakeFddbProductGateway(blockedUrls = setOf(Url(1)))
        val useCase = useCase(statusRepository = statusRepository, gateway = gateway)

        val result = useCase.sync(limit = 2)

        assertEquals(SyncDueFddbProductsResult(synced = 0, failed = 1, blocked = true), result)
        assertEquals(listOf(Url(1)), gateway.requestedUrls)
        assertEquals(mapOf(FoodId.Product(1) to "FDDB access blocked"), statusRepository.failures)
    }

    private fun useCase(
        statusRepository: FakeFddbProductSyncStatusRepository,
        gateway: FddbProductGateway,
    ) =
        SyncDueFddbProductsUseCase(
            statusRepository = statusRepository,
            resyncFddbProductUseCase =
                ResyncFddbProductUseCase(
                    productRepository = FakeProductRepository(products(1, 2, 3)),
                    fddbProductGateway = gateway,
                    transactionProvider = ImmediateTransactionProvider,
                    logger = NoopLogger,
                ),
            dateProvider = FixedDateProvider,
        )

    private class FakeFddbProductSyncStatusRepository(
        private val dueProducts: List<FddbProductSyncQueueItem>
    ) : FddbProductSyncStatusRepository {
        val successes = mutableListOf<FoodId.Product>()
        val failures = mutableMapOf<FoodId.Product, String>()

        override fun observeQueue(): Flow<List<FddbProductSyncQueueItem>> = flowOf(dueProducts)

        override suspend fun getDueProducts(limit: Int): List<FddbProductSyncQueueItem> =
            dueProducts.take(limit)

        override suspend fun markSuccess(productId: FoodId.Product, syncedAt: Instant) {
            successes += productId
        }

        override suspend fun markFailure(
            productId: FoodId.Product,
            attemptedAt: Instant,
            error: String,
        ) {
            failures[productId] = error
        }
    }

    private class FakeFddbProductGateway(
        private val failingUrls: Set<String> = emptySet(),
        private val blockedUrls: Set<String> = emptySet(),
    ) : FddbProductGateway {
        val requestedUrls = mutableListOf<String>()

        override suspend fun getProduct(url: String): FddbProduct {
            requestedUrls += url
            if (url in blockedUrls) throw FddbAccessBlockedException("Blocked")
            if (url in failingUrls) error("Failed")
            return FddbProduct(
                name = "Remote",
                brand = null,
                barcode = null,
                isLiquid = false,
                packageWeight = null,
                servingWeight = null,
                portions = emptyList(),
                nutritionFacts = nutrition(),
            )
        }
    }

    private class FakeProductRepository(initialProducts: List<Product>) : ProductRepository {
        private val products = MutableStateFlow(initialProducts)

        override fun observeProduct(id: FoodId.Product): Flow<Product?> =
            products.map { products -> products.firstOrNull { it.id == id } }

        override fun observeProductByBarcode(barcode: String): Flow<Product?> = flowOf(null)

        override suspend fun getProductByBarcode(barcode: String): Product? = null

        override suspend fun getProductBySource(type: FoodSource.Type, url: String): Product? = null

        override fun observeProducts(limit: Int, offset: Int): Flow<List<Product>> =
            products.map { it.drop(offset).take(limit) }

        override fun observeProductsBySource(
            type: FoodSource.Type,
            limit: Int,
            offset: Int,
        ): Flow<List<Product>> =
            products.map { products ->
                products.filter { it.source.type == type }.drop(offset).take(limit)
            }

        override fun observeProductCountBySource(type: FoodSource.Type): Flow<Int> =
            products.map { products -> products.count { it.source.type == type } }

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

        override suspend fun updateProduct(product: Product) {
            products.value = products.value.map { if (it.id == product.id) product else it }
        }

        override suspend fun replaceProductPortions(
            productId: FoodId.Product,
            sourceType: FoodSource.Type,
            portions: List<FddbPortion>,
        ) = Unit

        override suspend fun updateProductPortions(
            productId: FoodId.Product,
            portions: List<com.maksimowiczm.foodyou.food.domain.entity.ProductPortion>,
        ) = Unit

        override suspend fun deleteProduct(product: Product) = Unit

        override suspend fun deleteProductsBySource(type: FoodSource.Type): Int = 0
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
        private val now = Instant.fromEpochSeconds(1_000)

        override fun nowInstant(): Instant = now

        override fun observeInstant(interval: Duration): Flow<Instant> = flowOf(now)

        override fun observeDate(timeZone: TimeZone): Flow<LocalDate> =
            flowOf(LocalDate(2026, 6, 22))
    }

    private object NoopLogger : Logger {
        override fun d(tag: String, throwable: Throwable?, message: () -> String) = Unit

        override fun w(tag: String, throwable: Throwable?, message: () -> String) = Unit

        override fun e(tag: String, throwable: Throwable?, message: () -> String) = Unit

        override fun i(tag: String, throwable: Throwable?, message: () -> String) = Unit
    }

    private companion object {
        fun Url(id: Long) = "https://fddb.info/db/de/lebensmittel/product_$id/index.html"

        fun dueItems(vararg ids: Long): List<FddbProductSyncQueueItem> =
            ids.map { id ->
                FddbProductSyncQueueItem(
                    productId = FoodId.Product(id),
                    name = "Product $id",
                    brand = null,
                    lastSyncedAt = null,
                    lastAttemptAt = null,
                    lastError = null,
                )
            }

        fun products(vararg ids: Long): List<Product> =
            ids.map { id ->
                Product(
                    id = FoodId.Product(id),
                    name = "Product $id",
                    brand = null,
                    barcode = null,
                    note = null,
                    isLiquid = false,
                    packageWeight = null,
                    servingWeight = null,
                    portions = emptyList(),
                    source = FoodSource(FoodSource.Type.FDDB, Url(id)),
                    nutritionFacts = nutrition(),
                )
            }

        fun nutrition() =
            NutritionFacts(
                energy = NutrientValue.Complete(100.0),
                proteins = NutrientValue.Complete(1.0),
                carbohydrates = NutrientValue.Complete(2.0),
                fats = NutrientValue.Complete(3.0),
            )
    }
}

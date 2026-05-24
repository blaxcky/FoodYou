package com.maksimowiczm.foodyou.food.domain.usecase

import com.maksimowiczm.foodyou.common.domain.database.TransactionProvider
import com.maksimowiczm.foodyou.common.domain.database.TransactionScope
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.common.domain.food.NutrientValue
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.food.domain.entity.FoodHistory
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.FddbProduct
import com.maksimowiczm.foodyou.food.domain.entity.Product
import com.maksimowiczm.foodyou.food.domain.repository.FddbProductGateway
import com.maksimowiczm.foodyou.food.domain.repository.FoodHistoryRepository
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate

class ImportFddbProductsUseCaseTest {
    @Test
    fun importsMultipleLinksSequentially() = runBlocking {
        val gateway = FakeFddbProductGateway()
        val repository = FakeProductRepository()
        val useCase = useCase(gateway, repository)
        val links = "${Url(1)}\ntext ${Url(2)}"

        val progress = useCase.import(links).toList().last()

        assertEquals(listOf(Url(1), Url(2)), gateway.requests)
        assertEquals(2, progress.imported)
        assertEquals(2, repository.products.size)
    }

    @Test
    fun skipsExistingBarcode() = runBlocking {
        val gateway = FakeFddbProductGateway()
        val repository = FakeProductRepository(existingBarcode = "1234567890123")
        val useCase = useCase(gateway, repository)

        val result = useCase.import(Url(1)).toList().last().results.single()

        val skipped = assertIs<FddbImportResult.Skipped>(result)
        assertEquals(FddbSkipReason.BarcodeExists, skipped.reason)
        assertEquals(1, repository.products.size)
    }

    @Test
    fun failedLinksDoNotBlockFollowingLinks() = runBlocking {
        val gateway = FakeFddbProductGateway(failingUrl = Url(1))
        val repository = FakeProductRepository()
        val useCase = useCase(gateway, repository)

        val progress = useCase.import("${Url(1)} ${Url(2)}").toList().last()

        assertEquals(1, progress.failed)
        assertEquals(1, progress.imported)
        assertEquals(1, repository.products.size)
    }

    private fun useCase(
        gateway: FddbProductGateway,
        repository: ProductRepository,
    ) =
        ImportFddbProductsUseCase(
            fddbProductGateway = gateway,
            productRepository = repository,
            historyRepository = FakeFoodHistoryRepository(),
            transactionProvider = ImmediateTransactionProvider,
            dateProvider = FixedDateProvider,
            requestDelayMillis = 0,
        )

    private class FakeFddbProductGateway(private val failingUrl: String? = null) : FddbProductGateway {
        val requests = mutableListOf<String>()

        override suspend fun getProduct(url: String): FddbProduct {
            requests += url
            if (url == failingUrl) {
                error("Failed")
            }

            return FddbProduct(
                name = "Product $url",
                brand = "Brand",
                barcode = if (url.contains("product_1")) "1234567890123" else "1234567890124",
                isLiquid = false,
                packageWeight = null,
                servingWeight = null,
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

    private class FakeProductRepository(existingBarcode: String? = null) : ProductRepository {
        val products = mutableListOf<Product>()
        private var nextId = 1L

        init {
            if (existingBarcode != null) {
                products += product(id = nextId++, barcode = existingBarcode)
            }
        }

        override fun observeProduct(id: FoodId.Product): Flow<Product?> =
            flowOf(products.firstOrNull { it.id == id })

        override fun observeProductByBarcode(barcode: String): Flow<Product?> =
            flowOf(products.firstOrNull { it.barcode == barcode })

        override suspend fun getProductByBarcode(barcode: String): Product? =
            products.firstOrNull { it.barcode == barcode }

        override fun observeProducts(limit: Int, offset: Int): Flow<List<Product>> =
            flowOf(products.drop(offset).take(limit))

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

        override suspend fun updateProduct(product: Product) = Unit

        override suspend fun deleteProduct(product: Product) = Unit
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
            source: FoodSource = FoodSource(FoodSource.Type.FDDB),
            nutritionFacts: NutritionFacts = NutritionFacts.Empty,
        ) =
            Product(
                id = FoodId.Product(id),
                name = name,
                brand = brand,
                barcode = barcode,
                note = null,
                isLiquid = false,
                packageWeight = null,
                servingWeight = null,
                source = source,
                nutritionFacts = nutritionFacts,
            )
    }
}

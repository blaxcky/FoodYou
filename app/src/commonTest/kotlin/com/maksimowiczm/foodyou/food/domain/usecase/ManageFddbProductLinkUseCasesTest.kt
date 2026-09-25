package com.maksimowiczm.foodyou.food.domain.usecase

import com.maksimowiczm.foodyou.common.domain.database.TransactionProvider
import com.maksimowiczm.foodyou.common.domain.database.TransactionScope
import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.common.domain.food.NutrientValue
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.food.domain.entity.FddbProductSyncQueueItem
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.Product
import com.maksimowiczm.foodyou.food.domain.entity.ProductPortion
import com.maksimowiczm.foodyou.food.domain.repository.FddbProductSyncStatusRepository
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

class ManageFddbProductLinkUseCasesTest {
    @Test
    fun updatesOnlySourceUrl() = runBlocking {
        val original = product(ProductId, OldUrl)
        val repository = FakeProductRepository(listOf(original))
        val useCase = UpdateFddbProductLinkUseCase(repository, ImmediateTransactionProvider)

        val result = useCase.update(ProductId, "  $NonCanonicalNewUrl  ")

        assertEquals(NewUrl, assertIs<Result.Success<String, *>>(result).data)
        assertEquals(original.copy(source = original.source.copy(url = NewUrl)), repository[ProductId])
    }

    @Test
    fun rejectsInvalidAndAlreadyLinkedUrls() = runBlocking {
        val otherId = FoodId.Product(2)
        val repository =
            FakeProductRepository(
                listOf(product(ProductId, OldUrl), product(otherId, NonCanonicalNewUrl))
            )
        val useCase = UpdateFddbProductLinkUseCase(repository, ImmediateTransactionProvider)

        val invalid = useCase.update(ProductId, "https://example.com/not-fddb")
        val duplicate = useCase.update(ProductId, NewUrl)

        assertEquals(
            UpdateFddbProductLinkError.InvalidUrl,
            assertIs<Result.Error<*, UpdateFddbProductLinkError>>(invalid).error,
        )
        assertEquals(
            UpdateFddbProductLinkError.AlreadyLinked(otherId),
            assertIs<Result.Error<*, UpdateFddbProductLinkError>>(duplicate).error,
        )
        assertEquals(OldUrl, repository[ProductId].source.url)
    }

    @Test
    fun unlinkClearsOnlyUrlAndSyncStatus() = runBlocking {
        val original = product(ProductId, OldUrl)
        val repository = FakeProductRepository(listOf(original))
        val statusRepository = FakeStatusRepository()
        val useCase =
            UnlinkFddbProductUseCase(
                productRepository = repository,
                statusRepository = statusRepository,
                transactionProvider = ImmediateTransactionProvider,
            )

        val result = useCase.unlink(ProductId)

        assertEquals(Unit, assertIs<Result.Success<Unit, *>>(result).data)
        assertEquals(original.copy(source = original.source.copy(url = null)), repository[ProductId])
        assertEquals(listOf(ProductId), statusRepository.cleared)
    }

    private class FakeProductRepository(initialProducts: List<Product>) : ProductRepository {
        private val products = MutableStateFlow(initialProducts)

        operator fun get(id: FoodId.Product): Product = products.value.single { it.id == id }

        override fun observeProduct(id: FoodId.Product): Flow<Product?> =
            products.map { values -> values.firstOrNull { it.id == id } }

        override fun observeProductByBarcode(barcode: String): Flow<Product?> = flowOf(null)

        override suspend fun getProductByBarcode(barcode: String): Product? = null

        override suspend fun getProductBySource(type: FoodSource.Type, url: String): Product? =
            products.value.firstOrNull { it.source.type == type && it.source.url == url }

        override fun observeProducts(limit: Int, offset: Int): Flow<List<Product>> =
            products.map { it.drop(offset).take(limit) }

        override fun observeProductsBySource(
            type: FoodSource.Type,
            limit: Int,
            offset: Int,
        ): Flow<List<Product>> =
            products.map { values ->
                values.filter { it.source.type == type }.drop(offset).take(limit)
            }

        override fun observeProductCountBySource(type: FoodSource.Type): Flow<Int> =
            products.map { values -> values.count { it.source.type == type } }

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
            portions: List<ProductPortion>,
        ) = Unit

        override suspend fun deleteProduct(product: Product) = error("Must not delete product")

        override suspend fun deleteProductsBySource(type: FoodSource.Type): Int =
            error("Must not delete products")
    }

    private class FakeStatusRepository : FddbProductSyncStatusRepository {
        val cleared = mutableListOf<FoodId.Product>()

        override fun observeQueue(): Flow<List<FddbProductSyncQueueItem>> = flowOf(emptyList())

        override suspend fun getDueProducts(limit: Int): List<FddbProductSyncQueueItem> = emptyList()

        override suspend fun markAttempt(productId: FoodId.Product, attemptedAt: Instant) = Unit

        override suspend fun markSuccess(productId: FoodId.Product, syncedAt: Instant) = Unit

        override suspend fun markFailure(
            productId: FoodId.Product,
            attemptedAt: Instant,
            error: String,
        ) = Unit

        override suspend fun clear(productId: FoodId.Product) {
            cleared += productId
        }
    }

    private object ImmediateTransactionProvider : TransactionProvider {
        override suspend fun <T> withTransaction(block: suspend TransactionScope<T>.() -> T): T =
            block(
                object : TransactionScope<T> {
                    override suspend fun rollback(result: T) = Unit
                }
            )
    }

    private companion object {
        val ProductId = FoodId.Product(1)
        const val OldUrl = "https://fddb.info/db/de/lebensmittel/old_product/index.html"
        const val NewUrl = "https://fddb.info/db/de/lebensmittel/new_product/index.html"
        const val NonCanonicalNewUrl =
            "http://www.fddb.info/db/de/lebensmittel/new_product/index.html"

        fun product(id: FoodId.Product, url: String) =
            Product(
                id = id,
                name = "Product ${id.id}",
                brand = "Brand",
                barcode = "123",
                note = "Keep me",
                isLiquid = false,
                packageWeight = 250.0,
                servingWeight = 25.0,
                portions = listOf(ProductPortion("Piece", 25.0, ProductPortion.Unit.Gram)),
                source = FoodSource(FoodSource.Type.FDDB, url),
                isFavorite = true,
                isQuickCapture = true,
                nutritionFacts =
                    NutritionFacts(
                        energy = NutrientValue.Complete(100.0),
                        proteins = NutrientValue.Complete(1.0),
                        carbohydrates = NutrientValue.Complete(2.0),
                        fats = NutrientValue.Complete(3.0),
                    ),
            )
    }
}

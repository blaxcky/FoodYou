package com.maksimowiczm.foodyou.food.domain.usecase

import com.maksimowiczm.foodyou.common.domain.database.TransactionProvider
import com.maksimowiczm.foodyou.common.domain.database.TransactionScope
import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.common.domain.food.NutrientValue
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.food.domain.entity.FddbPortion
import com.maksimowiczm.foodyou.food.domain.entity.FddbProduct
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.Product
import com.maksimowiczm.foodyou.food.domain.repository.FddbAccessBlockedException
import com.maksimowiczm.foodyou.food.domain.repository.FddbProductGateway
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

class ResyncFddbProductUseCaseTest {
    @Test
    fun overwritesFddbNutritionWeightsLiquidFlagAndPortions() = runBlocking {
        val portions = listOf(FddbPortion("Piece", 42.0, FddbPortion.Unit.Gram))
        val remoteProduct =
            fddbProduct(
                nutritionFacts = nutrition(energy = 222.0),
                packageWeight = 500.0,
                servingWeight = 50.0,
                isLiquid = true,
                portions = portions,
            )
        val repository = FakeProductRepository(product(sourceUrl = Url))
        val useCase = useCase(repository = repository, gateway = FakeFddbProductGateway(remoteProduct))

        val result = useCase.resync(ProductId)

        assertIs<Result.Success<Unit, ResyncFddbProductError>>(result)
        val product = repository.product(ProductId)
        assertEquals(remoteProduct.nutritionFacts, product.nutritionFacts)
        assertEquals(500.0, product.packageWeight)
        assertEquals(50.0, product.servingWeight)
        assertEquals(true, product.isLiquid)
        assertEquals(portions, product.portions)
    }

    @Test
    fun preservesLocalIdentityFieldsAndSource() = runBlocking {
        val source = FoodSource(type = FoodSource.Type.FDDB, url = Url)
        val original =
            product(
                name = "Local name",
                brand = "Local brand",
                barcode = "1234567890123",
                note = "Local note",
                source = source,
            )
        val repository = FakeProductRepository(original)
        val useCase = useCase(repository = repository, gateway = FakeFddbProductGateway(fddbProduct()))

        useCase.resync(ProductId)

        val product = repository.product(ProductId)
        assertEquals("Local name", product.name)
        assertEquals("Local brand", product.brand)
        assertEquals("1234567890123", product.barcode)
        assertEquals("Local note", product.note)
        assertEquals(source, product.source)
    }

    @Test
    fun failsForNonFddbProduct() = runBlocking {
        val repository =
            FakeProductRepository(product(source = FoodSource(type = FoodSource.Type.User, url = Url)))
        val useCase = useCase(repository = repository)

        val result = useCase.resync(ProductId)

        assertEquals(
            ResyncFddbProductError.NotFddbProduct,
            assertIs<Result.Error<Unit, ResyncFddbProductError>>(result).error,
        )
    }

    @Test
    fun failsForMissingProduct() = runBlocking {
        val useCase = useCase(repository = FakeProductRepository(initialProduct = null))

        val result = useCase.resync(ProductId)

        assertEquals(
            ResyncFddbProductError.ProductNotFound(ProductId),
            assertIs<Result.Error<Unit, ResyncFddbProductError>>(result).error,
        )
    }

    @Test
    fun failsForMissingSourceUrl() = runBlocking {
        val repository =
            FakeProductRepository(product(source = FoodSource(type = FoodSource.Type.FDDB, url = null)))
        val useCase = useCase(repository = repository)

        val result = useCase.resync(ProductId)

        assertEquals(
            ResyncFddbProductError.MissingSourceUrl,
            assertIs<Result.Error<Unit, ResyncFddbProductError>>(result).error,
        )
    }

    @Test
    fun mapsNetworkOrParseFailure() = runBlocking {
        val repository = FakeProductRepository(product(sourceUrl = Url))
        val useCase =
            useCase(repository = repository, gateway = FakeFddbProductGateway(failing = true))

        val result = useCase.resync(ProductId)

        assertEquals(
            ResyncFddbProductError.NetworkOrParseFailed,
            assertIs<Result.Error<Unit, ResyncFddbProductError>>(result).error,
        )
    }

    @Test
    fun mapsBlockedAccess() = runBlocking {
        val repository = FakeProductRepository(product(sourceUrl = Url))
        val useCase =
            useCase(repository = repository, gateway = FakeFddbProductGateway(blocked = true))

        val result = useCase.resync(ProductId)

        assertEquals(
            ResyncFddbProductError.Blocked,
            assertIs<Result.Error<Unit, ResyncFddbProductError>>(result).error,
        )
    }

    private fun useCase(
        repository: FakeProductRepository = FakeProductRepository(product(sourceUrl = Url)),
        gateway: FddbProductGateway = FakeFddbProductGateway(fddbProduct()),
    ) =
        ResyncFddbProductUseCase(
            productRepository = repository,
            fddbProductGateway = gateway,
            transactionProvider = ImmediateTransactionProvider,
            logger = NoopLogger,
        )

    private class FakeFddbProductGateway(
        private val product: FddbProduct = fddbProduct(),
        private val blocked: Boolean = false,
        private val failing: Boolean = false,
    ) : FddbProductGateway {
        override suspend fun getProduct(url: String): FddbProduct {
            if (blocked) {
                throw FddbAccessBlockedException("Blocked")
            }
            if (failing) {
                error("Failed")
            }
            return product
        }
    }

    private class FakeProductRepository(initialProduct: Product?) : ProductRepository {
        private val products = MutableStateFlow(listOfNotNull(initialProduct))

        fun product(id: FoodId.Product): Product = products.value.single { it.id == id }

        override fun observeProduct(id: FoodId.Product): Flow<Product?> =
            products.map { products -> products.firstOrNull { it.id == id } }

        override fun observeProductByBarcode(barcode: String): Flow<Product?> =
            products.map { products -> products.firstOrNull { it.barcode == barcode } }

        override suspend fun getProductByBarcode(barcode: String): Product? =
            products.value.firstOrNull { it.barcode == barcode }

        override suspend fun getProductBySource(type: FoodSource.Type, url: String): Product? =
            products.value.firstOrNull { it.source.type == type && it.source.url == url }

        override fun observeProducts(limit: Int, offset: Int): Flow<List<Product>> =
            products.map { it.drop(offset).take(limit) }

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
        ): FoodId.Product = error("Not needed")

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
        ): FoodId.Product? = error("Not needed")

        override suspend fun updateProduct(product: Product) {
            products.value = products.value.map { if (it.id == product.id) product else it }
        }

        override suspend fun replaceProductPortions(
            productId: FoodId.Product,
            sourceType: FoodSource.Type,
            portions: List<FddbPortion>,
        ) {
            products.value =
                products.value.map { product ->
                    if (product.id == productId) product.copy(portions = portions) else product
                }
        }

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

    private object NoopLogger : Logger {
        override fun d(tag: String, throwable: Throwable?, message: () -> String) = Unit

        override fun w(tag: String, throwable: Throwable?, message: () -> String) = Unit

        override fun e(tag: String, throwable: Throwable?, message: () -> String) = Unit

        override fun i(tag: String, throwable: Throwable?, message: () -> String) = Unit
    }

    private companion object {
        val ProductId = FoodId.Product(1)
        const val Url = "https://fddb.info/db/de/lebensmittel/product/index.html"

        fun product(
            name: String = "Product",
            brand: String? = "Brand",
            barcode: String? = null,
            note: String? = null,
            isLiquid: Boolean = false,
            packageWeight: Double? = 100.0,
            servingWeight: Double? = 10.0,
            source: FoodSource = FoodSource(FoodSource.Type.FDDB),
            sourceUrl: String? = null,
            nutritionFacts: NutritionFacts = nutrition(energy = 100.0),
            portions: List<FddbPortion> = emptyList(),
        ) =
            Product(
                id = ProductId,
                name = name,
                brand = brand,
                barcode = barcode,
                note = note,
                isLiquid = isLiquid,
                packageWeight = packageWeight,
                servingWeight = servingWeight,
                portions = portions,
                source = source.copy(url = sourceUrl ?: source.url),
                nutritionFacts = nutritionFacts,
            )

        fun fddbProduct(
            isLiquid: Boolean = false,
            packageWeight: Double? = 200.0,
            servingWeight: Double? = 20.0,
            portions: List<FddbPortion> = emptyList(),
            nutritionFacts: NutritionFacts = nutrition(energy = 150.0),
        ) =
            FddbProduct(
                name = "Remote name",
                brand = "Remote brand",
                barcode = "999",
                isLiquid = isLiquid,
                packageWeight = packageWeight,
                servingWeight = servingWeight,
                portions = portions,
                nutritionFacts = nutritionFacts,
            )

        fun nutrition(energy: Double) =
            NutritionFacts(
                energy = NutrientValue.Complete(energy),
                proteins = NutrientValue.Complete(1.0),
                carbohydrates = NutrientValue.Complete(2.0),
                fats = NutrientValue.Complete(3.0),
            )
    }
}

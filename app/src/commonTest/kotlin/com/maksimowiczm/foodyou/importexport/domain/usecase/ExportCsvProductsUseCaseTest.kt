package com.maksimowiczm.foodyou.importexport.domain.usecase

import com.maksimowiczm.foodyou.common.domain.database.TransactionProvider
import com.maksimowiczm.foodyou.common.domain.database.TransactionScope
import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.Product
import com.maksimowiczm.foodyou.food.domain.entity.ProductPortion
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository
import com.maksimowiczm.foodyou.importexport.domain.entity.ProductField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

class ExportCsvProductsUseCaseTest {
    @Test
    fun escapesQuotesInStringFields() = runBlocking {
        val product =
            Product(
                id = FoodId.Product(1),
                name = "Hazelnut \"Spread\"",
                brand = null,
                barcode = null,
                note = "Use \"thinly\"",
                isLiquid = false,
                packageWeight = null,
                servingWeight = null,
                source = FoodSource(FoodSource.Type.User),
                nutritionFacts = NutritionFacts.Empty,
            )
        val useCase =
            ExportCsvProductsUseCaseImpl(
                productRepository = FakeProductRepository(listOf(product)),
                transactionProvider = ImmediateTransactionProvider,
            )

        val lines = useCase.export(listOf(ProductField.Name, ProductField.Note), null).toList()

        assertEquals(
            listOf("\"Name\",\"Note\"", "\"Hazelnut \"\"Spread\"\"\",\"Use \"\"thinly\"\"\""),
            lines,
        )
    }

    private class FakeProductRepository(private val products: List<Product>) : ProductRepository {
        override fun observeProducts(limit: Int, offset: Int): Flow<List<Product>> =
            flowOf(products.drop(offset).take(limit))

        override fun observeProductsBySource(
            type: FoodSource.Type,
            limit: Int,
            offset: Int,
        ): Flow<List<Product>> =
            flowOf(products.filter { it.source.type == type }.drop(offset).take(limit))

        override fun observeProduct(id: FoodId.Product): Flow<Product?> = error("Not used")

        override fun observeProductByBarcode(barcode: String): Flow<Product?> = error("Not used")

        override suspend fun getProductByBarcode(barcode: String): Product? = error("Not used")

        override suspend fun getProductBySource(type: FoodSource.Type, url: String): Product? =
            error("Not used")

        override fun observeProductCountBySource(type: FoodSource.Type): Flow<Int> =
            error("Not used")

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

        override suspend fun updateProduct(product: Product) = error("Not used")

        override suspend fun replaceProductPortions(
            productId: FoodId.Product,
            sourceType: FoodSource.Type,
            portions: List<ProductPortion>,
        ) = error("Not used")

        override suspend fun updateProductPortions(
            productId: FoodId.Product,
            portions: List<ProductPortion>,
        ) = error("Not used")

        override suspend fun deleteProduct(product: Product) = error("Not used")

        override suspend fun deleteProductsBySource(type: FoodSource.Type): Int =
            error("Not used")
    }

    private object ImmediateTransactionProvider : TransactionProvider {
        override suspend fun <T> withTransaction(block: suspend TransactionScope<T>.() -> T): T =
            block(
                object : TransactionScope<T> {
                    override suspend fun rollback(result: T) = Unit
                }
            )
    }
}

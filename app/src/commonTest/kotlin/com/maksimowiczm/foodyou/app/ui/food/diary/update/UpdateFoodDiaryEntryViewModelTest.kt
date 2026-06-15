package com.maksimowiczm.foodyou.app.ui.food.diary.update

import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.food.domain.entity.FddbPortion
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.Product
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFoodProduct
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFoodRecipe
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFoodRecipeIngredient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class UpdateFoodDiaryEntryViewModelTest {
    @Test
    fun editablePortionsUsesOriginalProductMatchedBySource() = runTest {
        val portions =
            listOf(
                FddbPortion(label = "1 Piece", amount = 10.0, unit = FddbPortion.Unit.Gram),
                FddbPortion(label = "1 Portion", amount = 250.0, unit = FddbPortion.Unit.Gram),
            )
        val repository =
            FakeProductRepository(
                products =
                    listOf(
                        product(
                            id = FoodId.Product(1),
                            source = FoodSource(FoodSource.Type.FDDB, ProductUrl),
                            portions = portions,
                        )
                    )
            )
        val diaryProduct =
            diaryProduct(
                id = FoodId.Product(99),
                source = FoodSource(FoodSource.Type.FDDB, ProductUrl),
            )

        assertEquals(portions, diaryProduct.editablePortions(repository))
        assertEquals(listOf(FoodSource.Type.FDDB to ProductUrl), repository.sourceLookups)
        assertTrue(repository.observedProductIds.isEmpty())
    }

    @Test
    fun editablePortionsReturnsEmptyForProductWithoutSourceUrl() = runTest {
        listOf(null, "", " ").forEach { sourceUrl ->
            val repository = FakeProductRepository()
            val diaryProduct =
                diaryProduct(source = FoodSource(FoodSource.Type.FDDB, sourceUrl))

            assertEquals(emptyList(), diaryProduct.editablePortions(repository))
            assertTrue(repository.sourceLookups.isEmpty())
            assertTrue(repository.observedProductIds.isEmpty())
        }
    }

    @Test
    fun editablePortionsReturnsEmptyForRecipe() = runTest {
        val repository = FakeProductRepository()
        val recipe =
            DiaryFoodRecipe(
                id = FoodId.Recipe(1),
                name = "Recipe",
                servings = 1,
                ingredients =
                    listOf(
                        DiaryFoodRecipeIngredient(
                            food = diaryProduct(),
                            measurement = Measurement.Gram(100.0),
                        )
                    ),
                isLiquid = false,
                note = null,
            )

        assertEquals(emptyList(), recipe.editablePortions(repository))
        assertTrue(repository.sourceLookups.isEmpty())
        assertTrue(repository.observedProductIds.isEmpty())
    }

    private class FakeProductRepository(private val products: List<Product> = emptyList()) :
        ProductRepository {
        val observedProductIds = mutableListOf<FoodId.Product>()
        val sourceLookups = mutableListOf<Pair<FoodSource.Type, String>>()

        override fun observeProduct(id: FoodId.Product): Flow<Product?> {
            observedProductIds += id
            return flowOf(products.firstOrNull { it.id == id })
        }

        override fun observeProductByBarcode(barcode: String): Flow<Product?> = flowOf(null)

        override suspend fun getProductByBarcode(barcode: String): Product? = null

        override suspend fun getProductBySource(type: FoodSource.Type, url: String): Product? {
            sourceLookups += type to url
            return products.firstOrNull { it.source.type == type && it.source.url == url }
        }

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
            portions: List<FddbPortion>,
        ) = error("Not used")

        override suspend fun deleteProduct(product: Product) = error("Not used")

        override suspend fun deleteProductsBySource(type: FoodSource.Type): Int = error("Not used")
    }

    private companion object {
        const val ProductUrl = "https://fddb.info/db/de/lebensmittel/example/index.html"

        fun diaryProduct(
            id: FoodId.Product = FoodId.Product(99),
            source: FoodSource = FoodSource(FoodSource.Type.FDDB, ProductUrl),
        ): DiaryFoodProduct =
            DiaryFoodProduct(
                id = id,
                name = "Snapshot product",
                nutritionFacts = NutritionFacts(),
                servingWeight = 20.0,
                totalWeight = 100.0,
                isLiquid = false,
                source = source,
                note = null,
            )

        fun product(
            id: FoodId.Product,
            source: FoodSource,
            portions: List<FddbPortion>,
        ): Product =
            Product(
                id = id,
                name = "Original product",
                brand = null,
                barcode = null,
                note = null,
                isLiquid = false,
                packageWeight = 100.0,
                servingWeight = 20.0,
                portions = portions,
                source = source,
                nutritionFacts = NutritionFacts(),
            )
    }
}

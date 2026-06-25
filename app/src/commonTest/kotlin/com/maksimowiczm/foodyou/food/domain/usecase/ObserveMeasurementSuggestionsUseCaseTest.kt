package com.maksimowiczm.foodyou.food.domain.usecase

import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.measurement.MeasurementType
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.Product
import com.maksimowiczm.foodyou.food.domain.entity.ProductPortion
import com.maksimowiczm.foodyou.food.domain.entity.Recipe
import com.maksimowiczm.foodyou.food.domain.entity.RecipeIngredient
import com.maksimowiczm.foodyou.food.domain.repository.FoodMeasurementSuggestionRepository
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository
import com.maksimowiczm.foodyou.food.domain.repository.RecipeRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class ObserveMeasurementSuggestionsUseCaseTest {

    @Test
    fun latestStoredPackageWinsOverGeneratedDefaults() = runTest {
        val foodId = FoodId.Product(1)
        val useCase =
            useCase(
                product = product(foodId, packageWeight = 500.0),
                suggestions = listOf(Measurement.Package(1.0)),
            )

        assertEquals(Measurement.Package(1.0), useCase.observeLatestOrDefault(foodId).first())
    }

    @Test
    fun emptyHistoryReturnsDefaultMeasurement() = runTest {
        val foodId = FoodId.Product(1)
        val useCase = useCase(product = product(foodId), suggestions = emptyList())

        assertEquals(Measurement.Gram(100.0), useCase.observeLatestOrDefault(foodId).first())
    }

    private fun useCase(
        product: Product,
        suggestions: List<Measurement>,
    ): ObserveMeasurementSuggestionsUseCase =
        ObserveMeasurementSuggestionsUseCase(
            observeFoodUseCase =
                ObserveFoodUseCase(
                    productRepository = FakeProductRepository(product),
                    recipeRepository = FakeRecipeRepository,
                ),
            repository = FakeFoodMeasurementSuggestionRepository(suggestions),
        )

    private fun product(
        id: FoodId.Product,
        packageWeight: Double? = null,
    ) =
        Product(
            id = id,
            name = "Food",
            brand = null,
            barcode = null,
            note = null,
            isLiquid = false,
            packageWeight = packageWeight,
            servingWeight = null,
            source = FoodSource(FoodSource.Type.User),
            nutritionFacts = NutritionFacts(),
        )
}

private class FakeFoodMeasurementSuggestionRepository(private val suggestions: List<Measurement>) :
    FoodMeasurementSuggestionRepository {
    override suspend fun insert(foodId: FoodId, measurement: Measurement) = Unit

    override suspend fun findLatestByProductIdAndType(
        productId: FoodId.Product,
        type: MeasurementType,
    ): Measurement? = unused()

    override fun observeByFoodId(foodId: FoodId, limit: Int): Flow<List<Measurement>> =
        flowOf(suggestions.take(limit))
}

private class FakeProductRepository(private val product: Product) : ProductRepository {
    override fun observeProduct(id: FoodId.Product): Flow<Product?> = flowOf(product)

    override fun observeProductByBarcode(barcode: String): Flow<Product?> = unused()

    override suspend fun getProductByBarcode(barcode: String): Product? = unused()

    override suspend fun getProductBySource(type: FoodSource.Type, url: String): Product? = unused()

    override fun observeProducts(limit: Int, offset: Int): Flow<List<Product>> = unused()

    override fun observeProductsBySource(
        type: FoodSource.Type,
        limit: Int,
        offset: Int,
    ): Flow<List<Product>> = unused()

    override fun observeProductCountBySource(type: FoodSource.Type): Flow<Int> = unused()

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
    ): FoodId.Product = unused()

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
    ): FoodId.Product? = unused()

    override suspend fun updateProduct(product: Product) = unused()

    override suspend fun replaceProductPortions(
        productId: FoodId.Product,
        sourceType: FoodSource.Type,
        portions: List<ProductPortion>,
    ) = unused()

    override suspend fun updateProductPortions(
        productId: FoodId.Product,
        portions: List<ProductPortion>,
    ) = unused()

    override suspend fun deleteProduct(product: Product) = unused()

    override suspend fun deleteProductsBySource(type: FoodSource.Type): Int = unused()
}

private object FakeRecipeRepository : RecipeRepository {
    override fun observeRecipe(recipeId: FoodId.Recipe): Flow<Recipe?> = flowOf(null)

    override suspend fun insertRecipe(
        name: String,
        servings: Int,
        note: String?,
        isLiquid: Boolean,
        ingredients: List<RecipeIngredient>,
    ): FoodId.Recipe = unused()

    override suspend fun updateRecipe(recipe: Recipe) = unused()

    override suspend fun deleteRecipe(recipe: Recipe) = unused()
}

private fun unused(): Nothing = error("Unused in this test")

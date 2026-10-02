package com.maksimowiczm.foodyou.food.search.domain

import androidx.paging.ExperimentalPagingApi
import androidx.paging.PagingConfig
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.RemoteMediator
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.event.EventBus
import com.maksimowiczm.foodyou.common.domain.event.IntegrationEvent
import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.domain.search.SearchQuery
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.food.domain.entity.*
import com.maksimowiczm.foodyou.food.domain.repository.*
import com.maksimowiczm.foodyou.food.domain.usecase.SetProductFavoriteUseCase
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone

internal class FoodSearchTestFixture {
    val events = mutableListOf<IntegrationEvent>()
    val preferences = object : UserPreferencesRepository<FoodSearchPreferences> {
        val value = MutableStateFlow(FoodSearchPreferences(
            FoodSearchPreferences.OpenFoodFacts(false), FoodSearchPreferences.Usda(false, null),
        ))
        override fun observe() = value
        override suspend fun update(transform: FoodSearchPreferences.() -> FoodSearchPreferences) {
            value.value = value.value.transform()
        }
    }
    val dateProvider = object : DateProvider {
        override fun nowInstant() = Instant.parse("2026-10-02T12:00:00Z")
        override fun observeInstant(interval: Duration) = flowOf(nowInstant())
        override fun observeDate(timeZone: TimeZone) = flowOf(LocalDate(2026, 10, 2))
    }
    val history = TestHistoryRepository()
    class TestHistoryRepository : FoodSearchHistoryRepository {
        val entries = MutableStateFlow<List<FoodSearchHistory>>(emptyList())
        var requestedLimit = 0
        override fun observeHistory(limit: Int): Flow<List<FoodSearchHistory>> {
            requestedLimit = limit
            return entries.map { it.take(limit) }
        }
        override suspend fun insert(entry: FoodSearchHistory) { entries.value += entry }
    }
    val repository = RecordingFoodSearchRepository()
    private val eventBus = object : EventBus {
        override val events: Flow<IntegrationEvent> = emptyFlow()
        override fun publish(integrationEvent: IntegrationEvent) { this@FoodSearchTestFixture.events += integrationEvent }
    }
    @OptIn(ExperimentalPagingApi::class)
    private val mediators = object : FoodRemoteMediatorFactoryAggregate {
        private val unused = object : ProductRemoteMediatorFactory {
            override suspend fun <K : Any, T : Any> create(query: SearchQuery, pageSize: Int): RemoteMediator<K, T>? = null
        }
        override val openFoodFactsRemoteMediatorFactory = unused
        override val usdaRemoteMediatorFactory = unused
    }
    val useCase = FoodSearchUseCase(repository, preferences, mediators, eventBus, dateProvider)
    val favoriteUseCase = SetProductFavoriteUseCase(UnusedProductRepository)
}

internal class RecordingFoodSearchRepository : FoodSearchRepository {
    data class Call(val query: SearchQuery, val excludedRecipe: FoodId.Recipe?)
    val calls = mutableListOf<Call>()
    val foods = mutableMapOf<String?, List<FoodSearch>>()
    val sourceFoods = mutableMapOf<Pair<String?, FoodSource.Type>, List<FoodSearch>>()
    val cancelledQueries = mutableListOf<String?>()
    val gates = mutableMapOf<String, CompletableDeferred<Unit>>()
    val sourceCounts = mutableMapOf<Pair<String?, FoodSource.Type?>, MutableStateFlow<Int>>()

    private fun pages(query: SearchQuery, excludedRecipeId: FoodId.Recipe?, source: FoodSource.Type? = null): Flow<PagingData<FoodSearch>> {
        calls += Call(query, excludedRecipeId)
        val items = source?.let { sourceFoods[query.query to it] } ?: foods[query.query] ?: emptyList()
        return flowOf(PagingData.from(items, sourceLoadStates = completeLoadStates))
    }
    private fun count(query: SearchQuery, source: FoodSource.Type?, excludedRecipeId: FoodId.Recipe?): Flow<Int> {
        calls += Call(query, excludedRecipeId)
        return flow {
            try {
                gates[query.query]?.await()
                emitAll(sourceCounts.getOrPut(query.query to source) { MutableStateFlow(1) })
            } finally { cancelledQueries += query.query }
        }
    }
    override fun search(query: SearchQuery, source: FoodSource.Type, config: PagingConfig, remoteMediatorFactory: RemoteMediatorFactory?, excludedRecipeId: FoodId.Recipe?) = pages(query, excludedRecipeId, source)
    override fun search(query: SearchQuery, sources: Set<FoodSource.Type>, config: PagingConfig, excludedRecipeId: FoodId.Recipe?) = pages(query, excludedRecipeId)
    override fun searchRecent(query: SearchQuery, config: PagingConfig, now: LocalDateTime, excludedRecipeId: FoodId.Recipe?) = pages(query, excludedRecipeId)
    override fun searchFoodCount(query: SearchQuery, source: FoodSource.Type, excludedRecipeId: FoodId.Recipe?) = count(query, source, excludedRecipeId)
    override fun searchFoodCount(query: SearchQuery, sources: Set<FoodSource.Type>, excludedRecipeId: FoodId.Recipe?) = count(query, null, excludedRecipeId)
    override fun searchRecentFoodCount(query: SearchQuery, now: LocalDateTime, excludedRecipeId: FoodId.Recipe?) = count(query, FoodSource.Type.User, excludedRecipeId)
}

private object UnusedProductRepository : ProductRepository {
    override fun observeProduct(id: FoodId.Product): Flow<Product?> = error("Unused")
    override fun observeProductByBarcode(barcode: String): Flow<Product?> = error("Unused")
    override suspend fun getProductByBarcode(barcode: String): Product? = error("Unused")
    override suspend fun getProductBySource(type: FoodSource.Type, url: String): Product? = error("Unused")
    override fun observeProducts(limit: Int, offset: Int): Flow<List<Product>> = error("Unused")
    override fun observeProductsBySource(type: FoodSource.Type, limit: Int, offset: Int): Flow<List<Product>> = error("Unused")
    override fun observeProductCountBySource(type: FoodSource.Type): Flow<Int> = error("Unused")
    override suspend fun insertProduct(name: String, brand: String?, barcode: String?, note: String?, isLiquid: Boolean, packageWeight: Double?, servingWeight: Double?, source: FoodSource, nutritionFacts: NutritionFacts): FoodId.Product = error("Unused")
    override suspend fun insertUniqueProduct(name: String, brand: String?, barcode: String?, note: String?, isLiquid: Boolean, packageWeight: Double?, servingWeight: Double?, source: FoodSource, nutritionFacts: NutritionFacts): FoodId.Product? = error("Unused")
    override suspend fun updateProduct(product: Product): Unit = error("Unused")
    override suspend fun replaceProductPortions(productId: FoodId.Product, sourceType: FoodSource.Type, portions: List<ProductPortion>): Unit = error("Unused")
    override suspend fun deleteProduct(product: Product): Unit = error("Unused")
    override suspend fun deleteProductsBySource(type: FoodSource.Type): Int = error("Unused")
}

private val completeLoadStates = LoadStates(
    refresh = LoadState.NotLoading(false),
    prepend = LoadState.NotLoading(true),
    append = LoadState.NotLoading(true),
)

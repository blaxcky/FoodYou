package com.maksimowiczm.foodyou.app.ui.food.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.maksimowiczm.foodyou.app.ui.food.search.RemoteStatus.Companion.toRemoteStatus
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.common.domain.search.searchQuery
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.common.extension.combine
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.repository.FoodSearchHistoryRepository
import com.maksimowiczm.foodyou.food.search.domain.FoodSearchPreferences
import com.maksimowiczm.foodyou.food.search.domain.FoodSearchRepository
import com.maksimowiczm.foodyou.food.search.domain.FoodSearchUseCase
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

internal class FoodSearchViewModel(
    private val excludedRecipeId: FoodId.Recipe?,
    private val foodSearchPreferencesRepository: UserPreferencesRepository<FoodSearchPreferences>,
    searchHistoryRepository: FoodSearchHistoryRepository,
    private val foodSearchRepository: FoodSearchRepository,
    private val foodSearchUseCase: FoodSearchUseCase,
    private val dateProvider: DateProvider,
) : ViewModel() {

    // Use shared flow to allow emitting same value multiple times
    private val searchQuery =
        MutableSharedFlow<String?>(replay = 1).apply { runBlocking { emit(null) } }

    private val filter = MutableStateFlow(FoodFilter())

    fun search(query: String?) {
        viewModelScope.launch { searchQuery.emit(query) }
    }

    fun changeSource(source: FoodFilter.Source) {
        filter.update { it.copy(source = source) }
    }

    private val foodPreferences =
        foodSearchPreferencesRepository
            .observe()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(2_000),
                initialValue = runBlocking { foodSearchPreferencesRepository.observe().first() },
            )

    private val recentFoodPages =
        searchQuery.flatMapLatest { query ->
            foodSearchUseCase.searchRecent(query, excludedRecipeId).cachedIn(viewModelScope)
        }
    private val recentFoodState =
        searchQuery
            .flatMapLatest { query ->
                foodSearchRepository.searchRecentFoodCount(
                    query = searchQuery(query),
                    now = dateProvider.now(),
                    excludedRecipeId = excludedRecipeId,
                )
            }
            .map { count ->
                FoodSourceUiState(
                    remoteEnabled = RemoteStatus.LocalOnly,
                    pages = recentFoodPages,
                    count = count,
                    alwaysShowFilter = true,
                )
            }

    private val yourFoodPages = observeFoodPages(FoodSource.Type.User).cachedIn(viewModelScope)
    private val yourFoodState =
        observeFoodCount(FoodSource.Type.User).map { count ->
            FoodSourceUiState(
                remoteEnabled = RemoteStatus.LocalOnly,
                pages = yourFoodPages,
                count = count,
                alwaysShowFilter = true,
            )
        }

    private val openFoodFactsPages =
        observeFoodPages(FoodSource.Type.OpenFoodFacts).cachedIn(viewModelScope)
    private val openFoodFactsState =
        combine(observeFoodCount(FoodSource.Type.OpenFoodFacts), foodPreferences) { count, prefs ->
            FoodSourceUiState(
                remoteEnabled = prefs.isOpenFoodFactsEnabled.toRemoteStatus(),
                pages = openFoodFactsPages,
                count = count,
            )
        }

    private val usdaPages = observeFoodPages(FoodSource.Type.USDA).cachedIn(viewModelScope)
    private val usdaState =
        combine(observeFoodCount(FoodSource.Type.USDA), foodPreferences) { count, prefs ->
            FoodSourceUiState(
                remoteEnabled = prefs.isUsdaEnabled.toRemoteStatus(),
                pages = usdaPages,
                count = count,
            )
        }

    private val swissPages =
        observeFoodPages(FoodSource.Type.SwissFoodCompositionDatabase).cachedIn(viewModelScope)
    private val swissState =
        observeFoodCount(FoodSource.Type.SwissFoodCompositionDatabase).map { count ->
            FoodSourceUiState(
                remoteEnabled = RemoteStatus.LocalOnly,
                pages = swissPages,
                count = count,
            )
        }

    private val fddbPages = observeFoodPages(FoodSource.Type.FDDB).cachedIn(viewModelScope)
    private val fddbState =
        observeFoodCount(FoodSource.Type.FDDB).map { count ->
            FoodSourceUiState(
                remoteEnabled = RemoteStatus.LocalOnly,
                pages = fddbPages,
                count = count,
            )
        }

    private val allFoodSources =
        combine(
            observeFoodCount(FoodSource.Type.FDDB),
            observeFoodCount(FoodSource.Type.SwissFoodCompositionDatabase),
            foodPreferences,
        ) { fddbCount, swissCount, prefs ->
            buildSet {
                add(FoodSource.Type.User)

                if (prefs.isOpenFoodFactsEnabled) {
                    add(FoodSource.Type.OpenFoodFacts)
                }

                if (prefs.isUsdaEnabled) {
                    add(FoodSource.Type.USDA)
                }

                if (swissCount > 0) {
                    add(FoodSource.Type.SwissFoodCompositionDatabase)
                }

                if (fddbCount > 0) {
                    add(FoodSource.Type.FDDB)
                }
            }
        }

    private val allFoodPages =
        combine(searchQuery, allFoodSources) { query, sources -> query to sources }
            .flatMapLatest { (query, sources) ->
                foodSearchRepository.search(
                    query = searchQuery(query),
                    sources = sources,
                    config = PagingConfig(pageSize = PAGE_SIZE),
                    excludedRecipeId = excludedRecipeId,
                )
            }
            .cachedIn(viewModelScope)
    private val allFoodState =
        combine(searchQuery, allFoodSources) { query, sources -> query to sources }
            .flatMapLatest { (query, sources) ->
                foodSearchRepository.searchFoodCount(
                    query = searchQuery(query),
                    sources = sources,
                    excludedRecipeId = excludedRecipeId,
                )
            }
            .map { count ->
                FoodSourceUiState(
                    remoteEnabled = RemoteStatus.LocalOnly,
                    pages = allFoodPages,
                    count = count,
                    alwaysShowFilter = true,
                )
            }

    private fun observeFoodCount(source: FoodSource.Type) =
        searchQuery.flatMapLatest { query ->
            foodSearchRepository.searchFoodCount(
                query = searchQuery(query),
                source = source,
                excludedRecipeId = excludedRecipeId,
            )
        }

    private fun observeFoodPages(source: FoodSource.Type) =
        searchQuery.flatMapLatest { query ->
            foodSearchUseCase.search(query, source, excludedRecipeId)
        }

    private val searchHistory =
        searchHistoryRepository
            .observeHistory(limit = 10)
            .map { list -> list.map { it.query } }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(2_000),
                initialValue = emptyList(),
            )

    private val sourceStates =
        listOf(
                FoodFilter.Source.All to allFoodState,
                FoodFilter.Source.Recent to recentFoodState,
                FoodFilter.Source.YourFood to yourFoodState,
                FoodFilter.Source.OpenFoodFacts to openFoodFactsState,
                FoodFilter.Source.USDA to usdaState,
                FoodFilter.Source.SwissFoodCompositionDatabase to swissState,
                FoodFilter.Source.FDDB to fddbState,
            )
            .map { (source, state) -> state.map { source to it } }
            .combine { states -> states.toMap() }

    val uiState =
        combine(sourceStates, filter, searchHistory) { sourceStates, filter, searchHistory ->
            FoodSearchUiState(
                sources = sourceStates,
                filter = filter,
                recentSearches = searchHistory.map { it.query },
            )
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(2_000),
                initialValue =
                    FoodSearchUiState(
                        sources = emptyMap(),
                        filter = FoodFilter(),
                        recentSearches = emptyList(),
                    ),
            )

    init {
        uiState
            .map { uiState ->
                val currentSourceIsAvailable = uiState.currentSourceState?.shouldShowFilter != false
                if (currentSourceIsAvailable) {
                    return@map null
                }

                FoodFilter.DefaultFilter
            }
            .onEach { source ->
                if (source != null) {
                    changeSource(source)
                }
            }
            .launchIn(viewModelScope)

        searchQuery
            .flatMapLatest { query ->
                if (query == null) {
                    return@flatMapLatest emptyFlow()
                }

                val switchFlow =
                    combine(filter, uiState) { currentFilter, uiState ->
                        if (
                            (currentFilter.source != FoodFilter.Source.All &&
                                currentFilter.source != FoodFilter.Source.Recent &&
                                currentFilter.source != FoodFilter.Source.YourFood) ||
                                uiState.currentSourceCount.positive()
                        ) {
                            return@combine
                        }

                        val recentCount = uiState.visibleCount(FoodFilter.Source.Recent)
                        if (recentCount.positive()) {
                            changeSource(FoodFilter.Source.Recent)
                            return@combine
                        }

                        val yourFoodCount = uiState.visibleCount(FoodFilter.Source.YourFood)
                        if (yourFoodCount.positive()) {
                            changeSource(FoodFilter.Source.YourFood)
                            return@combine
                        }

                        val openFoodFactsCount =
                            uiState.visibleCount(FoodFilter.Source.OpenFoodFacts)
                        if (openFoodFactsCount.positive()) {
                            changeSource(FoodFilter.Source.OpenFoodFacts)
                            return@combine
                        }

                        val usdaCount = uiState.visibleCount(FoodFilter.Source.USDA)
                        if (usdaCount.positive()) {
                            changeSource(FoodFilter.Source.USDA)
                            return@combine
                        }

                        val swissCount =
                            uiState.visibleCount(FoodFilter.Source.SwissFoodCompositionDatabase)
                        if (swissCount.positive()) {
                            changeSource(FoodFilter.Source.SwissFoodCompositionDatabase)
                            return@combine
                        }

                        val fddbCount = uiState.visibleCount(FoodFilter.Source.FDDB)
                        if (fddbCount.positive()) {
                            changeSource(FoodFilter.Source.FDDB)
                            return@combine
                        }
                    }

                val now = Clock.System.now().toEpochMilliseconds()
                val deadline = now + 100L
                switchFlow.takeWhile { Clock.System.now().toEpochMilliseconds() < deadline }
            }
            .launchIn(viewModelScope)
    }
}

private const val PAGE_SIZE = 30

private fun FoodSearchUiState.visibleCount(source: FoodFilter.Source): Int? =
    sources[source]?.takeIf { it.shouldShowFilter }?.count

@OptIn(ExperimentalContracts::class)
private fun Int?.positive(): Boolean {
    contract { returns(true) implies (this@positive != null) }

    return this != null && this > 0
}

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
import com.maksimowiczm.foodyou.food.domain.usecase.SetProductFavoriteUseCase
import com.maksimowiczm.foodyou.food.search.domain.FoodSearchPreferences
import com.maksimowiczm.foodyou.food.search.domain.FoodSearchRepository
import com.maksimowiczm.foodyou.food.search.domain.FoodSearchUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class FoodSearchViewModel(
    private val excludedRecipeId: FoodId.Recipe?,
    private val foodSearchPreferencesRepository: UserPreferencesRepository<FoodSearchPreferences>,
    searchHistoryRepository: FoodSearchHistoryRepository,
    private val foodSearchRepository: FoodSearchRepository,
    private val foodSearchUseCase: FoodSearchUseCase,
    private val setProductFavoriteUseCase: SetProductFavoriteUseCase,
    private val dateProvider: DateProvider,
) : ViewModel() {
    private data class SearchRequest(val query: String?, val immediately: Boolean)
    private data class SearchResults(
        val query: String?,
        val sources: Map<FoodFilter.Source, FoodSourceUiState> = emptyMap(),
    )

    private val inputQuery = MutableStateFlow<String?>(null)
    private val requests = MutableStateFlow(SearchRequest(null, immediately = true))
    private val filter = MutableStateFlow(FoodFilter())
    private val historyTab = MutableStateFlow(FoodSearchHistoryTab.RecentFood)
    private var allowAutomaticSourceSwitch = true

    fun changeQuery(query: String?) {
        val normalized = searchQuery(query).query
        if (inputQuery.value == normalized) return
        inputQuery.value = normalized
        allowAutomaticSourceSwitch = true
        requests.value = SearchRequest(normalized, immediately = normalized == null)
    }

    /** Immediate search for Enter, history, restored text and barcode scans. */
    fun search(query: String?) {
        val normalized = searchQuery(query).query
        if (inputQuery.value != normalized) allowAutomaticSourceSwitch = true
        inputQuery.value = normalized
        if (requests.value.query == normalized && results.value.query == normalized &&
            results.value.sources.isNotEmpty()
        ) return
        requests.value = SearchRequest(normalized, immediately = true)
    }

    fun recordSearch(query: String?) = foodSearchUseCase.recordSearch(query)

    fun changeHistoryTab(tab: FoodSearchHistoryTab) {
        historyTab.value = tab
    }

    fun changeSource(source: FoodFilter.Source) {
        allowAutomaticSourceSwitch = false
        filter.update { it.copy(source = source) }
    }

    /** Called only after the displayed page has finished refreshing. */
    fun resultsLoaded(query: String?, source: FoodFilter.Source) {
        val state = uiState.value
        if (!allowAutomaticSourceSwitch || state.isPending || query == null ||
            query != inputQuery.value || query != state.query || source != state.filter.source ||
            source !in listOf(FoodFilter.Source.All, FoodFilter.Source.Recent, FoodFilter.Source.YourFood) ||
            state.currentSourceCount != 0
        ) return

        val replacement = listOf(
            FoodFilter.Source.Recent,
            FoodFilter.Source.YourFood,
            FoodFilter.Source.OpenFoodFacts,
            FoodFilter.Source.USDA,
            FoodFilter.Source.SwissFoodCompositionDatabase,
            FoodFilter.Source.FDDB,
        ).firstOrNull { candidate ->
            state.sources[candidate]?.let { it.shouldShowFilter && it.count > 0 } == true
        }
        if (replacement != null) {
            filter.value = FoodFilter(replacement)
            allowAutomaticSourceSwitch = false
        }
    }

    fun setProductFavorite(id: FoodId.Product, isFavorite: Boolean) {
        viewModelScope.launch { setProductFavoriteUseCase.setFavorite(id, isFavorite) }
    }

    private val results = requests.flatMapLatest { request ->
        flow {
            // Clear the previous query before delaying or waiting for any new counters.
            emit(SearchResults(request.query))
            if (!request.immediately) delay(SEARCH_DELAY_MS)
            emitAll(observeSources(request.query).map { SearchResults(request.query, it) })
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SearchResults(null))

    /** Each query owns its pagers and counters; cancelling it also cancels their caches. */
    private fun observeSources(query: String?): Flow<Map<FoodFilter.Source, FoodSourceUiState>> =
        channelFlow {
            val prefs = foodSearchPreferencesRepository.observe()
            val recentPages = foodSearchUseCase.searchRecent(query, excludedRecipeId).cachedIn(this)
            val recent = foodSearchRepository.searchRecentFoodCount(
                searchQuery(query), dateProvider.now(), excludedRecipeId,
            ).map { count ->
                FoodSourceUiState(
                    remoteEnabled = RemoteStatus.LocalOnly,
                    pages = recentPages,
                    count = count,
                    alwaysShowFilter = true,
                )
            }

            fun source(type: FoodSource.Type): Flow<FoodSourceUiState> {
                val pages = foodSearchUseCase.search(query, type, excludedRecipeId).cachedIn(this)
                return combine(
                    foodSearchRepository.searchFoodCount(searchQuery(query), type, excludedRecipeId),
                    prefs,
                ) { count, preferences ->
                    val remote = when (type) {
                        FoodSource.Type.OpenFoodFacts -> preferences.isOpenFoodFactsEnabled.toRemoteStatus()
                        FoodSource.Type.USDA -> preferences.isUsdaEnabled.toRemoteStatus()
                        else -> RemoteStatus.LocalOnly
                    }
                    FoodSourceUiState(
                        remoteEnabled = remote,
                        pages = pages,
                        count = count,
                        alwaysShowFilter = type == FoodSource.Type.User,
                    )
                }
            }

            val localAndRemote = listOf(
                FoodFilter.Source.Recent to recent,
                FoodFilter.Source.YourFood to source(FoodSource.Type.User),
                FoodFilter.Source.OpenFoodFacts to source(FoodSource.Type.OpenFoodFacts),
                FoodFilter.Source.USDA to source(FoodSource.Type.USDA),
                FoodFilter.Source.SwissFoodCompositionDatabase to source(FoodSource.Type.SwissFoodCompositionDatabase),
                FoodFilter.Source.FDDB to source(FoodSource.Type.FDDB),
            ).map { (key, state) -> state.map { key to it } }.combine { it.toMap() }
                .stateIn(this, SharingStarted.Eagerly, emptyMap())

            fun enabledSources(states: Map<FoodFilter.Source, FoodSourceUiState>, preferences: FoodSearchPreferences) =
                buildSet {
                    add(FoodSource.Type.User)
                    if (preferences.isOpenFoodFactsEnabled) add(FoodSource.Type.OpenFoodFacts)
                    if (preferences.isUsdaEnabled) add(FoodSource.Type.USDA)
                    if ((states[FoodFilter.Source.SwissFoodCompositionDatabase]?.count ?: 0) > 0) {
                        add(FoodSource.Type.SwissFoodCompositionDatabase)
                    }
                    if ((states[FoodFilter.Source.FDDB]?.count ?: 0) > 0) add(FoodSource.Type.FDDB)
                }
            val readyStates = localAndRemote.filter { it.isNotEmpty() }
            val allSources = combine(readyStates, prefs, ::enabledSources).distinctUntilChanged()
            val all = allSources.flatMapLatest { sources ->
                allSource(query, sources, this).map { sources to it }
            }
            combine(readyStates, prefs, all) { states, preferences, allState ->
                if (enabledSources(states, preferences) != allState.first) {
                    emptyMap()
                } else states + (FoodFilter.Source.All to allState.second)
            }.collect { send(it) }
        }

    private fun allSource(
        query: String?,
        sources: Set<FoodSource.Type>,
        scope: CoroutineScope,
    ): Flow<FoodSourceUiState> {
        val pages = foodSearchRepository.search(
            searchQuery(query), sources, PagingConfig(pageSize = PAGE_SIZE), excludedRecipeId,
        ).cachedIn(scope)
        return foodSearchRepository.searchFoodCount(searchQuery(query), sources, excludedRecipeId)
            .map { count ->
                FoodSourceUiState(
                    remoteEnabled = RemoteStatus.LocalOnly,
                    pages = pages,
                    count = count,
                    alwaysShowFilter = true,
                )
            }
    }

    private val searchHistory = searchHistoryRepository.observeHistory(limit = 10)
        .map { entries -> entries.map { it.query.query } }

    val uiState = combine(results, inputQuery, filter, searchHistory, historyTab) {
        results, query, filter, history, tab ->
        val pending = results.query != query || results.sources.isEmpty()
        FoodSearchUiState(
            sources = if (pending) emptyMap() else results.sources,
            filter = filter,
            recentSearches = history,
            query = query,
            isPending = pending,
            historyTab = tab,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        FoodSearchUiState(emptyMap(), FoodFilter(), emptyList(), isPending = true),
    )
}

private const val PAGE_SIZE = 30
private const val SEARCH_DELAY_MS = 300L

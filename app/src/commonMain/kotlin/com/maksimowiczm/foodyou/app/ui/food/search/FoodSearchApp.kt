package com.maksimowiczm.foodyou.app.ui.food.search

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.maksimowiczm.foodyou.app.ui.common.component.FoodListItemSkeleton
import com.maksimowiczm.foodyou.app.ui.common.component.FullScreenCameraBarcodeScanner
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.search.searchQuery
import com.maksimowiczm.foodyou.common.extension.error
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.RemoteFoodException
import com.maksimowiczm.foodyou.food.search.domain.FoodSearch
import com.valentinilk.shimmer.ShimmerBounds
import com.valentinilk.shimmer.rememberShimmer
import foodyou.app.generated.resources.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun FoodSearchApp(
    onFoodClick: (FoodSearch, Measurement) -> Unit,
    onUpdateUsdaApiKey: () -> Unit,
    onUpdateOpenFoodFactsCredentials: () -> Unit,
    modifier: Modifier = Modifier,
    excludedRecipe: FoodId.Recipe? = null,
    layout: FoodSearchLayout = FoodSearchLayout.Overlay,
    showBarcodeScannerInitially: Boolean = false,
    restoredSearchText: String = "",
    searchInitially: Boolean = false,
    transformSearch: (String?) -> String? = { it },
    snackbarHostState: SnackbarHostState? = null,
) {
    val viewModel: FoodSearchViewModel = koinViewModel { parametersOf(excludedRecipe) }
    val appState =
        rememberFoodSearchAppState(
            searchTextFieldState = rememberTextFieldState(restoredSearchText),
            showBarcodeScanner = showBarcodeScannerInitially,
        )

    FoodSearchApp(
        uiState = viewModel.uiState.collectAsStateWithLifecycle().value,
        onSearch = viewModel::search,
        onQueryChange = viewModel::changeQuery,
        onRecordSearch = viewModel::recordSearch,
        onHistoryTabChange = viewModel::changeHistoryTab,
        onResultsLoaded = viewModel::resultsLoaded,
        searchImmediatelyInitially = searchInitially || restoredSearchText.isNotBlank(),
        onSourceChange = viewModel::changeSource,
        onProductFavoriteChange = viewModel::setProductFavorite,
        onFoodClick = onFoodClick,
        onUpdateUsdaApiKey = onUpdateUsdaApiKey,
        onUpdateOpenFoodFactsCredentials = onUpdateOpenFoodFactsCredentials,
        modifier = modifier,
        appState = appState,
        layout = layout,
        transformSearch = transformSearch,
        snackbarHostState = snackbarHostState,
    )
}

enum class FoodSearchLayout {
    Overlay,
    Stacked,
}

@Composable
internal fun FoodSearchApp(
    uiState: FoodSearchUiState,
    onSearch: (String?) -> Unit,
    onSourceChange: (FoodFilter.Source) -> Unit,
    onProductFavoriteChange: (FoodId.Product, Boolean) -> Unit,
    onFoodClick: (FoodSearch, Measurement) -> Unit,
    onUpdateUsdaApiKey: () -> Unit,
    onUpdateOpenFoodFactsCredentials: () -> Unit,
    modifier: Modifier = Modifier,
    appState: FoodSearchAppState = rememberFoodSearchAppState(),
    layout: FoodSearchLayout = FoodSearchLayout.Overlay,
    transformSearch: (String?) -> String? = { it },
    onQueryChange: (String?) -> Unit = onSearch,
    onRecordSearch: (String?) -> Unit = {},
    onHistoryTabChange: (FoodSearchHistoryTab) -> Unit = {},
    onResultsLoaded: (String?, FoodFilter.Source) -> Unit = { _, _ -> },
    searchImmediatelyInitially: Boolean = true,
    snackbarHostState: SnackbarHostState? = null,
) {
    val coroutineScope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val currentTransform by rememberUpdatedState(transformSearch)
    val currentOnQueryChange by rememberUpdatedState(onQueryChange)
    val currentOnSearch by rememberUpdatedState(onSearch)
    var observedText by remember(appState) {
        mutableStateOf(appState.searchTextFieldState.text.toString())
    }
    var observedQuery by remember(appState) { mutableStateOf(uiState.query) }
    val inputText = appState.searchTextFieldState.text.toString()
    val showingHistory = inputText.isBlank()
    val pending = uiState.isPending || observedText != inputText ||
        observedQuery != uiState.query || showingHistory != uiState.showingHistory
    val source = if (showingHistory) FoodFilter.Source.Recent else uiState.filter.source

    LaunchedEffect(appState.searchTextFieldState) {
        var initial = true
        snapshotFlow { appState.searchTextFieldState.text.toString() }.collect { text ->
            val query = currentTransform(text.takeIf { it.isNotBlank() })
            if (initial && searchImmediatelyInitially && text.isNotBlank()) {
                currentOnSearch(query)
            } else {
                currentOnQueryChange(query)
            }
            observedText = text
            observedQuery = searchQuery(query).query
            initial = false
        }
    }

    fun searchImmediately(text: String?, recordHistory: Boolean = false) {
        appState.searchTextFieldState.setTextAndPlaceCursorAtEnd(text ?: "")
        val query = transformSearch(text)
        onSearch(query)
        observedText = text ?: ""
        observedQuery = searchQuery(query).query
        if (recordHistory) onRecordSearch(query)
    }
    val selectFood: (FoodSearch, Measurement) -> Unit = { food, measurement ->
        // Read the current amount synchronously, including edits that do not change the food query.
        val query = transformSearch(
            appState.searchTextFieldState.text.toString().takeIf { it.isNotBlank() }
        )
        if (!pending && searchQuery(query).query == uiState.query) {
            onRecordSearch(query)
            keyboard?.hide()
            focusManager.clearFocus()
            onFoodClick(food, measurement)
        }
    }
    val ownSnackbarHostState = remember { SnackbarHostState() }
    val favoriteSnackbarHostState = snackbarHostState ?: ownSnackbarHostState
    val addedToFavoritesMessage = stringResource(Res.string.neutral_added_to_favorites)
    val removedFromFavoritesMessage = stringResource(Res.string.neutral_removed_from_favorites)
    val toggleFavorite: (FoodSearch.Product) -> Unit = { food ->
        val isFavorite = !food.isFavorite
        onProductFavoriteChange(food.id, isFavorite)
        coroutineScope.launch {
            favoriteSnackbarHostState.currentSnackbarData?.dismiss()
            favoriteSnackbarHostState.showSnackbar(
                if (isFavorite) addedToFavoritesMessage else removedFromFavoritesMessage
            )
        }
    }
    val pages = key(uiState.query, source, pending) {
        if (pending) null else uiState.sources[source]?.collectAsLazyPagingItems()
    }
    LaunchedEffect(uiState.query, source, pages?.loadState?.refresh, uiState.sources) {
        if (!showingHistory && !pending && pages?.loadState?.refresh is LoadState.NotLoading) {
            onResultsLoaded(uiState.query, source)
        }
    }
    var previousQuery by remember(appState) { mutableStateOf(uiState.query) }
    LaunchedEffect(uiState.query) {
        if (previousQuery != uiState.query && uiState.query != null) {
            appState.listStates.state(source).scrollToItem(0)
        }
        previousQuery = uiState.query
    }

    FullScreenCameraBarcodeScanner(
        visible = appState.showBarcodeScanner,
        onBarcodeScan = {
            appState.showBarcodeScanner = false
            searchImmediately(it)
            keyboard?.hide()
            focusManager.clearFocus()
        },
        onClose = { appState.showBarcodeScanner = false },
    )

    val searchInputField = @Composable {
        FoodSearchBarInputField(
            textFieldState = appState.searchTextFieldState,
            onSearch = { text ->
                searchImmediately(text, recordHistory = true)
                keyboard?.hide()
                focusManager.clearFocus()
            },
            onBarcodeScanner = {
                keyboard?.hide()
                focusManager.clearFocus()
                appState.showBarcodeScanner = true
            },
            onClear = { searchImmediately(null) },
        )
    }

    val content: @Composable (Modifier, PaddingValues) -> Unit = { contentModifier, padding ->
        if (showingHistory && uiState.historyTab == FoodSearchHistoryTab.RecentSearches) {
            FoodSearchHistory(
                searches = uiState.recentSearches,
                onSearch = { searchImmediately(it) },
                modifier = contentModifier,
                contentPadding = padding,
            )
        } else {
            FoodSearchResults(
                pages = pages,
                listState = appState.listStates.state(source),
                source = source,
                query = if (showingHistory) null else uiState.query,
                onFavoriteToggle = toggleFavorite,
                onFoodClick = selectFood,
                modifier = contentModifier,
                contentPadding = padding,
                showingRecentFood = showingHistory,
            )
        }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets =
            if (layout == FoodSearchLayout.Stacked) WindowInsets(0)
            else ScaffoldDefaults.contentWindowInsets,
        snackbarHost = {
            if (snackbarHostState == null) SnackbarHost(ownSnackbarHostState)
        },
    ) { scaffoldPadding ->
        // Fix for searchbar issues on Android SDK 27 and below
        Box(Modifier.focusable().size(1.dp))

        val headerOuterModifier =
            Modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                .padding(top = scaffoldPadding.calculateTopPadding())

        val headerInnerModifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)

        val header: @Composable (Modifier) -> Unit = { headerModifier ->
            Surface(
                modifier = headerModifier,
                color = MaterialTheme.colorScheme.background,
            ) {
                FoodSearchHeader(
                    uiState = uiState,
                    pages = pages,
                    appState = appState,
                    showingHistory = showingHistory,
                    pending = pending,
                    onHistoryTabChange = onHistoryTabChange,
                    onSourceChange = onSourceChange,
                    onUpdateUsdaApiKey = onUpdateUsdaApiKey,
                    onUpdateOpenFoodFactsCredentials = onUpdateOpenFoodFactsCredentials,
                    searchInputField = searchInputField,
                    coroutineScope = coroutineScope,
                    modifier = headerInnerModifier,
                )
            }
        }

        when (layout) {
            FoodSearchLayout.Overlay -> {
                var topContentHeight by remember { mutableIntStateOf(0) }
                val layoutDirection = LocalLayoutDirection.current
                val topContentHeightDp = LocalDensity.current.run { topContentHeight.toDp() }
                header(
                    headerOuterModifier.zIndex(10f).onSizeChanged { topContentHeight = it.height }
                )

                content(
                    Modifier.fillMaxSize().padding(top = topContentHeightDp),
                    PaddingValues(
                        start = scaffoldPadding.calculateStartPadding(layoutDirection),
                        end = scaffoldPadding.calculateEndPadding(layoutDirection),
                        bottom = scaffoldPadding.calculateBottomPadding() + 56.dp + 32.dp,
                    ),
                )
            }

            FoodSearchLayout.Stacked ->
                Column(Modifier.fillMaxSize()) {
                    header(headerOuterModifier)
                    content(
                        Modifier.fillMaxWidth().weight(1f),
                        PaddingValues(bottom = 56.dp + 32.dp),
                    )
                }
        }
    }
}

@Composable
private fun FoodSearchHeader(
    uiState: FoodSearchUiState,
    pages: LazyPagingItems<FoodSearch>?,
    appState: FoodSearchAppState,
    showingHistory: Boolean,
    pending: Boolean,
    onHistoryTabChange: (FoodSearchHistoryTab) -> Unit,
    onSourceChange: (FoodFilter.Source) -> Unit,
    onUpdateUsdaApiKey: () -> Unit,
    onUpdateOpenFoodFactsCredentials: () -> Unit,
    searchInputField: @Composable () -> Unit,
    coroutineScope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            shape = SearchBarDefaults.inputFieldShape,
            shadowElevation = 2.dp,
        ) { searchInputField() }

        Spacer(Modifier.height(8.dp))
        if (showingHistory) {
            FoodSearchHistoryTabs(uiState.historyTab, onHistoryTabChange)
        } else if (!pending && uiState.sources.isNotEmpty()) {
            FoodSearchFilters(
                uiState = uiState,
                onSource = {
                    onSourceChange(it)

                    if (it == uiState.filter.source) {
                        val listState = appState.listStates.state(it)
                        coroutineScope.launch { listState.animateScrollToItem(0) }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        val error = if (pending) null else pages?.loadState?.error as? RemoteFoodException
        if (error != null && pages != null) {
            FoodSearchErrorCard(
                error = error,
                onRetry = pages::retry,
                onUsdaApiKey = onUpdateUsdaApiKey,
                onUpdateOpenFoodFactsCredentials = onUpdateOpenFoodFactsCredentials,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun FoodSearchResults(
    pages: LazyPagingItems<FoodSearch>?,
    listState: LazyListState,
    source: FoodFilter.Source,
    query: String?,
    onFavoriteToggle: (FoodSearch.Product) -> Unit,
    onFoodClick: (FoodSearch, Measurement) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    showingRecentFood: Boolean = false,
) {
    Box(modifier) {
        if (pages?.itemCount == 0 && pages.loadState.refresh is LoadState.NotLoading &&
            pages.loadState.append !is LoadState.Loading
        ) {
            Text(
                text = stringResource(
                    if (showingRecentFood) Res.string.neutral_no_recent_food
                    else Res.string.neutral_no_food_found
                ),
                modifier = Modifier.safeContentPadding().align(Alignment.Center),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            state = listState,
        ) {
            if (pages != null) {
                items(
                    count = pages.itemCount,
                    key = pages.itemKey { (it.id to source).toString() },
                ) { i ->
                    val food = pages[i]

                    when (food) {
                        null -> FoodSearchListItemSkeleton()
                        is FoodSearch.Product -> {
                            val measurement = food.suggestedMeasurement
                            FoodSearchListItem(
                                food = food,
                                measurement = measurement,
                                query = query,
                                onClick = { onFoodClick(food, measurement) },
                                onFavoriteToggle = { onFavoriteToggle(food) },
                            )
                        }

                        is FoodSearch.Recipe -> {
                            val measurement = food.suggestedMeasurement
                            FoodSearchListItem(
                                food = food,
                                measurement = measurement,
                                query = query,
                                onClick = { onFoodClick(food, measurement) },
                                shimmer = rememberShimmer(ShimmerBounds.View),
                            )
                        }
                    }
                }

                if (pages.loadState.append is LoadState.Loading) {
                    items(10) { FoodSearchListItemSkeleton() }
                }
            }

            if (pages == null) {
                items(10) { FoodSearchListItemSkeleton() }
            }
        }

        if (pages?.delayedLoadingState() == true) {
            ContainedLoadingIndicator(
                modifier = Modifier.align(Alignment.TopCenter).zIndex(20f)
            )
        }
    }
}

@Composable
private fun FoodSearchListItemSkeleton() {
    FoodListItemSkeleton(rememberShimmer(ShimmerBounds.View))
}

private fun ListStates.state(source: FoodFilter.Source) =
    when (source) {
        FoodFilter.Source.All -> all
        FoodFilter.Source.Recent -> recent
        FoodFilter.Source.YourFood -> yourFood
        FoodFilter.Source.OpenFoodFacts -> openFoodFacts
        FoodFilter.Source.USDA -> usda
        FoodFilter.Source.SwissFoodCompositionDatabase -> swiss
        FoodFilter.Source.FDDB -> fddb
    }

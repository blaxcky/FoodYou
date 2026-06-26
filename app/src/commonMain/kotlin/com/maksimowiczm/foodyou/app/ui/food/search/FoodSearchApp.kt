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
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
) {
    val viewModel: FoodSearchViewModel = koinViewModel { parametersOf(excludedRecipe) }
    val appState =
        rememberFoodSearchAppState(showBarcodeScanner = showBarcodeScannerInitially)

    FoodSearchApp(
        uiState = viewModel.uiState.collectAsStateWithLifecycle().value,
        onSearch = viewModel::search,
        onSourceChange = viewModel::changeSource,
        onProductFavoriteChange = viewModel::setProductFavorite,
        onFoodClick = onFoodClick,
        onUpdateUsdaApiKey = onUpdateUsdaApiKey,
        onUpdateOpenFoodFactsCredentials = onUpdateOpenFoodFactsCredentials,
        modifier = modifier,
        appState = appState,
        layout = layout,
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
) {
    val coroutineScope = rememberCoroutineScope()
    val onSearch: (String?) -> Unit =
        remember(onSearch, appState, coroutineScope) {
            { query ->
                appState.searchTextFieldState.setTextAndPlaceCursorAtEnd(query ?: "")
                onSearch(query)
                coroutineScope.launch { appState.searchBarState.animateToCollapsed() }
            }
        }

    val pages = uiState.currentSourceState?.collectAsLazyPagingItems()
    FullScreenCameraBarcodeScanner(
        visible = appState.showBarcodeScanner,
        onBarcodeScan = {
            appState.showBarcodeScanner = false
            onSearch(it)
        },
        onClose = { appState.showBarcodeScanner = false },
    )

    val searchInputField =
        @Composable {
            FoodSearchBarInputField(
                searchBarState = appState.searchBarState,
                textFieldState = appState.searchTextFieldState,
                onSearch = onSearch,
                onBarcodeScanner = { appState.showBarcodeScanner = true },
            )
        }

    FoodSearchView(
        appState = appState,
        uiState = uiState,
        onFill = { search -> appState.searchTextFieldState.setTextAndPlaceCursorAtEnd(search) },
        onSearch = onSearch,
        onSource = onSourceChange,
        inputField = searchInputField,
    )

    Scaffold(
        modifier = modifier,
        contentWindowInsets =
            if (layout == FoodSearchLayout.Stacked) WindowInsets(0)
            else ScaffoldDefaults.contentWindowInsets,
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

                FoodSearchResults(
                    pages = pages,
                    listState = appState.listStates.state(uiState.filter.source),
                    source = uiState.filter.source,
                    onProductFavoriteChange = onProductFavoriteChange,
                    onFoodClick = onFoodClick,
                    modifier = Modifier.fillMaxSize().padding(top = topContentHeightDp),
                    contentPadding =
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
                    FoodSearchResults(
                        pages = pages,
                        listState = appState.listStates.state(uiState.filter.source),
                        source = uiState.filter.source,
                        onProductFavoriteChange = onProductFavoriteChange,
                        onFoodClick = onFoodClick,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(bottom = 56.dp + 32.dp),
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
    onSourceChange: (FoodFilter.Source) -> Unit,
    onUpdateUsdaApiKey: () -> Unit,
    onUpdateOpenFoodFactsCredentials: () -> Unit,
    searchInputField: @Composable () -> Unit,
    coroutineScope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        SearchBar(
            state = appState.searchBarState,
            inputField = searchInputField,
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            colors =
                SearchBarDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                ),
            shadowElevation = 2.dp,
        )

        if (uiState.sources.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            FoodSearchFilters(
                uiState = uiState,
                onSource = {
                    onSourceChange(it)

                    if (it == uiState.filter.source) {
                        val listState = appState.listStates.state(it)
                        coroutineScope.launch { listState.animateScrollToItem(0) }
                    }
                },
                modifier = Modifier.height(32.dp + 8.dp + 32.dp).fillMaxWidth(),
            )
        }

        val error = pages?.loadState?.error as? RemoteFoodException
        if (error != null) {
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
    onProductFavoriteChange: (FoodId.Product, Boolean) -> Unit,
    onFoodClick: (FoodSearch, Measurement) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    Box(modifier) {
        if (pages?.itemCount == 0 && pages.loadState.append !is LoadState.Loading) {
            Text(
                text = stringResource(Res.string.neutral_no_food_found),
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
                                onClick = { onFoodClick(food, measurement) },
                                onFavoriteClick = {
                                    onProductFavoriteChange(food.id, !food.isFavorite)
                                },
                            )
                        }

                        is FoodSearch.Recipe -> {
                            val measurement = food.suggestedMeasurement
                            FoodSearchListItem(
                                food = food,
                                measurement = measurement,
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

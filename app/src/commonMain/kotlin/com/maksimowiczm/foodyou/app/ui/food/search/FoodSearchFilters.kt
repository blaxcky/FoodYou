package com.maksimowiczm.foodyou.app.ui.food.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun FoodSearchFilters(
    uiState: FoodSearchUiState,
    onSource: (FoodFilter.Source) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
) {
    val filters =
        uiState.sources
            .filter { (source, state) -> source == uiState.filter.source || state.shouldShowFilter }
            .toList()
            // "All" is the default filter, so it leads the row.
            .sortedBy { (source, _) -> source != FoodFilter.Source.All }
    // The row is recreated for every search, so start at the selected filter to keep it visible.
    val listState =
        rememberLazyListState(
            initialFirstVisibleItemIndex =
                filters.indexOfFirst { (source, _) -> source == uiState.filter.source }
                    .coerceAtLeast(0)
        )

    LazyRow(
        modifier = modifier,
        state = listState,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(filters, key = { (source, _) -> source }) { (source, state) ->
            val pages = state.collectAsLazyPagingItems()
            val isLoading = pages.delayedLoadingState()
            val hasError = source != FoodFilter.Source.All && pages.loadState.hasError
            val selected = uiState.filter.source == source

            val colors =
                if (hasError) {
                    FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        labelColor = MaterialTheme.colorScheme.onErrorContainer,
                        iconColor = MaterialTheme.colorScheme.onErrorContainer,
                        selectedContainerColor = MaterialTheme.colorScheme.error,
                        selectedLabelColor = MaterialTheme.colorScheme.onError,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onError,
                        selectedTrailingIconColor = MaterialTheme.colorScheme.onError,
                    )
                } else {
                    FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                }

            val border =
                FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected,
                    borderColor =
                        if (hasError || selected) {
                            Color.Transparent
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                )

            FilterChip(
                selected = selected,
                onClick = { onSource(source) },
                label = { Text(source.stringResource()) },
                modifier = Modifier.animateItem(),
                // The FDDB icon is its name as text, which would repeat the label.
                leadingIcon =
                    if (source == FoodFilter.Source.FDDB) null
                    else {
                        { source.Icon(Modifier.size(FilterChipDefaults.IconSize)) }
                    },
                trailingIcon = {
                    if (hasError) {
                        Icon(
                            imageVector = Icons.Outlined.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    } else {
                        if (state.remoteEnabled != RemoteStatus.LocalOnly && isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(
                                text = state.count.toString(),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                },
                colors = colors,
                border = border,
            )
        }
    }
}

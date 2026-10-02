package com.maksimowiczm.foodyou.app.ui.food.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun FoodSearchHistoryTabs(
    selected: FoodSearchHistoryTab,
    onSelect: (FoodSearchHistoryTab) -> Unit,
) {
    PrimaryTabRow(selectedTabIndex = selected.ordinal) {
        FoodSearchHistoryTab.entries.forEach { tab ->
            Tab(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                text = {
                    Text(stringResource(when (tab) {
                        FoodSearchHistoryTab.RecentFood -> Res.string.headline_recently_logged
                        FoodSearchHistoryTab.RecentSearches -> Res.string.headline_recent_searches
                    }))
                },
            )
        }
    }
}

@Composable
internal fun FoodSearchHistory(
    searches: List<String>,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    Box(modifier) {
        if (searches.isEmpty()) {
            Text(
                text = stringResource(Res.string.neutral_no_recent_searches),
                modifier = Modifier.align(Alignment.Center).padding(16.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
            items(searches, key = { it }) { search ->
                ListItem(
                    modifier = Modifier.clickable { onSearch(search) },
                    headlineContent = { Text(search) },
                    leadingContent = {
                        Icon(imageVector = Icons.Outlined.History, contentDescription = null)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
}

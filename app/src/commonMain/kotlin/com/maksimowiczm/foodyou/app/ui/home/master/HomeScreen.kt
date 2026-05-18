package com.maksimowiczm.foodyou.app.ui.home.master

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.home.calendar.CalendarCard
import com.maksimowiczm.foodyou.app.ui.home.activity.ActivitiesCard
import com.maksimowiczm.foodyou.app.ui.home.goals.GoalsCard
import com.maksimowiczm.foodyou.app.ui.home.meals.card.mealsCards
import com.maksimowiczm.foodyou.app.ui.home.meals.card.rememberMealsCardsState
import com.maksimowiczm.foodyou.app.ui.home.poll.PollsCard
import com.maksimowiczm.foodyou.app.ui.home.shared.rememberHomeState
import com.maksimowiczm.foodyou.settings.domain.entity.HomeCard
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeScreen(
    onSettings: () -> Unit,
    onPendingProducts: () -> Unit,
    onTitle: () -> Unit,
    onMealCardLongClick: (mealId: Long) -> Unit,
    onMealCardAddClick: (epochDay: Long, mealId: Long) -> Unit,
    onMealCardQuickAddClick: (epochDay: Long, mealId: Long) -> Unit,
    onGoalsCardLongClick: () -> Unit,
    onGoalsCardClick: (epochDay: Long) -> Unit,
    onActivityCardLongClick: () -> Unit,
    onAddActivityClick: (epochDay: Long) -> Unit,
    onEditActivityClick: (id: Long) -> Unit,
    onEditDiaryEntryClick: (foodEntryId: Long?, manualEntryId: Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: HomeViewModel = koinViewModel()
    val order by viewModel.homeOrder.collectAsStateWithLifecycle()
    val activitySyncState by viewModel.activitySyncState.collectAsStateWithLifecycle()
    val homeState = rememberHomeState()
    val mealsCardsState =
        rememberMealsCardsState(
            homeState = homeState,
            onAdd = onMealCardAddClick,
            onQuickAdd = onMealCardQuickAddClick,
            onEditEntry = onEditDiaryEntryClick,
            onLongClick = onMealCardLongClick,
        )

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showSettingsMenu by remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.app_name),
                        modifier =
                            Modifier.clickable(
                                interactionSource = null,
                                indication = null,
                                onClick = onTitle,
                            ),
                    )
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.syncActivities(homeState.selectedDate) },
                        enabled = !activitySyncState.isSyncing,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Sync,
                            contentDescription = "Health Connect synchronisieren",
                            tint =
                                when {
                                    activitySyncState.isSyncing -> MaterialTheme.colorScheme.outline
                                    activitySyncState.isStale -> MaterialTheme.colorScheme.error
                                    else -> Color(0xFF2E7D32)
                                },
                        )
                    }
                    IconButton(onClick = { showSettingsMenu = true }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(Res.string.action_go_to_settings),
                        )
                    }
                    DropdownMenu(
                        expanded = showSettingsMenu,
                        onDismissRequest = { showSettingsMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.headline_pending_products)) },
                            onClick = {
                                showSettingsMenu = false
                                onPendingProducts()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.headline_settings)) },
                            onClick = {
                                showSettingsMenu = false
                                onSettings()
                            },
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = paddingValues,
        ) {
            item(key = "polls", contentType = "polls") {
                PollsCard(modifier = Modifier.padding(horizontal = 8.dp).padding(bottom = 8.dp))
            }

            order.forEach { homeCard ->
                when (homeCard) {
                    HomeCard.Calendar ->
                        item(key = HomeCard.Calendar, contentType = HomeCard.Calendar) {
                            CalendarCard(
                                homeState = homeState,
                                modifier =
                                    Modifier.padding(horizontal = 8.dp).padding(bottom = 8.dp),
                            )
                        }

                    HomeCard.Goals ->
                        item(key = HomeCard.Goals, contentType = HomeCard.Goals) {
                            GoalsCard(
                                homeState = homeState,
                                onClick = onGoalsCardClick,
                                onLongClick = onGoalsCardLongClick,
                                modifier =
                                    Modifier.padding(horizontal = 8.dp).padding(bottom = 8.dp),
                            )
                        }

                    HomeCard.Meals ->
                        mealsCards(
                            state = mealsCardsState,
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.padding(bottom = 8.dp),
                        )

                    HomeCard.Activities ->
                        item(key = HomeCard.Activities, contentType = HomeCard.Activities) {
                            ActivitiesCard(
                                homeState = homeState,
                                onAdd = onAddActivityClick,
                                onEdit = onEditActivityClick,
                                onLongClick = onActivityCardLongClick,
                                modifier =
                                    Modifier.padding(horizontal = 8.dp).padding(bottom = 8.dp),
                            )
                        }
                }
            }
        }
    }
}

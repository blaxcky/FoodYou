package com.maksimowiczm.foodyou.app.ui.home.master

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.home.calendar.CalendarCard
import com.maksimowiczm.foodyou.app.ui.home.activity.ActivitiesCard
import com.maksimowiczm.foodyou.app.ui.home.goals.GoalOverviewCard
import com.maksimowiczm.foodyou.app.ui.home.goals.GoalsCard
import com.maksimowiczm.foodyou.app.ui.home.goals.WeeklyGoalsCard
import com.maksimowiczm.foodyou.app.ui.home.meals.card.mealsCards
import com.maksimowiczm.foodyou.app.ui.home.meals.card.rememberMealsCardsState
import com.maksimowiczm.foodyou.app.ui.home.poll.PollsCard
import com.maksimowiczm.foodyou.app.ui.home.shared.rememberHomeState
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.settings.domain.entity.HomeCard
import foodyou.app.generated.resources.*
import kotlinx.coroutines.delay
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
    onEditFoodClick: (FoodId.Product) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: HomeViewModel = koinViewModel()
    val order by viewModel.homeOrder.collectAsStateWithLifecycle()
    val activitySyncState by viewModel.activitySyncState.collectAsStateWithLifecycle()
    val fddbSyncState by viewModel.fddbSyncState.collectAsStateWithLifecycle()
    val homeSyncState by viewModel.homeSyncState.collectAsStateWithLifecycle()
    val homeState = rememberHomeState()
    val burnedEnergyDelta = activitySyncState.burnedEnergySyncDeltas[homeState.selectedDate]
    val mealsCardsState =
        rememberMealsCardsState(
            homeState = homeState,
            onAdd = onMealCardAddClick,
            onQuickAdd = onMealCardQuickAddClick,
            onEditEntry = onEditDiaryEntryClick,
            onEditFood = onEditFoodClick,
            onLongClick = onMealCardLongClick,
        )

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showSettingsMenu by remember { mutableStateOf(false) }
    var showFddbLoginDialog by remember { mutableStateOf(false) }
    var pullRefreshActive by remember { mutableStateOf(false) }
    var pullRefreshSyncStarted by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()

    fun showFddbLoginDialogIfNeeded() {
        if (
            homeSyncState.fddbDiaryEnabled &&
                homeSyncState.fddbSyncState is HomeFddbSyncState.MissingCredentials
        ) {
            showFddbLoginDialog = true
        }
    }

    fun syncConfiguredHome() {
        showFddbLoginDialogIfNeeded()
        viewModel.syncConfigured(homeState.selectedDate)
    }

    val onFddbSyncClick = {
        if (fddbSyncState is HomeFddbSyncState.MissingCredentials) {
            showFddbLoginDialog = true
        } else {
            viewModel.syncFddbDiary(homeState.selectedDate)
        }
    }
    val onHomeSyncClick = { syncConfiguredHome() }
    val onPullRefresh = {
        if (!homeSyncState.isSyncing) {
            pullRefreshActive = true
            pullRefreshSyncStarted = false
            syncConfiguredHome()
        }
    }

    LaunchedEffect(pullRefreshActive, homeSyncState.isSyncing) {
        when {
            !pullRefreshActive -> pullRefreshSyncStarted = false
            homeSyncState.isSyncing -> pullRefreshSyncStarted = true
            pullRefreshSyncStarted -> {
                pullRefreshActive = false
                pullRefreshSyncStarted = false
            }
        }
    }

    LaunchedEffect(pullRefreshActive) {
        if (pullRefreshActive) {
            delay(PULL_REFRESH_NO_SYNC_FALLBACK_MILLIS)
            if (!homeSyncState.isSyncing && !pullRefreshSyncStarted) {
                pullRefreshActive = false
            }
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
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
                    HomeSyncButton(state = homeSyncState, onClick = onHomeSyncClick)
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
        if (showFddbLoginDialog) {
            FddbLoginDialog(onDismissRequest = { showFddbLoginDialog = false })
        }
        PullToRefreshBox(
            isRefreshing = pullRefreshActive,
            onRefresh = onPullRefresh,
            modifier = Modifier.fillMaxSize(),
            state = pullToRefreshState,
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullToRefreshState,
                    isRefreshing = pullRefreshActive,
                    modifier =
                        Modifier.align(Alignment.TopCenter)
                            .padding(top = paddingValues.calculateTopPadding()),
                )
            },
        ) {
            LazyColumn(
                modifier =
                    Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
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
                                    burnedEnergyDelta = burnedEnergyDelta,
                                    onClick = {},
                                    onLongClick = onGoalsCardLongClick,
                                    modifier =
                                        Modifier.padding(horizontal = 8.dp).padding(bottom = 8.dp),
                                )
                            }

                        HomeCard.Meals -> {
                            mealsCards(
                                state = mealsCardsState,
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                            item(key = "fddb-sync", contentType = "fddb-sync") {
                                FddbSyncSection(
                                    state = fddbSyncState,
                                    onClick = onFddbSyncClick,
                                    modifier =
                                        Modifier.padding(horizontal = 8.dp).padding(bottom = 8.dp),
                                )
                            }
                        }

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

                item(key = "weekly-goals", contentType = "weekly-goals") {
                    WeeklyGoalsCard(
                        homeState = homeState,
                        modifier = Modifier.padding(horizontal = 8.dp).padding(bottom = 8.dp),
                    )
                }

                item(key = "goal-overview", contentType = "goal-overview") {
                    GoalOverviewCard(
                        homeState = homeState,
                        onClick = {},
                        onLongClick = onGoalsCardLongClick,
                        modifier = Modifier.padding(horizontal = 8.dp).padding(bottom = 8.dp),
                    )
                }
            }
        }
    }
}

private const val PULL_REFRESH_NO_SYNC_FALLBACK_MILLIS = 1_200L

@Composable
private fun FddbSyncSection(state: HomeFddbSyncState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FddbSyncButton(state = state, onClick = onClick)
            FddbSyncStatusText(state = state, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun FddbSyncStatusText(state: HomeFddbSyncState, modifier: Modifier = Modifier) {
    val text =
        when (state) {
            is HomeFddbSyncState.MissingCredentials ->
                stringResource(Res.string.neutral_fddb_sync_not_configured)
            is HomeFddbSyncState.Syncing -> stringResource(Res.string.neutral_fddb_sync_running)
            is HomeFddbSyncState.Failed -> stringResource(Res.string.neutral_fddb_sync_failed)
            is HomeFddbSyncState.Idle ->
                state.lastStatus?.let {
                    stringResource(
                        Res.string.neutral_fddb_import_summary,
                        it.imported,
                        it.skipped,
                        it.failed,
                    )
                } ?: stringResource(Res.string.action_sync_fddb_diary)
        }

    Text(text = text, style = MaterialTheme.typography.bodySmall, modifier = modifier)
}

@Composable
private fun FddbSyncButton(state: HomeFddbSyncState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val isSyncing = state is HomeFddbSyncState.Syncing
    val isError = state is HomeFddbSyncState.Failed || state is HomeFddbSyncState.MissingCredentials
    val transition = rememberInfiniteTransition(label = "fddb-sync")
    val rotation by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = if (isSyncing) 360f else 0f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 900, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "fddb-sync-rotation",
        )

    FilledTonalButton(onClick = onClick, enabled = !isSyncing, modifier = modifier) {
        Icon(
            imageVector = Icons.Filled.CloudSync,
            contentDescription = stringResource(Res.string.action_sync_fddb_diary),
            tint = if (isError) colors.error else colors.primary,
            modifier = Modifier.graphicsLayer { rotationZ = rotation },
        )
    }
}

@Composable
private fun HomeSyncButton(state: HomeSyncState, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val activityState = state.activitySyncState
    val hasFddbFailure = state.hasFddbFailure
    val iconColor =
        when {
            state.isSyncing -> colors.primary
            hasFddbFailure || (state.healthConnectEnabled && activityState.isStale) -> colors.error
            !state.healthConnectEnabled && !state.fddbDiaryEnabled -> colors.onSurfaceVariant
            else -> Color(0xFF1B7F3A)
        }
    val backgroundColor =
        when {
            state.isSyncing -> colors.primaryContainer.copy(alpha = 0.55f)
            hasFddbFailure || (state.healthConnectEnabled && activityState.isStale) ->
                colors.errorContainer.copy(alpha = 0.95f)
            !state.healthConnectEnabled && !state.fddbDiaryEnabled -> colors.surfaceContainerHighest
            else -> Color(0xFFDDEFE3)
        }
    val badgeColor =
        if (hasFddbFailure || (state.healthConnectEnabled && activityState.isStale)) {
            colors.error
        } else {
            Color(0xFF1B7F3A)
        }
    val transition = rememberInfiniteTransition(label = "home-sync")
    val rotation by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = if (state.isSyncing) 360f else 0f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 900, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "health-connect-sync-rotation",
        )

    Box(contentAlignment = Alignment.Center) {
        IconButton(
            onClick = onClick,
            enabled = !state.isSyncing,
            modifier = Modifier.clip(CircleShape).background(backgroundColor),
        ) {
            Icon(
                imageVector = Icons.Filled.Sync,
                contentDescription = stringResource(Res.string.action_sync_home),
                tint = iconColor,
                modifier = Modifier.graphicsLayer { rotationZ = rotation },
            )
        }

        if (!state.isSyncing) {
            Box(
                modifier =
                    Modifier.align(Alignment.TopEnd)
                        .offset(x = (-5).dp, y = 5.dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(badgeColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector =
                        if (hasFddbFailure || (state.healthConnectEnabled && activityState.isStale)) {
                            Icons.Outlined.Warning
                        } else {
                            Icons.Outlined.Check
                        },
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(11.dp),
                )
            }
        }
    }
}

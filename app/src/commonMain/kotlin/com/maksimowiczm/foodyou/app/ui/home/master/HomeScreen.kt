package com.maksimowiczm.foodyou.app.ui.home.master

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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

private val DefaultHomeCardSpacing = 16.dp
private val RelatedHomeSectionSpacing = 24.dp

@Composable
fun HomeScreen(
    onSettings: () -> Unit,
    onPendingProducts: () -> Unit,
    onFoodSnap: () -> Unit,
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
    onWeightReportClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: HomeViewModel = koinViewModel()
    val order by viewModel.homeOrder.collectAsStateWithLifecycle()
    val activitySyncState by viewModel.activitySyncState.collectAsStateWithLifecycle()
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
    var showGoalOverviewInGoalSlot by rememberSaveable { mutableStateOf(false) }
    var showDeleteSelectedEntriesDialog by rememberSaveable { mutableStateOf(false) }
    var showMoveSelectedEntriesSheet by rememberSaveable { mutableStateOf(false) }
    var pullRefreshActive by remember { mutableStateOf(false) }
    var pullRefreshSyncStarted by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()
    val goalBottomSpacing =
        if (!showGoalOverviewInGoalSlot && order.hasAdjacentCards(HomeCard.Goals, HomeCard.Meals)) {
            RelatedHomeSectionSpacing
        } else {
            DefaultHomeCardSpacing
        }
    val mealsBottomSpacing =
        if (order.hasAdjacentCards(HomeCard.Meals, HomeCard.Activities)) {
            RelatedHomeSectionSpacing
        } else {
            DefaultHomeCardSpacing
        }

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

    BackHandler(enabled = mealsCardsState.isSelectionMode) { mealsCardsState.onClearSelection() }

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
                            text = { Text("FoodSnap") },
                            onClick = {
                                showSettingsMenu = false
                                onFoodSnap()
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
        bottomBar = {
            if (mealsCardsState.isSelectionMode) {
                SelectedMealEntriesBottomBar(
                    selectedCount = mealsCardsState.selectedEntries.size,
                    onDelete = { showDeleteSelectedEntriesDialog = true },
                    onMove = { showMoveSelectedEntriesSheet = true },
                )
            }
        },
    ) { paddingValues ->
        if (showFddbLoginDialog) {
            FddbLoginDialog(onDismissRequest = { showFddbLoginDialog = false })
        }
        if (showDeleteSelectedEntriesDialog) {
            DeleteSelectedMealEntriesDialog(
                selectedCount = mealsCardsState.selectedEntries.size,
                onDismissRequest = { showDeleteSelectedEntriesDialog = false },
                onConfirm = {
                    mealsCardsState.onDeleteSelectedEntries()
                    showDeleteSelectedEntriesDialog = false
                },
            )
        }
        if (showMoveSelectedEntriesSheet) {
            MoveSelectedMealEntriesSheet(
                meals = mealsCardsState.meals.orEmpty(),
                onDismissRequest = { showMoveSelectedEntriesSheet = false },
                onMealClick = { mealId ->
                    mealsCardsState.onMoveSelectedEntries(mealId)
                    showMoveSelectedEntriesSheet = false
                },
            )
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
                    PollsCard(
                        modifier =
                            Modifier.padding(horizontal = 8.dp)
                                .padding(bottom = DefaultHomeCardSpacing)
                    )
                }

                order.forEach { homeCard ->
                    when (homeCard) {
                        HomeCard.Calendar ->
                            item(key = HomeCard.Calendar, contentType = HomeCard.Calendar) {
                                CalendarCard(
                                    homeState = homeState,
                                    modifier =
                                        Modifier.padding(horizontal = 8.dp)
                                            .padding(bottom = DefaultHomeCardSpacing),
                                )
                            }

                        HomeCard.Goals ->
                            item(
                                key = HomeCard.Goals,
                                contentType =
                                    if (showGoalOverviewInGoalSlot) {
                                        "goal-overview"
                                    } else {
                                        HomeCard.Goals
                                    },
                            ) {
                                if (showGoalOverviewInGoalSlot) {
                                    GoalOverviewCard(
                                        homeState = homeState,
                                        onClick = {},
                                        onLongClick = onGoalsCardLongClick,
                                        onDoubleClick = { showGoalOverviewInGoalSlot = false },
                                        modifier =
                                            Modifier.padding(horizontal = 8.dp)
                                                .padding(bottom = goalBottomSpacing),
                                    )
                                } else {
                                    GoalsCard(
                                        homeState = homeState,
                                        burnedEnergyDelta = burnedEnergyDelta,
                                        onClick = {},
                                        onLongClick = onGoalsCardLongClick,
                                        onDoubleClick = { showGoalOverviewInGoalSlot = true },
                                        modifier =
                                            Modifier.padding(horizontal = 8.dp)
                                                .padding(bottom = goalBottomSpacing),
                                    )
                                }
                            }

                        HomeCard.Meals -> {
                            if (!showGoalOverviewInGoalSlot) {
                                mealsCards(
                                    state = mealsCardsState,
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    bottomSpacing = mealsBottomSpacing,
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
                                        Modifier.padding(horizontal = 8.dp)
                                            .padding(bottom = DefaultHomeCardSpacing),
                                )
                            }
                    }
                }

                if (!showGoalOverviewInGoalSlot) {
                    item(key = "weekly-goals", contentType = "weekly-goals") {
                        WeeklyGoalsCard(
                            homeState = homeState,
                            onWeightClick = onWeightReportClick,
                            modifier =
                                Modifier.padding(horizontal = 8.dp)
                                    .padding(bottom = DefaultHomeCardSpacing),
                        )
                    }
                }

            }
        }
    }
}

private fun List<HomeCard>.hasAdjacentCards(first: HomeCard, second: HomeCard): Boolean =
    zipWithNext().any { (current, next) -> current == first && next == second }

private const val PULL_REFRESH_NO_SYNC_FALLBACK_MILLIS = 1_200L

@Composable
private fun SelectedMealEntriesBottomBar(
    selectedCount: Int,
    onDelete: () -> Unit,
    onMove: () -> Unit,
) {
    BottomAppBar {
        Text(
            text = stringResource(Res.string.label_selected_entries_count, selectedCount),
            modifier = Modifier.weight(1f).padding(start = 16.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        TextButton(onClick = onMove) {
            Icon(imageVector = Icons.Filled.SwapHoriz, contentDescription = null)
            Text(
                text = stringResource(Res.string.action_move_selected_entries),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        TextButton(
            onClick = onDelete,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Icon(imageVector = Icons.Filled.Delete, contentDescription = null)
            Text(
                text = stringResource(Res.string.action_delete),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun DeleteSelectedMealEntriesDialog(
    selectedCount: Int,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors =
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(Res.string.action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
        title = { Text(stringResource(Res.string.headline_delete_selected_entries)) },
        text = {
            Text(stringResource(Res.string.description_delete_selected_entries, selectedCount))
        },
    )
}

@Composable
private fun MoveSelectedMealEntriesSheet(
    meals: List<com.maksimowiczm.foodyou.app.ui.home.meals.card.MealModel>,
    onDismissRequest: () -> Unit,
    onMealClick: (mealId: Long) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(onDismissRequest = onDismissRequest, sheetState = sheetState) {
        Text(
            text = stringResource(Res.string.headline_select_target_meal),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            style = MaterialTheme.typography.titleLarge,
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            items(count = meals.size, key = { meals[it].id }) { index ->
                val meal = meals[index]
                ListItem(
                    headlineContent = { Text(meal.name) },
                    modifier = Modifier.clickable { onMealClick(meal.id) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                if (index != meals.lastIndex) {
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                }
            }
        }
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

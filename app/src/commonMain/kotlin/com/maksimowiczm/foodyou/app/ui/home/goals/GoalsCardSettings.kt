package com.maksimowiczm.foodyou.app.ui.home.goals

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.app.widget.updateCalorieWidgetValues
import com.maksimowiczm.foodyou.settings.domain.entity.DEFAULT_GOAL_GAUGE_MARKER_GAP_DEGREES
import com.maksimowiczm.foodyou.settings.domain.entity.GOAL_GAUGE_MARKER_GAP_DEGREES_RANGE
import com.maksimowiczm.foodyou.settings.domain.entity.GOAL_GAUGE_MARKER_GAP_DEGREES_STEP
import com.maksimowiczm.foodyou.settings.domain.entity.GOAL_GAUGE_SIZE_PERCENT_RANGE
import com.maksimowiczm.foodyou.settings.domain.entity.GOAL_GAUGE_SIZE_PERCENT_STEP
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
import com.maksimowiczm.foodyou.settings.domain.entity.WeeklyDetailsStyle
import foodyou.app.generated.resources.*
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.qualifier.named

@Composable
internal fun GoalsCardSettings(
    onBack: () -> Unit,
    homeViewModelStoreOwner: ViewModelStoreOwner? = null,
    modifier: Modifier = Modifier,
) {
    val repository: UserPreferencesRepository<Settings> =
        koinInject(named(Settings::class.qualifiedName!!))
    val settings by repository.observe().collectAsStateWithLifecycle(null)
    val scope = rememberCoroutineScope()
    // The preview shows the day selected on the home screen with its real, possibly long values,
    // so other days can be tried there. Without the home screen it shows today.
    val viewModel: GoalsViewModel =
        if (homeViewModelStoreOwner != null) {
            koinViewModel(viewModelStoreOwner = homeViewModelStoreOwner)
        } else {
            val ownViewModel: GoalsViewModel = koinViewModel()
            val dateProvider: DateProvider = koinInject()
            LaunchedEffect(ownViewModel) { ownViewModel.setDate(dateProvider.now().date) }
            ownViewModel
        }
    val selectedDay by viewModel.model.collectAsStateWithLifecycle()
    settings?.let { current ->
        GoalsCardSettingsContent(
            onBack = onBack,
            preview = selectedDay,
            goalCardModeSwitchingEnabled = current.goalCardModeSwitchingEnabled,
            supplementalGoalsEnabled = current.supplementalGoalsEnabled,
            weeklyDetailsStyle = current.weeklyDetailsStyle,
            weeklyChartEnabled = current.weeklyChartEnabled,
            gaugeSizePercent = current.goalGaugeSizePercent,
            onModeSwitchingChange = { enabled ->
                scope.launch { repository.update { copy(goalCardModeSwitchingEnabled = enabled) } }
            },
            onSupplementalGoalsChange = { enabled ->
                scope.launch { repository.update { copy(supplementalGoalsEnabled = enabled) } }
            },
            onWeeklyDetailsStyleChange = { style ->
                scope.launch { repository.update { copy(weeklyDetailsStyle = style) } }
            },
            onWeeklyChartChange = { enabled ->
                scope.launch { repository.update { copy(weeklyChartEnabled = enabled) } }
            },
            onGaugeSizeChange = { percent ->
                scope.launch { repository.update { copy(goalGaugeSizePercent = percent) } }
            },
            gaugeMarkerGapDegrees = current.goalGaugeMarkerGapDegrees,
            onGaugeMarkerGapChange = { degrees ->
                scope.launch { repository.update { copy(goalGaugeMarkerGapDegrees = degrees) } }
            },
            widgetGaugeMarkerGapDegrees = current.widgetGaugeMarkerGapDegrees,
            onWidgetGaugeMarkerGapChange = { degrees ->
                scope.launch {
                    repository.update { copy(widgetGaugeMarkerGapDegrees = degrees) }
                    updateCalorieWidgetValues()
                }
            },
            modifier = modifier,
        )
    }
}

@Composable
internal fun GoalsCardSettingsContent(
    onBack: () -> Unit,
    preview: DaySummaryModel?,
    goalCardModeSwitchingEnabled: Boolean,
    supplementalGoalsEnabled: Boolean,
    weeklyDetailsStyle: WeeklyDetailsStyle,
    weeklyChartEnabled: Boolean,
    gaugeSizePercent: Int,
    onModeSwitchingChange: (Boolean) -> Unit,
    onSupplementalGoalsChange: (Boolean) -> Unit,
    onWeeklyDetailsStyleChange: (WeeklyDetailsStyle) -> Unit,
    onWeeklyChartChange: (Boolean) -> Unit,
    onGaugeSizeChange: (Int) -> Unit,
    gaugeMarkerGapDegrees: Int = DEFAULT_GOAL_GAUGE_MARKER_GAP_DEGREES,
    onGaugeMarkerGapChange: (Int) -> Unit = {},
    widgetGaugeMarkerGapDegrees: Int = DEFAULT_GOAL_GAUGE_MARKER_GAP_DEGREES,
    onWidgetGaugeMarkerGapChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // The preview switches modes on its own, without changing the home card's mode.
    var previewMode by remember { mutableStateOf<GoalDisplayMode?>(null) }
    // The slider's value while dragging, so the preview follows it before it is saved.
    var draggedGaugeSizePercent by remember { mutableStateOf<Int?>(null) }
    val previewGaugeSizePercent = draggedGaugeSizePercent ?: gaugeSizePercent
    var draggedGaugeMarkerGapDegrees by remember { mutableStateOf<Int?>(null) }
    val previewGaugeMarkerGapDegrees = draggedGaugeMarkerGapDegrees ?: gaugeMarkerGapDegrees
    var draggedWidgetGaugeMarkerGapDegrees by remember { mutableStateOf<Int?>(null) }
    val shownWidgetGaugeMarkerGapDegrees =
        draggedWidgetGaugeMarkerGapDegrees ?: widgetGaugeMarkerGapDegrees
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier,
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(Res.string.headline_goals_card)) },
                navigationIcon = { ArrowBackIconButton(onBack) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = paddingValues,
        ) {
            stickyHeader {
                if (preview == null) {
                    GoalsCardSkeleton(
                        onClick = {},
                        onLongClick = {},
                        gaugeSizePercent = previewGaugeSizePercent,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    GoalsCard(
                        energy = preview.energy,
                        burnedEnergy = preview.burnedEnergy,
                        netEnergy = preview.netEnergy,
                        energyGoal = preview.energyGoal,
                        showEnergyGoalValue = preview.showEnergyGoalValue,
                        goalCardModeSwitchingEnabled = goalCardModeSwitchingEnabled,
                        supplementalGoalsEnabled = supplementalGoalsEnabled,
                        goalDisplayMode = previewMode ?: preview.goalDisplayMode,
                        onSelectGoalDisplayMode = { previewMode = it },
                        goalDisplaySummaries = preview.goalDisplaySummaries,
                        dietGoalDisplayModeEnabled = preview.dietGoalDisplayModeEnabled,
                        proteins = preview.proteins,
                        proteinsGoal = preview.proteinsGoal,
                        carbohydrates = preview.carbohydrates,
                        carbohydratesGoal = preview.carbohydratesGoal,
                        fats = preview.fats,
                        fatsGoal = preview.fatsGoal,
                        todayRemainingEnergy = preview.todayRemainingEnergy,
                        onClick = {},
                        onLongClick = {},
                        gaugeSizePercent = previewGaugeSizePercent,
                        gaugeMarkerGapDegrees = previewGaugeMarkerGapDegrees,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            item { HorizontalDivider() }
            item {
                GaugeSliderSetting(
                    title = stringResource(Res.string.goal_gauge_size_title),
                    description = stringResource(Res.string.goal_gauge_size_description),
                    value = previewGaugeSizePercent,
                    valueText = "$previewGaugeSizePercent %",
                    range = GOAL_GAUGE_SIZE_PERCENT_RANGE,
                    step = GOAL_GAUGE_SIZE_PERCENT_STEP,
                    onDrag = { draggedGaugeSizePercent = it },
                    onDragFinished = {
                        draggedGaugeSizePercent?.let(onGaugeSizeChange)
                        draggedGaugeSizePercent = null
                    },
                )
            }
            item {
                GaugeSliderSetting(
                    title = stringResource(Res.string.goal_gauge_marker_gap_title),
                    description = stringResource(Res.string.goal_gauge_marker_gap_description),
                    value = previewGaugeMarkerGapDegrees,
                    valueText = "$previewGaugeMarkerGapDegrees°",
                    range = GOAL_GAUGE_MARKER_GAP_DEGREES_RANGE,
                    step = GOAL_GAUGE_MARKER_GAP_DEGREES_STEP,
                    onDrag = { draggedGaugeMarkerGapDegrees = it },
                    onDragFinished = {
                        draggedGaugeMarkerGapDegrees?.let(onGaugeMarkerGapChange)
                        draggedGaugeMarkerGapDegrees = null
                    },
                )
            }
            item {
                GaugeSliderSetting(
                    title = stringResource(Res.string.widget_gauge_marker_gap_title),
                    description = stringResource(Res.string.widget_gauge_marker_gap_description),
                    value = shownWidgetGaugeMarkerGapDegrees,
                    valueText = "$shownWidgetGaugeMarkerGapDegrees°",
                    range = GOAL_GAUGE_MARKER_GAP_DEGREES_RANGE,
                    step = GOAL_GAUGE_MARKER_GAP_DEGREES_STEP,
                    onDrag = { draggedWidgetGaugeMarkerGapDegrees = it },
                    onDragFinished = {
                        draggedWidgetGaugeMarkerGapDegrees?.let(onWidgetGaugeMarkerGapChange)
                        draggedWidgetGaugeMarkerGapDegrees = null
                    },
                )
            }
            item {
                GoalCardSettingSwitch(
                    title = stringResource(Res.string.goal_card_mode_switching_title),
                    description = stringResource(Res.string.goal_card_mode_switching_description),
                    checked = goalCardModeSwitchingEnabled,
                    onCheckedChange = onModeSwitchingChange,
                )
            }
            item {
                GoalCardSettingSwitch(
                    title = stringResource(Res.string.supplemental_goals_title),
                    description = stringResource(Res.string.supplemental_goals_description),
                    checked = supplementalGoalsEnabled,
                    onCheckedChange = onSupplementalGoalsChange,
                )
            }
            item {
                WeeklyDetailsStyleSetting(
                    style = weeklyDetailsStyle,
                    onChange = onWeeklyDetailsStyleChange,
                )
            }
            item {
                GoalCardSettingSwitch(
                    title = stringResource(Res.string.weekly_chart_title),
                    description = stringResource(Res.string.weekly_chart_description),
                    checked = weeklyChartEnabled,
                    onCheckedChange = onWeeklyChartChange,
                )
            }
        }
    }
}

/** A setting of the card's ring, picked in [step]s across [range]. */
@Composable
private fun GaugeSliderSetting(
    title: String,
    description: String,
    value: Int,
    valueText: String,
    range: IntRange,
    step: Int,
    onDrag: (Int) -> Unit,
    onDragFinished: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Column {
                Text(description)
                Slider(
                    value = value.toFloat(),
                    onValueChange = { onDrag(it.roundToInt()) },
                    onValueChangeFinished = onDragFinished,
                    valueRange = range.first.toFloat()..range.last.toFloat(),
                    steps = (range.last - range.first) / step - 1,
                )
            }
        },
        trailingContent = {
            Text(
                text = valueText,
                color = MaterialTheme.colorScheme.primary,
            )
        },
    )
}

@Composable
private fun WeeklyDetailsStyleSetting(
    style: WeeklyDetailsStyle,
    onChange: (WeeklyDetailsStyle) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(stringResource(Res.string.headline_weekly_details_style)) },
        supportingContent = { Text(stringResource(Res.string.description_weekly_details_style)) },
        trailingContent = {
            Box {
                Text(
                    text = style.label(),
                    modifier = Modifier.padding(horizontal = 8.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    WeeklyDetailsStyle.entries.forEach { entry ->
                        DropdownMenuItem(
                            text = { Text(entry.label()) },
                            onClick = {
                                onChange(entry)
                                expanded = false
                            },
                        )
                    }
                }
            }
        },
        modifier = Modifier.clickable { expanded = true },
    )
}

@Composable
private fun WeeklyDetailsStyle.label(): String =
    when (this) {
        WeeklyDetailsStyle.Table -> stringResource(Res.string.weekly_details_style_table)
        WeeklyDetailsStyle.DifferenceBars -> stringResource(Res.string.weekly_details_style_bars)
    }

@Composable
private fun GoalCardSettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(
            value = checked,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ),
    )
}

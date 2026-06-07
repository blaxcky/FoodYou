package com.maksimowiczm.foodyou.app.ui.home.goals

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.LocalFireDepartment as OutlinedLocalFireDepartment
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.common.utility.LocalEnergyFormatter
import com.maksimowiczm.foodyou.app.ui.home.shared.FoodYouHomeCard
import com.maksimowiczm.foodyou.app.ui.home.shared.HomeState
import com.maksimowiczm.foodyou.common.compose.extension.toDp
import com.maksimowiczm.foodyou.common.compose.utility.LocalDateFormatter
import com.maksimowiczm.foodyou.settings.domain.entity.GoalDisplayMode as SettingsGoalDisplayMode
import com.valentinilk.shimmer.Shimmer
import com.valentinilk.shimmer.ShimmerBounds
import com.valentinilk.shimmer.rememberShimmer
import com.valentinilk.shimmer.shimmer
import foodyou.app.generated.resources.Res
import foodyou.app.generated.resources.action_details
import foodyou.app.generated.resources.goal_burned
import foodyou.app.generated.resources.goal_carbs_short
import foodyou.app.generated.resources.goal_eaten
import foodyou.app.generated.resources.goal_display_mode_diet
import foodyou.app.generated.resources.goal_display_mode_normal
import foodyou.app.generated.resources.goal_display_mode_optimized
import foodyou.app.generated.resources.goal_fat
import foodyou.app.generated.resources.goal_goal
import foodyou.app.generated.resources.goal_left
import foodyou.app.generated.resources.goal_no_diet_deficit
import foodyou.app.generated.resources.goal_net_energy
import foodyou.app.generated.resources.goal_protein
import foodyou.app.generated.resources.goal_reached_percentage
import foodyou.app.generated.resources.goal_too_much
import foodyou.app.generated.resources.headline_your_week
import foodyou.app.generated.resources.inter
import foodyou.app.generated.resources.neutral_today_short
import foodyou.app.generated.resources.unit_gram_short
import foodyou.app.generated.resources.unit_kcal
import foodyou.app.generated.resources.weekly_difference
import foodyou.app.generated.resources.weekly_per_day
import foodyou.app.generated.resources.weekly_percent
import foodyou.app.generated.resources.weekly_reached
import foodyou.app.generated.resources.weekly_weight_gained
import foodyou.app.generated.resources.weekly_weight_lost
import foodyou.app.generated.resources.weekly_so_far
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private val GoalsCardShape = RoundedCornerShape(24.dp)
private val GoalsCardColor = Color(0xFFFFFFFF)
private val GoalsTextColor = Color(0xFF202124)
private val GoalsMutedTextColor = Color(0xFF5F6368)
private val GoalsTrackColor = Color(0xFFE4EEF5)
private val GoalsProgressColor = Color(0xFF006B9A)
private val GoalsErrorColor = Color(0xFFC51F1F)
private val NormalGoalComparisonBorderColor = Color(0xFFDADCE0)
private val NormalGoalComparisonTrackColor = Color(0xFFE0E3E7)
private val OverviewGoalAccentColor = Color(0xFF537188)
private val OptimizedGoalAccentColor = GoalsProgressColor
private val DietGoalAccentColor = Color(0xFFC98A00)
private val FatTrackColor = Color(0xFFFFCFCF)
private val FatColor = Color(0xFFFF7477)
private val CarbsTrackColor = Color(0xFFFFE5B8)
private val CarbsColor = Color(0xFFFFB743)
private val ProteinTrackColor = Color(0xFFD4F0DD)
private val ProteinColor = Color(0xFF61BF80)

private enum class GoalCardView {
    Overview,
    Normal,
    Optimized,
    Diet,
}

@Composable
private fun interNumberFontFamily(): FontFamily = FontFamily(Font(Res.font.inter))

@Composable
internal fun GoalsCard(
    homeState: HomeState,
    burnedEnergyDelta: Int? = null,
    onClick: (epochDay: Long) -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GoalsViewModel = koinViewModel(),
) {
    LaunchedEffect(homeState.selectedDate) { viewModel.setDate(homeState.selectedDate) }

    val model = viewModel.model.collectAsStateWithLifecycle().value

    if (model == null) {
        GoalsCardSkeleton(
            onClick = { onClick(homeState.selectedDate.toEpochDays()) },
            onLongClick = onLongClick,
            modifier = modifier,
        )
    } else {
        GoalsCard(
            energy = model.energy,
            burnedEnergy = model.burnedEnergy,
            burnedEnergyDelta = burnedEnergyDelta,
            netEnergy = model.netEnergy,
            energyGoal = model.energyGoal,
            showEnergyGoalValue = model.showEnergyGoalValue,
            goalDisplayMode = model.goalDisplayMode,
            goalDisplaySummaries = model.goalDisplaySummaries,
            dietGoalDisplayModeEnabled = model.dietGoalDisplayModeEnabled,
            proteins = model.proteins,
            proteinsGoal = model.proteinsGoal,
            carbohydrates = model.carbohydrates,
            carbohydratesGoal = model.carbohydratesGoal,
            fats = model.fats,
            fatsGoal = model.fatsGoal,
            onClick = { onClick(homeState.selectedDate.toEpochDays()) },
            onLongClick = onLongClick,
            onSelectGoalDisplayMode = { viewModel.setGoalDisplayMode(it.toSettingsGoalDisplayMode()) },
            modifier = modifier,
        )
    }
}

@Composable
internal fun WeeklyGoalsCard(
    homeState: HomeState,
    modifier: Modifier = Modifier,
    viewModel: GoalsViewModel = koinViewModel(),
) {
    LaunchedEffect(homeState.selectedDate) { viewModel.setDate(homeState.selectedDate) }

    val weekModel = viewModel.weekModel.collectAsStateWithLifecycle().value
    val expanded = viewModel.expandGoalsCard.collectAsStateWithLifecycle().value

    if (weekModel != null) {
        WeeklyGoalsContent(
            model = weekModel,
            expanded = expanded,
            onExpandedChange = viewModel::setExpandGoalsCard,
            modifier = modifier,
        )
    }
}

@Composable
internal fun GoalOverviewCard(
    homeState: HomeState,
    onClick: (epochDay: Long) -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GoalsViewModel = koinViewModel(),
) {
    LaunchedEffect(homeState.selectedDate) { viewModel.setDate(homeState.selectedDate) }

    val model = viewModel.model.collectAsStateWithLifecycle().value ?: return
    val summaries =
        remember(
            model.goalDisplayMode,
            model.energyGoal,
            model.showEnergyGoalValue,
            model.goalDisplaySummaries,
        ) {
            model.goalDisplaySummaries.withNormalGoalDisplaySummary(
                goalDisplayMode = model.goalDisplayMode,
                energyGoal = model.energyGoal,
                showEnergyGoalValue = model.showEnergyGoalValue,
            )
        }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = GoalCardView.Overview.label(),
                style = MaterialTheme.typography.titleMedium,
                color = GoalsTextColor,
                fontWeight = FontWeight.SemiBold,
            )
        }

        CaloriesOverview(
            energy = model.energy,
            burnedEnergy = model.burnedEnergy,
            burnedEnergyDelta = null,
            netEnergy = model.netEnergy,
            energyGoal = model.energyGoal,
            showEnergyGoalValue = model.showEnergyGoalValue,
            goalCardView = GoalCardView.Overview,
            goalDisplayMode = model.goalDisplayMode.availableOrNormal(summaries.map { it.mode }),
            goalDisplaySummaries = summaries,
            dietGoalDisplayModeEnabled = model.dietGoalDisplayModeEnabled,
            onClick = { onClick(homeState.selectedDate.toEpochDays()) },
            onLongClick = onLongClick,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun GoalsCard(
    energy: Int,
    burnedEnergy: Int,
    burnedEnergyDelta: Int? = null,
    netEnergy: Int,
    energyGoal: Int,
    showEnergyGoalValue: Boolean = true,
    goalDisplayMode: GoalDisplayMode = GoalDisplayMode.Normal,
    goalDisplaySummaries: List<GoalDisplaySummaryModel> =
        listOf(
            GoalDisplaySummaryModel(
                mode = goalDisplayMode,
                energyGoal = energyGoal,
                showEnergyGoalValue = showEnergyGoalValue,
            )
        ),
    dietGoalDisplayModeEnabled: Boolean = true,
    proteins: Int,
    proteinsGoal: Int,
    carbohydrates: Int,
    carbohydratesGoal: Int,
    fats: Int,
    fatsGoal: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSelectGoalDisplayMode: (GoalDisplayMode) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val proteinsProgress = goalProgress(proteins, proteinsGoal)
    val carbsProgress = goalProgress(carbohydrates, carbohydratesGoal)
    val fatsProgress = goalProgress(fats, fatsGoal)
    val summaries =
        remember(goalDisplayMode, energyGoal, showEnergyGoalValue, goalDisplaySummaries) {
            goalDisplaySummaries.withNormalGoalDisplaySummary(
                goalDisplayMode = goalDisplayMode,
                energyGoal = energyGoal,
                showEnergyGoalValue = showEnergyGoalValue,
            )
        }
    val availableGoalDisplaySummaries =
        remember(summaries, dietGoalDisplayModeEnabled) {
            summaries.availableForGoalDisplayModes(dietGoalDisplayModeEnabled)
        }
    val availableGoalDisplayModes =
        remember(availableGoalDisplaySummaries) { availableGoalDisplaySummaries.map { it.mode } }
    val effectiveGoalDisplayMode = goalDisplayMode.availableOrNormal(availableGoalDisplayModes)
    var displayedGoalCardView by remember { mutableStateOf(effectiveGoalDisplayMode.toGoalCardView()) }

    LaunchedEffect(goalDisplayMode, availableGoalDisplayModes) {
        displayedGoalCardView =
            goalDisplayMode.availableOrNormal(availableGoalDisplayModes).toGoalCardView()
    }

    Column(modifier = modifier) {
        GoalDisplayModeButtons(
            goalCardView = displayedGoalCardView,
            availableGoalDisplayModes = availableGoalDisplayModes,
            dietGoalDisplayModeEnabled = dietGoalDisplayModeEnabled,
            onSelectGoalCardView = {
                when (it) {
                    GoalCardView.Overview -> displayedGoalCardView = GoalCardView.Overview
                    GoalCardView.Normal,
                    GoalCardView.Optimized,
                    GoalCardView.Diet -> {
                        val mode = it.toGoalDisplayMode()
                        if (mode in availableGoalDisplayModes) {
                            displayedGoalCardView = it
                            onSelectGoalDisplayMode(mode)
                        }
                    }
                }
            },
            modifier =
                Modifier.fillMaxWidth()
                    .height(38.dp)
                    .padding(end = 14.dp),
        )

        Spacer(Modifier.height(4.dp))

        CaloriesOverview(
            energy = energy,
            burnedEnergy = burnedEnergy,
            burnedEnergyDelta = burnedEnergyDelta,
            netEnergy = netEnergy,
            energyGoal = energyGoal,
            showEnergyGoalValue = showEnergyGoalValue,
            goalCardView = displayedGoalCardView,
            goalDisplayMode = displayedGoalCardView.toGoalDisplayMode(),
            goalDisplaySummaries = summaries,
            dietGoalDisplayModeEnabled = dietGoalDisplayModeEnabled,
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            MacroGoalsFooter(
                proteins = proteins,
                proteinsGoal = proteinsGoal,
                carbohydrates = carbohydrates,
                carbohydratesGoal = carbohydratesGoal,
                fats = fats,
                fatsGoal = fatsGoal,
                proteinsProgress = proteinsProgress,
                carbsProgress = carbsProgress,
                fatsProgress = fatsProgress,
            )
        }
    }
}

@Composable
private fun MacroGoalsFooter(
    proteins: Int,
    proteinsGoal: Int,
    carbohydrates: Int,
    carbohydratesGoal: Int,
    fats: Int,
    fatsGoal: Int,
    proteinsProgress: Float = goalProgress(proteins, proteinsGoal),
    carbsProgress: Float = goalProgress(carbohydrates, carbohydratesGoal),
    fatsProgress: Float = goalProgress(fats, fatsGoal),
) {
    Spacer(Modifier.height(10.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MacroGoal(
            label = stringResource(Res.string.goal_fat),
            value = fats,
            goal = fatsGoal,
            progress = fatsProgress,
            trackColor = FatTrackColor,
            color = FatColor,
            modifier = Modifier.weight(1f),
        )
        MacroGoal(
            label = stringResource(Res.string.goal_carbs_short),
            value = carbohydrates,
            goal = carbohydratesGoal,
            progress = carbsProgress,
            trackColor = CarbsTrackColor,
            color = CarbsColor,
            modifier = Modifier.weight(1f),
        )
        MacroGoal(
            label = stringResource(Res.string.goal_protein),
            value = proteins,
            goal = proteinsGoal,
            progress = proteinsProgress,
            trackColor = ProteinTrackColor,
            color = ProteinColor,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun goalProgress(value: Int, goal: Int): Float =
    if (goal <= 0) {
        0f
    } else {
        (value.toFloat() / goal).coerceIn(0f, 1f)
    }

internal fun goalReachedPercentage(value: Int, goal: Int): Int =
    (value.toFloat() / goal.coerceAtLeast(1) * 100).roundToInt().coerceAtLeast(0)

internal data class CalorieGoalProgress(
    val progress: Float,
    val overflowProgress: Float,
    val reachedPercentage: Int,
)

internal fun calorieGoalProgress(netEnergy: Int, energyGoal: Int, percentageEnergyGoal: Int) =
    if (energyGoal < 0) {
        val goal = abs(energyGoal).coerceAtLeast(1)
        CalorieGoalProgress(
            progress = (-netEnergy.toFloat() / goal).coerceIn(0f, 1f),
            overflowProgress = ((netEnergy - energyGoal).toFloat() / goal).coerceIn(0f, 1f),
            reachedPercentage = (-netEnergy.toFloat() / goal * 100).roundToInt().coerceAtLeast(0),
        )
    } else {
        val goal = percentageEnergyGoal.coerceAtLeast(1)
        CalorieGoalProgress(
            progress = (netEnergy.toFloat() / goal).coerceIn(0f, 1f),
            overflowProgress = ((netEnergy - goal).toFloat() / goal).coerceIn(0f, 1f),
            reachedPercentage = goalReachedPercentage(netEnergy, goal),
        )
    }

@Composable
private fun WeeklyGoalsContent(
    model: WeekSummaryModel,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.headline_your_week),
                style = MaterialTheme.typography.titleMedium,
                color = GoalsTextColor,
                fontWeight = FontWeight.SemiBold,
            )
        }

        FoodYouHomeCard(color = GoalsCardColor, shape = GoalsCardShape) {
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                WeeklyGoalsHeader(model)
                WeeklyGoalsChart(days = model.days, today = model.today)
                WeeklyDetailsToggle(expanded = expanded, onClick = { onExpandedChange(!expanded) })
                if (expanded) {
                    WeeklyDetailsTable(model.days)
                    HorizontalDivider(color = GoalsTrackColor)
                    WeeklySummaryFooter(model)
                }
            }
        }
    }
}

@Composable
private fun WeeklyGoalsHeader(model: WeekSummaryModel, modifier: Modifier = Modifier) {
    val energyFormatter = LocalEnergyFormatter.current
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Filled.LocalFireDepartment,
            contentDescription = null,
            tint = FatColor,
            modifier = Modifier.size(32.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text =
                energyFormatter.formatEnergy(model.totalEnergy, withSuffix = false).groupDigits() +
                    "/" +
                    energyFormatter.formatEnergy(model.totalGoal, withSuffix = false).groupDigits() +
                    " " +
                    stringResource(Res.string.unit_kcal),
            color = GoalsTextColor,
            style =
                MaterialTheme.typography.titleLarge.copy(
                    fontFamily = interNumberFontFamily(),
                    fontSize = 20.sp,
                    lineHeight = 24.sp,
                ),
        )
    }
}

@Composable
private fun WeeklyGoalsChart(
    days: List<WeekDaySummaryModel>,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    val max = days.maxOfOrNull { maxOf(it.energy, it.goal) }?.coerceAtLeast(1) ?: 1
    val dateFormatter = LocalDateFormatter.current
    val todayLabel = stringResource(Res.string.neutral_today_short)

    Row(
        modifier = modifier.fillMaxWidth().height(184.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        repeat(7) { index ->
            val day = days.getOrNull(index)
            if (day == null) {
                Spacer(Modifier.weight(1f))
            } else {
                WeeklyBar(
                    day = day,
                    label =
                        day.chartLabel(
                            today = today,
                            todayLabel = todayLabel,
                            weekDayNamesShort = dateFormatter.weekDayNamesShort,
                        ),
                    max = max,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun WeeklyBar(
    day: WeekDaySummaryModel,
    label: String,
    max: Int,
    modifier: Modifier = Modifier,
) {
    val valueHeight = (day.energy.toFloat() / max).coerceIn(0.03f, 1f)
    val overflow = day.energy > day.goal && day.goal > 0

    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(
            text = day.energy.toString().groupDigits(),
            color = GoalsTextColor,
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = interNumberFontFamily()),
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .height(126.dp)
                    .padding(horizontal = 2.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            if (overflow) {
                val goalFraction = (day.goal.toFloat() / day.energy).coerceIn(0f, 1f)
                Column(
                    modifier =
                        Modifier.fillMaxWidth()
                            .fillMaxHeight(valueHeight)
                            .align(Alignment.BottomCenter)
                ) {
                    Box(
                        modifier =
                            Modifier.fillMaxWidth()
                                .weight(1f - goalFraction)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(Color(0xFFC57484))
                    )
                    Box(
                        modifier =
                            Modifier.fillMaxWidth()
                                .weight(goalFraction)
                                .clip(RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
                                .background(FatColor)
                    )
                }
            } else {
                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .fillMaxHeight(valueHeight)
                            .align(Alignment.BottomCenter)
                            .clip(RoundedCornerShape(6.dp))
                            .background(FatColor)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            color = GoalsTextColor,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

@Composable
private fun WeeklyDetailsToggle(expanded: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.action_details),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun WeeklyDetailsTable(days: List<WeekDaySummaryModel>, modifier: Modifier = Modifier) {
    val dateFormatter = LocalDateFormatter.current
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WeeklyDetailsRow(
            day = "",
            goal = stringResource(Res.string.goal_goal).replaceFirstChar { it.uppercase() },
            soFar = stringResource(Res.string.weekly_so_far),
            difference = stringResource(Res.string.weekly_difference),
            percent = stringResource(Res.string.weekly_percent),
            header = true,
        )
        days.forEach { day ->
            WeeklyDetailsRow(
                day = day.tableLabel(dateFormatter.weekDayNamesShort),
                goal = day.goal.toString().groupDigits(),
                soFar = day.energy.toString().groupDigits(),
                difference = day.difference.toString().groupDigits(),
                percent = day.percent.toString(),
            )
        }
    }
}

@Composable
private fun WeeklyDetailsRow(
    day: String,
    goal: String,
    soFar: String,
    difference: String,
    percent: String,
    modifier: Modifier = Modifier,
    header: Boolean = false,
) {
    val numberFontFamily = interNumberFontFamily()
    val style =
        if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium
    val valueStyle = if (header) style else style.copy(fontFamily = numberFontFamily)
    val weight = if (header) FontWeight.SemiBold else FontWeight.Normal

    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(day, modifier = Modifier.weight(0.65f), style = style, fontWeight = FontWeight.SemiBold)
        Text(goal, modifier = Modifier.weight(1f), style = valueStyle, fontWeight = weight)
        Text(soFar, modifier = Modifier.weight(1f), style = valueStyle, fontWeight = weight)
        Text(difference, modifier = Modifier.weight(1f), style = valueStyle, fontWeight = weight)
        Text(percent, modifier = Modifier.weight(0.9f), style = valueStyle, fontWeight = weight)
    }
}

@Composable
private fun WeeklySummaryFooter(model: WeekSummaryModel, modifier: Modifier = Modifier) {
    val remaining = model.totalGoal - model.totalEnergy
    val absoluteRemaining = abs(remaining)
    val estimatedWeightKg = (absoluteRemaining / 7700.0).formatKgEstimate()
    val weightChangeLabel =
        stringResource(
            if (remaining >= 0) Res.string.weekly_weight_lost
            else Res.string.weekly_weight_gained
        )
    val average = if (model.days.isEmpty()) 0 else model.totalEnergy / model.days.size
    val percent =
        if (model.totalGoal <= 0) 0 else (model.totalEnergy.toFloat() / model.totalGoal * 100).roundToInt()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            WeeklyFooterMetric(
                icon = Icons.Filled.Speed,
                value = "${absoluteRemaining.toString().groupDigits()} ${stringResource(Res.string.unit_kcal)}",
                label = stringResource(if (remaining >= 0) Res.string.goal_left else Res.string.goal_too_much),
                modifier = Modifier.weight(1f),
            )
            WeeklyFooterMetric(
                icon = Icons.Filled.LocalFireDepartment,
                value = "${average.toString().groupDigits()} ${stringResource(Res.string.unit_kcal)}",
                label = stringResource(Res.string.weekly_per_day),
                modifier = Modifier.weight(1f),
            )
            WeeklyFooterMetric(
                icon = Icons.Filled.CheckCircleOutline,
                value = "$percent %",
                label = stringResource(Res.string.weekly_reached),
                modifier = Modifier.weight(1f),
            )
        }

        HorizontalDivider(color = GoalsTrackColor)

        WeeklyFooterWeightEstimate(
            icon = Icons.Filled.MonitorWeight,
            value = "$estimatedWeightKg kg",
            label = weightChangeLabel,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun WeeklyFooterMetric(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = GoalsTextColor,
            modifier = Modifier.size(22.dp).alpha(0.95f),
        )
        Spacer(Modifier.width(6.dp))
        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = GoalsTextColor,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Normal,
                color = GoalsTextColor,
            )
        }
    }
}

@Composable
private fun WeeklyFooterWeightEstimate(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val text =
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(value) }
            append(" ")
            append(label)
        }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = GoalsTextColor,
            modifier = Modifier.size(22.dp).alpha(0.95f),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = GoalsTextColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CaloriesOverview(
    energy: Int,
    burnedEnergy: Int,
    burnedEnergyDelta: Int?,
    netEnergy: Int,
    energyGoal: Int,
    showEnergyGoalValue: Boolean,
    goalCardView: GoalCardView,
    goalDisplayMode: GoalDisplayMode,
    goalDisplaySummaries: List<GoalDisplaySummaryModel>,
    dietGoalDisplayModeEnabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit = {},
) {
    val summaries =
        remember(goalDisplayMode, energyGoal, showEnergyGoalValue, goalDisplaySummaries) {
            goalDisplaySummaries
                .takeIf { summaries -> summaries.any { it.mode == goalDisplayMode } }
                ?: listOf(
                    GoalDisplaySummaryModel(
                        mode = goalDisplayMode,
                        energyGoal = energyGoal,
                        showEnergyGoalValue = showEnergyGoalValue,
                    )
                )
        }
    val selectedIndex = summaries.indexOfFirst { it.mode == goalDisplayMode }.coerceAtLeast(0)
    val currentSummary = summaries[selectedIndex]

    Box(
        modifier =
            modifier
                .clip(GoalsCardShape)
                .background(Color.Transparent)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            if (goalCardView == GoalCardView.Overview) {
                GoalComparisonOverviewCard(
                    netEnergy = netEnergy,
                    summaries = summaries,
                    dietGoalDisplayModeEnabled = dietGoalDisplayModeEnabled,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    modifier = Modifier.fillMaxWidth(),
                    footer = footer,
                )
            } else {
                CaloriesOverviewPageCard(
                    energy = energy,
                    burnedEnergy = burnedEnergy,
                    burnedEnergyDelta = burnedEnergyDelta,
                    netEnergy = netEnergy,
                    summary = currentSummary,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    modifier = Modifier.fillMaxWidth(),
                    footer = footer,
                )
            }
        }
    }
}

@Composable
private fun GoalComparisonOverviewCard(
    netEnergy: Int,
    summaries: List<GoalDisplaySummaryModel>,
    dietGoalDisplayModeEnabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit = {},
) {
    FoodYouHomeCard(
        modifier = modifier,
        color = GoalsCardColor,
        shape = GoalsCardShape,
        onClick = onClick,
        onLongClick = onLongClick,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val horizontalPadding = if (maxWidth < 430.dp) 24.dp else 48.dp
            val compact = maxWidth < 360.dp

            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(
                            start = horizontalPadding,
                            top = 21.dp,
                            end = horizontalPadding,
                            bottom = 14.dp,
                        ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GoalComparisonHeader(netEnergy = netEnergy)

                val normalSummary = summaries.firstOrNull { it.mode == GoalDisplayMode.Normal }
                val optimizedSummary =
                    summaries.firstOrNull { it.mode == GoalDisplayMode.Optimized }
                val dietSummary = summaries.firstOrNull { it.mode == GoalDisplayMode.Diet }

                if (compact) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GoalComparisonItem(
                            mode = GoalDisplayMode.Normal,
                            netEnergy = netEnergy,
                            summary = normalSummary,
                            enabled = normalSummary != null,
                        )
                        GoalComparisonItem(
                            mode = GoalDisplayMode.Optimized,
                            netEnergy = netEnergy,
                            summary = optimizedSummary,
                            enabled = optimizedSummary?.showEnergyGoalValue == true,
                        )
                        GoalComparisonItem(
                            mode = GoalDisplayMode.Diet,
                            netEnergy = netEnergy,
                            summary = dietSummary,
                            enabled = dietGoalDisplayModeEnabled && dietSummary != null,
                            disabledLabel = stringResource(Res.string.goal_no_diet_deficit),
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        GoalComparisonItem(
                            mode = GoalDisplayMode.Normal,
                            netEnergy = netEnergy,
                            summary = normalSummary,
                            enabled = normalSummary != null,
                            modifier = Modifier.weight(1f),
                        )
                        GoalComparisonItem(
                            mode = GoalDisplayMode.Optimized,
                            netEnergy = netEnergy,
                            summary = optimizedSummary,
                            enabled = optimizedSummary?.showEnergyGoalValue == true,
                            modifier = Modifier.weight(1f),
                        )
                        GoalComparisonItem(
                            mode = GoalDisplayMode.Diet,
                            netEnergy = netEnergy,
                            summary = dietSummary,
                            enabled = dietGoalDisplayModeEnabled && dietSummary != null,
                            disabledLabel = stringResource(Res.string.goal_no_diet_deficit),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                footer()
            }
        }
    }
}

@Composable
private fun GoalComparisonHeader(netEnergy: Int, modifier: Modifier = Modifier) {
    val energyFormatter = LocalEnergyFormatter.current
    val valueColor = if (netEnergy < 0) GoalsErrorColor else GoalsTextColor
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.LocalFireDepartment,
            contentDescription = null,
            tint = GoalsProgressColor,
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text =
                    "${energyFormatter.formatEnergy(netEnergy, withSuffix = false).groupDigits()} " +
                        stringResource(Res.string.unit_kcal),
                color = valueColor,
                style =
                    MaterialTheme.typography.titleLarge.copy(
                        fontFamily = interNumberFontFamily(),
                        fontSize = 20.sp,
                        lineHeight = 24.sp,
                    ),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = stringResource(Res.string.goal_net_energy),
                color = GoalsMutedTextColor,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun GoalComparisonItem(
    mode: GoalDisplayMode,
    netEnergy: Int,
    summary: GoalDisplaySummaryModel?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    disabledLabel: String? = null,
) {
    val energyFormatter = LocalEnergyFormatter.current
    val numberFontFamily = interNumberFontFamily()
    val accentColor = mode.accentColor()
    val neutral = mode == GoalDisplayMode.Normal
    val backgroundColor =
        if (neutral) Color.Transparent else accentColor.copy(alpha = if (enabled) 0.1f else 0.04f)
    val borderColor =
        if (neutral) {
            NormalGoalComparisonBorderColor.copy(alpha = if (enabled) 1f else 0.42f)
        } else {
            accentColor.copy(alpha = if (enabled) 0.28f else 0.1f)
        }
    val iconColor = if (neutral) GoalsMutedTextColor else accentColor
    val progressTrackColor = if (neutral) NormalGoalComparisonTrackColor else GoalsTrackColor
    val cardShape = RoundedCornerShape(14.dp)
    val target = summary?.energyGoal ?: 0
    val remaining = target - netEnergy
    val overflow = remaining < 0
    val remainingValue = abs(remaining)
    val remainingLabel =
        stringResource(if (overflow) Res.string.goal_too_much else Res.string.goal_left)
    val calorieProgress =
        summary?.let { calorieGoalProgress(netEnergy, it.energyGoal, it.percentageEnergyGoal) }
    val progress = calorieProgress?.progress ?: 0f
    val overflowProgress = calorieProgress?.overflowProgress ?: 0f
    val contentAlpha = if (enabled) 1f else 0.46f
    val remainingColor = if (enabled && overflow) GoalsErrorColor else GoalsTextColor
    val targetColor = if (target < 0) GoalsErrorColor else GoalsMutedTextColor

    Column(
        modifier =
            modifier
                .clip(cardShape)
                .background(backgroundColor)
                .border(
                    width = 1.dp,
                    color = borderColor,
                    shape = cardShape,
                )
                .padding(horizontal = 10.dp, vertical = 10.dp)
                .alpha(contentAlpha),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector =
                    if (neutral) {
                        Icons.Outlined.OutlinedLocalFireDepartment
                    } else {
                        mode.icon()
                    },
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = mode.label(),
                color = GoalsTextColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text =
                if (enabled && summary?.showEnergyGoalValue == true) {
                    "${energyFormatter.formatEnergy(remainingValue, withSuffix = false).groupDigits()} " +
                        stringResource(Res.string.unit_kcal)
                } else {
                    "-"
                },
            color = remainingColor,
            style =
                MaterialTheme.typography.titleMedium.copy(
                    fontFamily = numberFontFamily,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                ),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (enabled && summary?.showEnergyGoalValue == true) {
            Text(
                text = remainingLabel,
                color = remainingColor,
                style =
                    MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 14.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        MacroProgressBar(
            progress = progress,
            trackColor = progressTrackColor,
            color = accentColor,
            overflow = false,
            overflowProgress = if (enabled) overflowProgress else 0f,
            overflowGapWidth = 1.dp,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text =
                if (enabled && summary?.showEnergyGoalValue == true) {
                    "${energyFormatter.formatEnergy(target, withSuffix = false).groupDigits()} " +
                        stringResource(Res.string.unit_kcal) +
                        " " +
                        stringResource(Res.string.goal_goal)
                } else {
                    disabledLabel ?: "-"
                },
            color = targetColor,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, lineHeight = 14.sp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CaloriesOverviewPageCard(
    energy: Int,
    burnedEnergy: Int,
    burnedEnergyDelta: Int?,
    netEnergy: Int,
    summary: GoalDisplaySummaryModel,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit = {},
) {
    FoodYouHomeCard(
        modifier = modifier,
        color = GoalsCardColor,
        shape = GoalsCardShape,
        onClick = onClick,
        onLongClick = onLongClick,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val horizontalPadding = if (maxWidth < 430.dp) 24.dp else 48.dp

            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .defaultMinSize(minHeight = 259.dp)
                        .padding(
                            start = horizontalPadding,
                            top = 21.dp,
                            end = horizontalPadding,
                            bottom = 14.dp,
                        ),
            ) {
                CaloriesOverviewPage(
                    energy = energy,
                    burnedEnergy = burnedEnergy,
                    burnedEnergyDelta = burnedEnergyDelta,
                    netEnergy = netEnergy,
                    summary = summary,
                    modifier = Modifier.fillMaxWidth(),
                    footer = footer,
                )
            }
        }
    }
}

@Composable
private fun CaloriesOverviewPage(
    energy: Int,
    burnedEnergy: Int,
    burnedEnergyDelta: Int?,
    netEnergy: Int,
    summary: GoalDisplaySummaryModel,
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit = {},
) {
    val energyGoal = summary.energyGoal
    val showEnergyGoalValue = summary.showEnergyGoalValue
    val energyFormatter = LocalEnergyFormatter.current
    val left = energyGoal - netEnergy
    val calorieProgress =
        calorieGoalProgress(
            netEnergy = netEnergy,
            energyGoal = summary.energyGoal,
            percentageEnergyGoal = summary.percentageEnergyGoal,
        )
    val reached = calorieProgress.reachedPercentage
    val progress = calorieProgress.progress
    val overflowProgress = calorieProgress.overflowProgress
    val overflow = left < 0
    val remainingValue = if (overflow) -left else left
    val valueColor = if (overflow) GoalsErrorColor else GoalsTextColor
    val remainingLabel =
        stringResource(if (overflow) Res.string.goal_too_much else Res.string.goal_left)

    BoxWithConstraints(
        modifier = modifier
    ) {
        val gaugeDiameter = (maxWidth * 0.48f).coerceIn(156.dp, 254.dp)
        val compact = maxWidth < 320.dp
        val phoneWidth = maxWidth < 430.dp
        val caloriesHeight = if (phoneWidth) 164.dp else 230.dp
        val sideMetricTopPadding = if (phoneWidth) 39.dp else 50.dp
        val secondaryMetricTopPadding = 18.dp

        Column(modifier = Modifier.fillMaxWidth()) {
            if (compact) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    GaugeMetric(
                        value =
                            if (showEnergyGoalValue) {
                                energyFormatter.formatEnergy(remainingValue, withSuffix = false)
                                    .groupDigits()
                            } else {
                                "-"
                            },
                        label = remainingLabel,
                        progress = progress,
                        overflowProgress = overflowProgress,
                        progressColor = summary.mode.accentColor(),
                        valueColor = valueColor,
                        diameter = gaugeDiameter,
                    )

                    Spacer(Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        SideMetric(
                            value =
                                energyFormatter
                                    .formatEnergy(energy, withSuffix = false)
                                    .groupDigits(),
                            label = stringResource(Res.string.goal_eaten),
                            supportingValue = "$reached %",
                            supportingLabel =
                                stringResource(Res.string.goal_reached_percentage, reached)
                                    .substringAfter("% "),
                            supportingTopPadding = secondaryMetricTopPadding,
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        )
                        SideMetric(
                            value =
                                energyFormatter
                                    .formatEnergy(burnedEnergy, withSuffix = false)
                                    .groupDigits(),
                            deltaValue = burnedEnergyDelta,
                            label = stringResource(Res.string.goal_burned),
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        )
                        SideMetric(
                            value =
                                if (showEnergyGoalValue) {
                                    energyFormatter
                                        .formatEnergy(energyGoal, withSuffix = false)
                                        .groupDigits()
                                } else {
                                    "-"
                                },
                            label = stringResource(Res.string.goal_goal),
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            muted = true,
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().height(caloriesHeight),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    SideMetric(
                        value =
                            energyFormatter.formatEnergy(energy, withSuffix = false).groupDigits(),
                        label = stringResource(Res.string.goal_eaten),
                        supportingValue = "$reached %",
                        supportingLabel =
                            stringResource(Res.string.goal_reached_percentage, reached)
                                .substringAfter("% "),
                        supportingTopPadding = secondaryMetricTopPadding,
                        modifier = Modifier.weight(1f).padding(top = sideMetricTopPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    )
                    GaugeMetric(
                        value =
                            if (showEnergyGoalValue) {
                                energyFormatter.formatEnergy(remainingValue, withSuffix = false)
                                    .groupDigits()
                            } else {
                                "-"
                            },
                        label = remainingLabel,
                        progress = progress,
                        overflowProgress = overflowProgress,
                        progressColor = summary.mode.accentColor(),
                        valueColor = valueColor,
                        diameter = gaugeDiameter,
                    )
                    Column(
                        modifier = Modifier.weight(1f).padding(top = sideMetricTopPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(secondaryMetricTopPadding + 3.dp),
                    ) {
                        SideMetric(
                            value =
                                energyFormatter
                                    .formatEnergy(burnedEnergy, withSuffix = false)
                                    .groupDigits(),
                            deltaValue = burnedEnergyDelta,
                            label = stringResource(Res.string.goal_burned),
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        )
                        SideMetric(
                            value =
                                if (showEnergyGoalValue) {
                                    energyFormatter
                                        .formatEnergy(energyGoal, withSuffix = false)
                                        .groupDigits()
                                } else {
                                    "-"
                                },
                            label = stringResource(Res.string.goal_goal),
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            muted = true,
                        )
                    }
                }
            }

            footer()
        }

    }
}

@Composable
private fun GoalDisplayModeButtons(
    goalCardView: GoalCardView,
    availableGoalDisplayModes: List<GoalDisplayMode>,
    dietGoalDisplayModeEnabled: Boolean,
    onSelectGoalCardView: (GoalCardView) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = goalCardView.label(),
            modifier = Modifier.weight(1f).padding(start = 16.dp, end = 8.dp),
            color =
                if (goalCardView == GoalCardView.Normal) {
                    GoalsTextColor
                } else {
                    goalCardView.accentColor()
                },
            style =
                MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 22.sp,
                    lineHeight = 26.sp,
                ),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        GoalDisplayModeButton(
            view = GoalCardView.Normal,
            selected = goalCardView == GoalCardView.Normal,
            enabled = GoalDisplayMode.Normal in availableGoalDisplayModes,
            contentDescription = stringResource(Res.string.goal_display_mode_normal),
            onClick = onSelectGoalCardView,
        )
        GoalDisplayModeButton(
            view = GoalCardView.Optimized,
            selected = goalCardView == GoalCardView.Optimized,
            enabled = GoalDisplayMode.Optimized in availableGoalDisplayModes,
            contentDescription = stringResource(Res.string.goal_display_mode_optimized),
            onClick = onSelectGoalCardView,
        )
        GoalDisplayModeButton(
            view = GoalCardView.Diet,
            selected = goalCardView == GoalCardView.Diet,
            enabled =
                dietGoalDisplayModeEnabled && GoalDisplayMode.Diet in availableGoalDisplayModes,
            contentDescription = stringResource(Res.string.goal_display_mode_diet),
            onClick = onSelectGoalCardView,
        )
    }
}

@Composable
private fun GoalDisplayModeButton(
    view: GoalCardView,
    selected: Boolean,
    contentDescription: String,
    onClick: (GoalCardView) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val accentColor = view.accentColor()
    val neutral = view == GoalCardView.Normal
    val selectedBackgroundColor =
        if (neutral) {
            Color.Transparent
        } else {
            accentColor.copy(alpha = 0.12f)
        }
    val selectedBorderColor =
        if (neutral) {
            NormalGoalComparisonBorderColor
        } else {
            accentColor.copy(alpha = 0.7f)
        }
    val selectedIconColor = if (neutral) GoalsMutedTextColor else accentColor
    val shape = RoundedCornerShape(50)
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(34.dp)
                .clip(shape)
                .background(if (selected) selectedBackgroundColor else Color.Transparent)
                .then(
                    if (selected) {
                        Modifier.border(1.dp, selectedBorderColor, shape)
                    } else {
                        Modifier
                    }
                )
                .clickable(enabled = enabled) { onClick(view) },
    ) {
        Icon(
            imageVector =
                if (neutral) {
                    Icons.Outlined.OutlinedLocalFireDepartment
                } else {
                    view.icon()
                },
            contentDescription = contentDescription,
            tint =
                when {
                    !enabled -> GoalsMutedTextColor.copy(alpha = 0.34f)
                    selected -> selectedIconColor
                    else -> GoalsMutedTextColor.copy(alpha = 0.62f)
                },
                modifier =
                    Modifier.size(18.dp)
                        .alpha(if (enabled) 1f else 0.54f),
        )
    }
}

internal fun List<GoalDisplaySummaryModel>.withNormalGoalDisplaySummary(
    goalDisplayMode: GoalDisplayMode,
    energyGoal: Int,
    showEnergyGoalValue: Boolean,
): List<GoalDisplaySummaryModel> {
    val fallbackSummary =
        GoalDisplaySummaryModel(
            mode = goalDisplayMode,
            energyGoal = energyGoal,
            showEnergyGoalValue = showEnergyGoalValue,
        )
    val baseSummaries =
        takeIf { summaries ->
            summaries.any { it.mode == goalDisplayMode } ||
                summaries.any { it.mode == GoalDisplayMode.Normal }
        } ?: listOf(fallbackSummary)
    return if (baseSummaries.any { it.mode == GoalDisplayMode.Normal }) {
        baseSummaries
    } else {
        listOf(
            GoalDisplaySummaryModel(
                mode = GoalDisplayMode.Normal,
                energyGoal = energyGoal,
                showEnergyGoalValue = true,
            )
        ) + baseSummaries
    }.distinctBy { it.mode }
}

internal fun List<GoalDisplaySummaryModel>.availableForGoalDisplayModes(
    dietGoalDisplayModeEnabled: Boolean
): List<GoalDisplaySummaryModel> =
    filter { it.availableForGoalDisplayMode(dietGoalDisplayModeEnabled) }

internal fun GoalDisplayMode.availableOrNormal(
    availableGoalDisplayModes: List<GoalDisplayMode>
): GoalDisplayMode =
    if (this in availableGoalDisplayModes) {
        this
    } else {
        GoalDisplayMode.Normal
    }

private fun GoalDisplaySummaryModel.availableForGoalDisplayMode(
    dietGoalDisplayModeEnabled: Boolean
): Boolean =
    when (mode) {
        GoalDisplayMode.Normal -> true
        GoalDisplayMode.Optimized -> showEnergyGoalValue
        GoalDisplayMode.Diet -> showEnergyGoalValue && dietGoalDisplayModeEnabled
    }

private fun GoalDisplayMode.toSettingsGoalDisplayMode(): SettingsGoalDisplayMode =
    when (this) {
        GoalDisplayMode.Normal -> SettingsGoalDisplayMode.Normal
        GoalDisplayMode.Optimized -> SettingsGoalDisplayMode.Optimized
        GoalDisplayMode.Diet -> SettingsGoalDisplayMode.Diet
    }

private fun GoalDisplayMode.toGoalCardView(): GoalCardView =
    when (this) {
        GoalDisplayMode.Normal -> GoalCardView.Normal
        GoalDisplayMode.Optimized -> GoalCardView.Optimized
        GoalDisplayMode.Diet -> GoalCardView.Diet
    }

private fun GoalCardView.toGoalDisplayMode(): GoalDisplayMode =
    when (this) {
        GoalCardView.Overview -> GoalDisplayMode.Normal
        GoalCardView.Normal -> GoalDisplayMode.Normal
        GoalCardView.Optimized -> GoalDisplayMode.Optimized
        GoalCardView.Diet -> GoalDisplayMode.Diet
    }

private fun GoalCardView.label(): String =
    when (this) {
        GoalCardView.Overview -> "Übersicht"
        GoalCardView.Normal -> GoalDisplayMode.Normal.label()
        GoalCardView.Optimized -> GoalDisplayMode.Optimized.label()
        GoalCardView.Diet -> GoalDisplayMode.Diet.label()
    }

private fun GoalDisplayMode.label(): String =
    when (this) {
        GoalDisplayMode.Normal -> "Normal"
        GoalDisplayMode.Optimized -> "Optimiert"
        GoalDisplayMode.Diet -> "Diät"
    }

private fun GoalCardView.accentColor(): Color =
    when (this) {
        GoalCardView.Overview -> OverviewGoalAccentColor
        GoalCardView.Normal -> GoalDisplayMode.Normal.accentColor()
        GoalCardView.Optimized -> GoalDisplayMode.Optimized.accentColor()
        GoalCardView.Diet -> GoalDisplayMode.Diet.accentColor()
    }

private fun GoalDisplayMode.accentColor(): Color =
    when (this) {
        GoalDisplayMode.Normal -> GoalsProgressColor
        GoalDisplayMode.Optimized -> OptimizedGoalAccentColor
        GoalDisplayMode.Diet -> DietGoalAccentColor
    }

private fun GoalCardView.icon(): androidx.compose.ui.graphics.vector.ImageVector =
    when (this) {
        GoalCardView.Overview -> Icons.Filled.Dashboard
        GoalCardView.Normal -> GoalDisplayMode.Normal.icon()
        GoalCardView.Optimized -> GoalDisplayMode.Optimized.icon()
        GoalCardView.Diet -> GoalDisplayMode.Diet.icon()
    }

private fun GoalDisplayMode.icon(): androidx.compose.ui.graphics.vector.ImageVector =
    when (this) {
        GoalDisplayMode.Normal -> Icons.Filled.LocalFireDepartment
        GoalDisplayMode.Optimized -> Icons.Filled.Speed
        GoalDisplayMode.Diet -> Icons.Filled.MonitorWeight
    }

private fun Dp.coerceIn(minimumValue: Dp, maximumValue: Dp): Dp =
    coerceAtLeast(minimumValue).coerceAtMost(maximumValue)

@Composable
private fun SideMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    deltaValue: Int? = null,
    supportingValue: String? = null,
    supportingLabel: String? = null,
    supportingTopPadding: Dp = 0.dp,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    muted: Boolean = false,
) {
    val numberFontFamily = interNumberFontFamily()
    val energyFormatter = LocalEnergyFormatter.current

    Column(
        modifier = modifier,
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        MetricValue(
            value = value,
            delta =
                deltaValue?.let {
                    "+${energyFormatter.formatEnergy(it, withSuffix = false).groupDigits()}"
                },
            muted = muted,
            numberFontFamily = numberFontFamily,
        )
        Text(
            text = label,
            modifier = Modifier.fillMaxWidth(),
            color = if (muted) GoalsMutedTextColor else GoalsTextColor,
            style =
                MaterialTheme.typography.labelMedium.copy(
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                ),
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
        )
        if (supportingValue != null) {
            Text(
                text = supportingValue,
                modifier = Modifier.fillMaxWidth().padding(top = supportingTopPadding),
                color = GoalsMutedTextColor,
                style =
                    MaterialTheme.typography.labelMedium.copy(
                        fontFamily = numberFontFamily,
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                    ),
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Center,
            )
        }
        if (supportingLabel != null) {
            Text(
                text = supportingLabel,
                modifier = Modifier.fillMaxWidth(),
                color = GoalsMutedTextColor,
                style =
                    MaterialTheme.typography.labelMedium.copy(
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                    ),
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MetricValue(
    value: String,
    delta: String?,
    muted: Boolean,
    numberFontFamily: FontFamily,
) {
    val valueStyle =
        if (muted) {
            MaterialTheme.typography.labelMedium.copy(
                fontFamily = numberFontFamily,
                fontSize = 13.sp,
                lineHeight = 16.sp,
            )
        } else {
            MaterialTheme.typography.titleLarge.copy(
                fontFamily = numberFontFamily,
                fontSize = 18.sp,
                lineHeight = 22.sp,
            )
        }

    val density = LocalDensity.current
    val deltaSpacingPx = with(density) { 3.dp.roundToPx() }
    val deltaYOffsetPx = with(density) { (-5).dp.roundToPx() }
    var containerWidthPx by remember { mutableIntStateOf(0) }
    var valueTextWidthPx by remember(value) { mutableIntStateOf(0) }

    Box(modifier = Modifier.fillMaxWidth().onSizeChanged { containerWidthPx = it.width }) {
        Text(
            text = value,
            modifier = Modifier.align(Alignment.TopCenter),
            color = if (muted) GoalsMutedTextColor else GoalsTextColor,
            style = valueStyle,
            fontWeight = if (muted) FontWeight.Normal else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            onTextLayout = { layout ->
                valueTextWidthPx =
                    ceil(layout.getLineRight(0) - layout.getLineLeft(0)).toInt()
            },
        )
        if (delta != null) {
            Text(
                text = delta,
                modifier =
                    Modifier.offset {
                        val preferredX =
                            containerWidthPx / 2 + valueTextWidthPx / 2 + deltaSpacingPx
                        IntOffset(
                            x = preferredX,
                            y = deltaYOffsetPx,
                        )
                    },
                color = Color(0xFF1B7F3A),
                style =
                    MaterialTheme.typography.labelMedium.copy(
                        fontFamily = numberFontFamily,
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                    ),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

@Composable
private fun GaugeMetric(
    value: String,
    label: String,
    progress: Float,
    overflowProgress: Float,
    progressColor: Color,
    valueColor: Color,
    diameter: Dp,
    modifier: Modifier = Modifier,
) {
    val numberFontFamily = interNumberFontFamily()

    Box(
        modifier = modifier.size(width = diameter, height = diameter - 7.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        SemiCircleGauge(
            progress = progress,
            overflowProgress = overflowProgress,
            progressColor = progressColor,
            diameter = diameter,
            modifier = Modifier.size(width = diameter, height = diameter - 7.dp),
        )

        Column(
            modifier = Modifier.offset(y = diameter * 0.35f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                color = valueColor,
                style =
                    MaterialTheme.typography.headlineLarge.copy(
                        fontFamily = numberFontFamily,
                        fontSize = 24.sp,
                        lineHeight = 28.sp,
                    ),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
            Text(
                text = label,
                modifier = Modifier.padding(top = 8.dp),
                color = GoalsMutedTextColor,
                style =
                    MaterialTheme.typography.labelLarge.copy(
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    ),
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SemiCircleGauge(
    progress: Float,
    overflowProgress: Float,
    progressColor: Color,
    diameter: Dp,
    modifier: Modifier = Modifier,
) {
    val visibleOverflowGapAngle = 2.5f
    Canvas(modifier = modifier) {
        val strokeWidth = 10.dp.toPx()
        val arcDiameter = diameter.toPx()
        val topLeft = Offset(x = (size.width - arcDiameter) / 2f, y = strokeWidth / 2f)
        val arcSize = Size(width = arcDiameter, height = arcDiameter)
        val center = Offset(topLeft.x + arcDiameter / 2f, topLeft.y + arcDiameter / 2f)
        val radius = arcDiameter / 2f

        fun arcPoint(angle: Float): Offset {
            val radians = Math.toRadians(angle.toDouble())
            return Offset(
                x = center.x + radius * cos(radians).toFloat(),
                y = center.y + radius * sin(radians).toFloat(),
            )
        }

        drawArc(
            color = GoalsTrackColor,
            startAngle = 135f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
        val coercedOverflowProgress = overflowProgress.coerceIn(0f, 1f)
        val visibleProgress =
            if (coercedOverflowProgress > 0f) {
                1f - coercedOverflowProgress
            } else {
                progress.coerceIn(0f, 1f)
            }
        val progressSweepAngle = 270f * visibleProgress
        val progressOverflowGap =
            if (visibleProgress > 0f && coercedOverflowProgress > 0f) {
                visibleOverflowGapAngle.coerceAtMost(progressSweepAngle)
            } else {
                0f
            }
        val hasOverflowGap = progressOverflowGap > 0f
        val progressDrawSweepAngle = progressSweepAngle - progressOverflowGap
        if (hasOverflowGap) {
            drawArc(
                color = GoalsCardColor,
                startAngle = 135f + progressDrawSweepAngle,
                sweepAngle = progressOverflowGap,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
            )
        }
        if (progressDrawSweepAngle > 0f) {
            drawArc(
                color = progressColor,
                startAngle = 135f,
                sweepAngle = progressDrawSweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style =
                    Stroke(
                        width = strokeWidth,
                        cap = if (hasOverflowGap) StrokeCap.Butt else StrokeCap.Round,
                    ),
            )
            if (hasOverflowGap) {
                drawCircle(
                    color = progressColor,
                    radius = strokeWidth / 2f,
                    center = arcPoint(135f),
                )
            }
        }
        if (coercedOverflowProgress > 0f) {
            val overflowSweepAngle = 270f * coercedOverflowProgress
            drawArc(
                color = GoalsErrorColor,
                startAngle = 45f - overflowSweepAngle,
                sweepAngle = overflowSweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style =
                    Stroke(
                        width = strokeWidth,
                        cap = if (hasOverflowGap) StrokeCap.Butt else StrokeCap.Round,
                    ),
            )
            if (hasOverflowGap) {
                drawCircle(
                    color = GoalsErrorColor,
                    radius = strokeWidth / 2f,
                    center = arcPoint(45f),
                )
            }
        }
    }
}

private fun String.groupDigits(): String {
    val sign = if (startsWith("-")) "-" else ""
    val digits = if (sign.isEmpty()) this else drop(1)

    if (digits.length <= 3 || digits.any { !it.isDigit() }) return this

    return sign +
        digits
            .reversed()
            .chunked(3)
            .joinToString(" ")
            .reversed()
}

private fun Double.formatKgEstimate(): String {
    val centiKg = (this * 100).roundToInt()
    return "${centiKg / 100}.${(centiKg % 100).toString().padStart(2, '0')}"
}

private fun WeekDaySummaryModel.chartLabel(
    today: LocalDate,
    todayLabel: String,
    weekDayNamesShort: List<String>,
): String =
    if (date == today) {
        todayLabel
    } else {
        tableLabel(weekDayNamesShort)
    }

private fun WeekDaySummaryModel.tableLabel(weekDayNamesShort: List<String>): String {
    val index = (date.dayOfWeek.isoDayNumber - 1).coerceIn(0, weekDayNamesShort.lastIndex)
    return weekDayNamesShort[index].trimEnd('.').plus(".")
}

@Composable
private fun MacroGoal(
    label: String,
    value: Int,
    goal: Int,
    progress: Float,
    trackColor: Color,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val gramShort = stringResource(Res.string.unit_gram_short)
    val numberFontFamily = interNumberFontFamily()
    val valueColor = if (goal > 0 && value > goal) GoalsErrorColor else GoalsTextColor

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.fillMaxWidth(),
            color = GoalsTextColor,
            style =
                MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                ),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
        )
        MacroProgressBar(
            progress = progress,
            trackColor = trackColor,
            color = color,
            overflow = goal > 0 && value > goal,
        )
        Text(
            text = "$value/$goal $gramShort",
            modifier = Modifier.fillMaxWidth(),
            color = valueColor,
            style =
                MaterialTheme.typography.labelMedium.copy(
                    fontFamily = numberFontFamily,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                ),
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MacroProgressBar(
    progress: Float,
    trackColor: Color,
    color: Color,
    overflow: Boolean,
    overflowProgress: Float = 0f,
    overflowGapWidth: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val barShape = RoundedCornerShape(50)
    val coercedOverflowProgress = overflowProgress.coerceIn(0f, 1f)
    val visibleProgress =
        if (coercedOverflowProgress > 0f) {
            1f - coercedOverflowProgress
        } else {
            progress.coerceIn(0f, 1f)
        }
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier =
            modifier
                .width(132.dp)
                .height(6.dp)
                .clip(barShape)
                .background(trackColor)
    ) {
        val overflowGapProgress =
            if (visibleProgress > 0f && coercedOverflowProgress > 0f && overflowGapWidth > 0.dp) {
                with(density) { overflowGapWidth.toPx() / maxWidth.toPx() }
                    .coerceIn(0f, visibleProgress)
            } else {
                0f
            }
        val hasOverflowGap = overflowGapProgress > 0f
        val progressShape =
            if (hasOverflowGap) {
                RoundedCornerShape(topStart = 50.dp, bottomStart = 50.dp)
            } else {
                barShape
            }
        val overflowShape =
            if (hasOverflowGap) {
                RoundedCornerShape(topEnd = 50.dp, bottomEnd = 50.dp)
            } else {
                barShape
            }
        Box(
            modifier =
                Modifier.fillMaxWidth(visibleProgress - overflowGapProgress)
                    .height(6.dp)
                    .clip(progressShape)
                    .background(
                        if (overflow && coercedOverflowProgress == 0f) {
                            GoalsErrorColor
                        } else {
                            color
                        }
                    )
        )
        if (coercedOverflowProgress > 0f) {
            Box(
                modifier =
                    Modifier.align(Alignment.CenterEnd)
                        .fillMaxWidth(coercedOverflowProgress)
                        .height(6.dp)
                        .clip(overflowShape)
                        .background(GoalsErrorColor)
            )
        }
    }
}

@Composable
private fun GoalsCardSkeleton(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shimmer = rememberShimmer(shimmerBounds = ShimmerBounds.Window)

    FoodYouHomeCard(
        modifier = modifier,
        color = GoalsCardColor,
        shape = GoalsCardShape,
        onClick = onClick,
        onLongClick = onLongClick,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val horizontalPadding = if (maxWidth < 430.dp) 24.dp else 48.dp
            val contentWidth = maxWidth - horizontalPadding * 2
            val phoneWidth = contentWidth < 430.dp
            val caloriesHeight = if (phoneWidth) 164.dp else 230.dp
            val gaugeDiameter = (contentWidth * 0.48f).coerceIn(156.dp, 254.dp)

            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .defaultMinSize(minHeight = 259.dp)
                        .padding(
                            start = horizontalPadding,
                            top = 21.dp,
                            end = horizontalPadding,
                            bottom = 14.dp,
                        ),
            ) {
                Box(modifier = Modifier.fillMaxWidth().height(caloriesHeight)) {
                    Box(
                        modifier =
                            Modifier.align(Alignment.TopCenter)
                                .size(width = gaugeDiameter, height = gaugeDiameter - 7.dp),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        SkeletonBlock(
                            shimmer,
                            Modifier.requiredWidth(125.dp)
                                .height(125.dp)
                                .clip(RoundedCornerShape(topStart = 120.dp, topEnd = 120.dp)),
                        )
                        Column(
                            modifier = Modifier.offset(y = 106.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            SkeletonBlock(
                                shimmer,
                                Modifier.width(74.dp).height(42.dp),
                            )
                            Spacer(Modifier.height(5.dp))
                            SkeletonBlock(
                                shimmer,
                                Modifier.width(44.dp).height(24.dp),
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.width(158.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SkeletonBlock(
                            shimmer,
                            Modifier.width(54.dp).height(36.dp),
                        )
                        SkeletonBlock(
                            shimmer,
                            Modifier.width(70.dp).height(22.dp),
                        )
                        SkeletonBlock(
                            shimmer,
                            Modifier.width(42.dp).height(22.dp),
                        )
                    }

                    Column(
                        modifier = Modifier.align(Alignment.TopEnd).width(96.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(30.dp),
                    ) {
                        SkeletonSideMetric(shimmer, Alignment.CenterHorizontally)
                        SkeletonSideMetric(shimmer, Alignment.CenterHorizontally)
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    repeat(3) {
                        Column(
                            modifier = Modifier.width(133.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            SkeletonBlock(
                                shimmer,
                                Modifier.width(48.dp)
                                    .height(MaterialTheme.typography.labelMedium.toDp()),
                            )
                            SkeletonBlock(
                                shimmer,
                                Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                            )
                            SkeletonBlock(
                                shimmer,
                                Modifier.width(58.dp)
                                    .height(MaterialTheme.typography.labelMedium.toDp()),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkeletonSideMetric(shimmer: Shimmer, horizontalAlignment: Alignment.Horizontal) {
    Column(
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SkeletonBlock(
            shimmer,
            Modifier.width(54.dp).height(MaterialTheme.typography.titleLarge.toDp()),
        )
        SkeletonBlock(
            shimmer,
            Modifier.width(70.dp).height(MaterialTheme.typography.labelMedium.toDp()),
        )
    }
}

@Composable
private fun SkeletonBlock(shimmer: Shimmer, modifier: Modifier = Modifier) {
    Spacer(
        modifier
            .shimmer(shimmer)
            .clip(MaterialTheme.shapes.medium)
            .background(Color(0xFFE8EEF2))
    )
}

package com.maksimowiczm.foodyou.app.ui.home.goals

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.LocalFireDepartment as OutlinedLocalFireDepartment
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.common.utility.LocalEnergyFormatter
import com.maksimowiczm.foodyou.app.ui.home.shared.FoodYouHomeCard
import com.maksimowiczm.foodyou.app.ui.home.shared.HomeState
import com.maksimowiczm.foodyou.common.compose.extension.toDp
import com.maksimowiczm.foodyou.common.compose.utility.LocalDateFormatter
import com.maksimowiczm.foodyou.settings.domain.entity.DEFAULT_GOAL_GAUGE_SIZE_PERCENT
import com.maksimowiczm.foodyou.settings.domain.entity.GoalDisplayMode as SettingsGoalDisplayMode
import com.maksimowiczm.foodyou.settings.domain.entity.WeeklyDetailsStyle
import com.valentinilk.shimmer.Shimmer
import com.valentinilk.shimmer.ShimmerBounds
import com.valentinilk.shimmer.rememberShimmer
import com.valentinilk.shimmer.shimmer
import foodyou.app.generated.resources.Res
import foodyou.app.generated.resources.goal_burned
import foodyou.app.generated.resources.goal_carbs_short
import foodyou.app.generated.resources.goal_eaten
import foodyou.app.generated.resources.goal_display_mode_diet
import foodyou.app.generated.resources.goal_display_mode_normal
import foodyou.app.generated.resources.goal_display_mode_optimized
import foodyou.app.generated.resources.goal_fat
import foodyou.app.generated.resources.goal_goal
import foodyou.app.generated.resources.goal_left
import foodyou.app.generated.resources.goal_protein
import foodyou.app.generated.resources.goal_reached_percentage
import foodyou.app.generated.resources.goal_too_much
import foodyou.app.generated.resources.goal_today_target
import foodyou.app.generated.resources.headline_your_week
import foodyou.app.generated.resources.inter
import foodyou.app.generated.resources.label_diet
import foodyou.app.generated.resources.label_optimized
import foodyou.app.generated.resources.locked_day_status
import foodyou.app.generated.resources.neutral_today_short
import foodyou.app.generated.resources.unit_gram_short
import foodyou.app.generated.resources.unit_kcal
import foodyou.app.generated.resources.weekly_diet_goal_reached
import foodyou.app.generated.resources.weekly_difference
import foodyou.app.generated.resources.weekly_sum_on_goal_so_far
import foodyou.app.generated.resources.weekly_sum_over_goal_so_far
import foodyou.app.generated.resources.weekly_sum_under_goal_so_far
import foodyou.app.generated.resources.weekly_of_goal
import foodyou.app.generated.resources.weekly_percent
import foodyou.app.generated.resources.action_weigh_in
import foodyou.app.generated.resources.weekly_weight_gained
import foodyou.app.generated.resources.weekly_weight_last_measured_days_ago
import foodyou.app.generated.resources.weekly_weight_lost
import foodyou.app.generated.resources.weekly_weight_measured_days_ago
import foodyou.app.generated.resources.weekly_weight_measured_today
import foodyou.app.generated.resources.weekly_weight_measured_yesterday
import foodyou.app.generated.resources.weekly_weight_not_measured
import foodyou.app.generated.resources.weekly_so_far
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private val GoalsCardShape = RoundedCornerShape(24.dp)
internal val GoalsCardColor = Color(0xFFFFFFFF)
internal val GoalsTextColor = Color(0xFF202124)
internal val GoalsMutedTextColor = Color(0xFF5F6368)
internal val GoalsTrackColor = Color(0xFFE4EEF5)
internal val GoalsProgressColor = Color(0xFF006B9A)
internal val GoalsErrorColor = Color(0xFFC51F1F)
private val NormalGoalComparisonBorderColor = Color(0xFFDADCE0)
private val OptimizedGoalAccentColor = GoalsProgressColor
internal val DietGoalAccentColor = Color(0xFFC98A00)

/** The ring section up to the Optimized goal; much lighter than the deep-blue Normal section. */
internal val OptimizedGoalZoneColor = Color(0xFF4FA9DB)

/** The empty Optimized section of the ring, clearly apart from the normal [GoalsTrackColor]. */
internal val OptimizedGoalZoneTrackColor = Color(0xFFB5DBF0)
private val DietGoalReachedTextColor = Color(0xFF2E7D32)
private val DietGoalReachedRowColor = Color(0xFFF0F7F0)
private val WeeklyOverGoalBarColor = Color(0xFFC57484)
private val WeeklyOverGoalTextColor = Color(0xFF9B3F55)
private val WeeklyColumnOverBarColor = Color(0xFFD493A0)
private val WeeklyColumnUnderBarColor = Color(0xFF8CCBA4)
private val WeeklyGoalLineColor = Color(0xFFC4C7C5)
private val WeeklyFutureDayColor = Color(0xFF757575)
private val WeeklyFutureDotColor = Color(0xFFDADCE0)
private val WeeklyColumnBarWidth = 10.dp
private val WeeklyColumnMaxBarLength = 56.dp
private val WeeklyColumnOverHeight = 84.dp
private val WeeklyColumnLineHeight = 8.dp
private val WeeklyColumnUnderHeight = 76.dp
private val WeeklyColumnValueTextSize = 11.sp
private val WeeklyRingSize = 56.dp
private val WeeklyRingStroke = 7.dp
private val FatTrackColor = Color(0xFFFFCFCF)
private val FatColor = Color(0xFFFF7477)
private val CarbsTrackColor = Color(0xFFFFE5B8)
private val CarbsColor = Color(0xFFFFB743)
private val ProteinTrackColor = Color(0xFFD4F0DD)
private val ProteinColor = Color(0xFF61BF80)

private enum class GoalCardView {
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
    val model = viewModel.model.collectAsStateWithLifecycle().value
    val gaugeSizePercent = viewModel.goalGaugeSizePercent.collectAsStateWithLifecycle().value

    var showTodayGoalDialog by remember { mutableStateOf(false) }

    if (model == null) {
        GoalsCardSkeleton(
            onClick = { onClick(homeState.selectedDate.toEpochDays()) },
            onLongClick = onLongClick,
            gaugeSizePercent = gaugeSizePercent,
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
            goalCardModeSwitchingEnabled = model.goalCardModeSwitchingEnabled,
            supplementalGoalsEnabled = model.supplementalGoalsEnabled,
            goalDisplayMode = model.goalDisplayMode,
            goalDisplaySummaries = model.goalDisplaySummaries,
            dietGoalDisplayModeEnabled = model.dietGoalDisplayModeEnabled,
            proteins = model.proteins,
            proteinsGoal = model.proteinsGoal,
            carbohydrates = model.carbohydrates,
            carbohydratesGoal = model.carbohydratesGoal,
            fats = model.fats,
            fatsGoal = model.fatsGoal,
            todayRemainingEnergy = model.todayRemainingEnergy,
            todayEnergyGoalEditable = model.todayEnergyGoalEditable,
            onTodayEnergyGoalClick = { showTodayGoalDialog = true },
            onClick = { onClick(homeState.selectedDate.toEpochDays()) },
            onLongClick = onLongClick,
            onSelectGoalDisplayMode = { viewModel.setGoalDisplayMode(it.toSettingsGoalDisplayMode()) },
            gaugeSizePercent = gaugeSizePercent,
            modifier = modifier,
        )
    }

    if (showTodayGoalDialog && model != null) {
        val baseGoal =
            model.goalDisplaySummaries
                .firstOrNull { it.mode == GoalDisplayMode.Normal }
                ?.energyGoal ?: model.energyGoal
        TodayEnergyGoalDialog(
            baseGoalKcal = baseGoal,
            currentReductionKcal = model.todayEnergyGoalReductionKcal,
            onDismiss = { showTodayGoalDialog = false },
            onSave = {
                viewModel.setTodayEnergyGoalReduction(it)
                showTodayGoalDialog = false
            },
            onReset = {
                viewModel.resetTodayEnergyGoalReduction()
                showTodayGoalDialog = false
            },
        )
    }
}

@Composable
internal fun WeeklyGoalsCard(
    homeState: HomeState,
    onWeightClick: () -> Unit,
    onWeightEntryClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GoalsViewModel = koinViewModel(),
) {
    val weekModel = viewModel.weekModel.collectAsStateWithLifecycle().value
    val latestWeight = viewModel.latestWeight.collectAsStateWithLifecycle().value
    val chartEnabled = viewModel.weeklyChartEnabled.collectAsStateWithLifecycle().value
    val detailsStyle = viewModel.weeklyDetailsStyle.collectAsStateWithLifecycle().value

    if (weekModel == null) {
        WeeklyGoalsSkeleton(modifier = modifier)
    } else {
        WeeklyGoalsContent(
            model = weekModel,
            chartEnabled = chartEnabled,
            detailsStyle = detailsStyle,
            latestWeight = latestWeight,
            onWeightClick = onWeightClick,
            onWeightEntryClick = onWeightEntryClick,
            modifier = modifier,
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
    goalCardModeSwitchingEnabled: Boolean = true,
    supplementalGoalsEnabled: Boolean = true,
    dietGoalDisplayModeEnabled: Boolean = true,
    proteins: Int,
    proteinsGoal: Int,
    carbohydrates: Int,
    carbohydratesGoal: Int,
    fats: Int,
    fatsGoal: Int,
    todayRemainingEnergy: Int? = null,
    todayEnergyGoalEditable: Boolean = false,
    onTodayEnergyGoalClick: () -> Unit = {},
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSelectGoalDisplayMode: (GoalDisplayMode) -> Unit = {},
    gaugeSizePercent: Int = DEFAULT_GOAL_GAUGE_SIZE_PERCENT,
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
        if (goalCardModeSwitchingEnabled) {
            GoalDisplayModeButtons(
                goalCardView = displayedGoalCardView,
                availableGoalDisplayModes = availableGoalDisplayModes,
                dietGoalDisplayModeEnabled = dietGoalDisplayModeEnabled,
                onSelectGoalCardView = {
                    when (it) {
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
        }

        CaloriesOverview(
            energy = energy,
            burnedEnergy = burnedEnergy,
            burnedEnergyDelta = burnedEnergyDelta,
            netEnergy = netEnergy,
            energyGoal = energyGoal,
            showEnergyGoalValue = showEnergyGoalValue,
            goalDisplayMode =
                if (goalCardModeSwitchingEnabled) displayedGoalCardView.toGoalDisplayMode()
                else GoalDisplayMode.Normal,
            goalDisplaySummaries = summaries,
            ringGoalSummaries =
                if (supplementalGoalsEnabled) {
                    summaries.supplementalGoalSummaries(dietGoalDisplayModeEnabled)
                } else {
                    emptyList()
                },
            todayRemainingEnergy = todayRemainingEnergy,
            todayEnergyGoalEditable = todayEnergyGoalEditable,
            onTodayEnergyGoalClick = onTodayEnergyGoalClick,
            onClick = onClick,
            onLongClick = onLongClick,
            gaugeSizePercent = gaugeSizePercent,
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
    chartEnabled: Boolean,
    detailsStyle: WeeklyDetailsStyle,
    latestWeight: LatestWeightState,
    onWeightClick: () -> Unit,
    onWeightEntryClick: () -> Unit,
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
            if (model.days.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    WeeklyGoalsHeader(model)
                    Column(
                        modifier =
                            Modifier.fillMaxWidth()
                                .padding(start = 24.dp, top = 18.dp, end = 24.dp, bottom = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        if (chartEnabled) {
                            WeeklyGoalsChart(days = model.days, today = model.today)
                        }
                        when (detailsStyle) {
                            WeeklyDetailsStyle.Table -> WeeklyDetailsTable(model.days)
                            WeeklyDetailsStyle.DifferenceBars ->
                                WeeklyDifferenceBars(days = model.days, today = model.today)
                        }
                        HorizontalDivider(color = GoalsTrackColor)
                        WeeklySummaryFooter(
                            model = model,
                            weightStatus = weeklyWeightStatus(latestWeight, model.today),
                            onWeightClick = onWeightClick,
                            onWeightEntryClick = onWeightEntryClick,
                        )
                    }
                }
            }
        }
    }
}

/** The top of the card with the week's energy and its goal. */
@Composable
internal fun WeeklyGoalsHeader(model: WeekSummaryModel, modifier: Modifier = Modifier) {
    val percent = goalReachedPercentage(model.totalEnergy, model.totalGoal)
    val percentDescription = stringResource(Res.string.goal_reached_percentage, percent)
    Row(
        modifier =
            modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier =
                Modifier.weight(1f).clearAndSetSemantics {
                    contentDescription = percentDescription
                },
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WeeklyGoalRing(
                progress = model.totalEnergy.toFloat() / model.totalGoal.coerceAtLeast(1)
            )
            Column {
                Text(
                    text = "$percent %",
                    color = GoalsTextColor,
                    style =
                        MaterialTheme.typography.titleLarge.copy(
                            fontFamily = interNumberFontFamily(),
                            fontWeight = FontWeight.Normal,
                            fontSize = 22.sp,
                            lineHeight = 26.sp,
                        ),
                    maxLines = 1,
                )
                Text(
                    text = stringResource(Res.string.weekly_of_goal),
                    color = GoalsMutedTextColor,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
        }
        if (model.days.isNotEmpty()) {
            WeeklySumSoFar(sum = model.days.sumOf { it.difference })
        }
    }
}

/**
 * The energy of the days up to today as a share of their goals, drawn like the day's ring: blue up
 * to the goal, then a red arc for what lies above it.
 */
@Composable
private fun WeeklyGoalRing(progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(WeeklyRingSize)) {
        val stroke = WeeklyRingStroke.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(inset, inset)
        fun arc(color: Color, sweep: Float) =
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        drawCircle(
            color = GoalsTrackColor,
            radius = (size.minDimension - stroke) / 2,
            style = Stroke(width = stroke),
        )
        val within = progress.coerceIn(0f, 1f)
        val overflow = (progress - 1f).coerceIn(0f, 1f)
        if (within > 0f) arc(GoalsProgressColor, within * 360f)
        if (overflow > 0f) arc(WeeklyColumnOverBarColor, overflow * 360f)
    }
}

/** The differences of the days up to today, summed: the week's exceeded or remaining energy. */
@Composable
private fun WeeklySumSoFar(sum: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(top = 2.dp).semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            text = sum.signedGroupedDigits(),
            color =
                when {
                    sum > 0 -> WeeklyOverGoalTextColor
                    sum < 0 -> DietGoalReachedTextColor
                    else -> GoalsTextColor
                },
            style =
                MaterialTheme.typography.titleMedium.copy(
                    fontFamily = interNumberFontFamily(),
                    fontWeight = FontWeight.Medium,
                    fontSize = 18.sp,
                    fontFeatureSettings = "tnum",
                ),
            maxLines = 1,
        )
        Text(
            text =
                stringResource(
                    when {
                        sum > 0 -> Res.string.weekly_sum_over_goal_so_far
                        sum < 0 -> Res.string.weekly_sum_under_goal_so_far
                        else -> Res.string.weekly_sum_on_goal_so_far
                    }
                ),
            color = GoalsMutedTextColor,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

@Composable
internal fun WeeklyGoalsChart(
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
    val valueHeight =
        if (day.energy > 0) (day.energy.toFloat() / max).coerceIn(0.03f, 1f) else 0f
    val goalHeight = if (day.goal > 0) (day.goal.toFloat() / max).coerceIn(0.03f, 1f) else 0f
    val overflow = day.energy > day.goal && day.goal > 0
    val barMaxHeight = 126.dp
    val overflowSegmentGap = 2.dp
    val targetCaloriesColor = FatColor.copy(alpha = 0.24f)

    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .height(barMaxHeight + 24.dp)
                    .padding(horizontal = 2.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier =
                    Modifier.fillMaxWidth().height(barMaxHeight).align(Alignment.BottomCenter)
            ) {
                if (goalHeight > 0f) {
                    Box(
                        modifier =
                            Modifier.fillMaxWidth()
                                .fillMaxHeight(goalHeight)
                                .align(Alignment.BottomCenter)
                                .clip(RoundedCornerShape(6.dp))
                                .background(targetCaloriesColor)
                    )
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                BasicText(
                    text = day.energy.toString().groupDigits(),
                    style =
                        MaterialTheme.typography.labelMedium.copy(
                            color = GoalsTextColor,
                            fontFamily = interNumberFontFamily(),
                            textAlign = TextAlign.Center,
                        ),
                    maxLines = 1,
                    softWrap = false,
                    autoSize =
                        TextAutoSize.StepBased(
                            minFontSize = 9.sp,
                            maxFontSize = MaterialTheme.typography.labelMedium.fontSize,
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth().height(barMaxHeight * valueHeight)) {
                    if (overflow) {
                        val goalFraction = (day.goal.toFloat() / day.energy).coerceIn(0f, 1f)
                        Column(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .weight(1f - goalFraction)
                                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                        .background(WeeklyOverGoalBarColor)
                            )
                            Spacer(Modifier.height(overflowSegmentGap))
                            Box(
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .weight(goalFraction)
                                        .clip(
                                            RoundedCornerShape(
                                                bottomStart = 6.dp,
                                                bottomEnd = 6.dp,
                                            )
                                        )
                                        .background(FatColor)
                            )
                        }
                    } else {
                        Box(
                            modifier =
                                Modifier.fillMaxSize()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(FatColor)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                color = GoalsTextColor,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
            if (day.locked) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = stringResource(Res.string.locked_day_status),
                    tint = GoalsTextColor,
                    modifier = Modifier.padding(start = 2.dp).size(11.dp),
                )
            }
        }
    }
}

@Composable
internal fun WeeklyDetailsTable(days: List<WeekDaySummaryModel>, modifier: Modifier = Modifier) {
    val dateFormatter = LocalDateFormatter.current
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                difference = day.difference.toString().groupDigits().replace('-', '\u2212'),
                percent = day.percent.toString(),
                locked = day.locked,
                dietGoalReached = day.dietGoalReached,
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
    locked: Boolean = false,
    dietGoalReached: Boolean = false,
) {
    val numberFontFamily = interNumberFontFamily()
    val style =
        if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium
    val valueStyle =
        if (header) style.copy(textAlign = TextAlign.End)
        else style.copy(fontFamily = numberFontFamily, fontFeatureSettings = "tnum", textAlign = TextAlign.End)
    val weight = if (header) FontWeight.SemiBold else FontWeight.Normal
    val dietGoalReachedDescription = stringResource(Res.string.weekly_diet_goal_reached)
    val rowModifier =
        if (dietGoalReached) {
            modifier
                .background(DietGoalReachedRowColor, RoundedCornerShape(8.dp))
                .semantics(mergeDescendants = true) { stateDescription = dietGoalReachedDescription }
        } else {
            modifier
        }

    Row(
        modifier = rowModifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(modifier = Modifier.weight(0.65f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                day,
                style = style,
                fontWeight = FontWeight.SemiBold,
                color = if (dietGoalReached) DietGoalReachedTextColor else Color.Unspecified,
            )
            if (locked) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = stringResource(Res.string.locked_day_status),
                    modifier = Modifier.padding(start = 2.dp).size(12.dp),
                )
            }
        }
        Text(goal, modifier = Modifier.weight(1f), style = valueStyle, fontWeight = weight)
        Text(soFar, modifier = Modifier.weight(1f), style = valueStyle, fontWeight = weight)
        Text(
            difference,
            modifier = Modifier.weight(1f),
            style = valueStyle,
            fontWeight = weight,
            maxLines = 1,
            softWrap = false,
        )
        Text(percent, modifier = Modifier.weight(0.9f), style = valueStyle, fontWeight = weight)
    }
}

/**
 * The week as seven narrow columns around a dashed goal line: a day above its goal grows a bar
 * upward, a day below it downward, each with its signed difference at the bar's end. All days
 * share one scale. Days still ahead are a dot on the line. A day's name is green when the diet
 * goal was reached, bold for today and muted otherwise; the sum of the shown days, which only run
 * up to today, is in [WeeklyGoalsHeader].
 */
@Composable
internal fun WeeklyDifferenceBars(
    days: List<WeekDaySummaryModel>,
    modifier: Modifier = Modifier,
    today: LocalDate? = null,
) {
    val dateFormatter = LocalDateFormatter.current
    val maxDifference = (days.maxOfOrNull { abs(it.difference) } ?: 0).coerceAtLeast(1)
    val firstDate = days.firstOrNull()?.date
    Row(
        modifier =
            modifier.fillMaxWidth().drawBehind {
                val y = (WeeklyColumnOverHeight + WeeklyColumnLineHeight / 2).toPx()
                drawLine(
                    color = WeeklyGoalLineColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect =
                        PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                )
            }
    ) {
        repeat(7) { index ->
            val day = days.getOrNull(index)
            val date = day?.date ?: firstDate?.plus(index, DateTimeUnit.DAY)
            WeeklyDifferenceColumn(
                day = day,
                label = date?.weekDayLabel(dateFormatter.weekDayNamesShort).orEmpty(),
                isToday = date != null && date == today,
                fraction = day?.let { abs(it.difference).toFloat() / maxDifference } ?: 0f,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** One day of [WeeklyDifferenceBars]; [day] is null for a day still ahead. */
@Composable
private fun WeeklyDifferenceColumn(
    day: WeekDaySummaryModel?,
    label: String,
    isToday: Boolean,
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    val difference = day?.difference ?: 0
    val over = day != null && difference > 0
    val under = day != null && difference < 0
    val barLength = WeeklyColumnMaxBarLength * fraction.coerceIn(0.05f, 1f)
    val barRound = WeeklyColumnBarWidth / 2
    val labelColor =
        when {
            day == null -> WeeklyFutureDayColor
            day.dietGoalReached -> DietGoalReachedTextColor
            isToday -> GoalsTextColor
            else -> GoalsMutedTextColor
        }
    val dietGoalReachedDescription = stringResource(Res.string.weekly_diet_goal_reached)

    Column(
        modifier =
            modifier.semantics(mergeDescendants = true) {
                if (day?.dietGoalReached == true) {
                    stateDescription = dietGoalReachedDescription
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().height(WeeklyColumnOverHeight),
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (over) {
                WeeklyColumnValue(difference = difference, color = WeeklyOverGoalTextColor)
                Box(
                    modifier =
                        Modifier.size(WeeklyColumnBarWidth, barLength)
                            .clip(
                                RoundedCornerShape(
                                    topStart = barRound,
                                    topEnd = barRound,
                                    bottomStart = 2.dp,
                                    bottomEnd = 2.dp,
                                )
                            )
                            .background(WeeklyColumnOverBarColor)
                )
            }
        }
        Box(
            modifier = Modifier.height(WeeklyColumnLineHeight),
            contentAlignment = Alignment.Center,
        ) {
            if (day == null) {
                Box(Modifier.size(7.dp).background(WeeklyFutureDotColor, CircleShape))
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().height(WeeklyColumnUnderHeight),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (under) {
                Box(
                    modifier =
                        Modifier.size(WeeklyColumnBarWidth, barLength)
                            .clip(
                                RoundedCornerShape(
                                    topStart = 2.dp,
                                    topEnd = 2.dp,
                                    bottomStart = barRound,
                                    bottomEnd = barRound,
                                )
                            )
                            .background(WeeklyColumnUnderBarColor)
                )
                WeeklyColumnValue(difference = difference, color = DietGoalReachedTextColor)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                color = labelColor,
                maxLines = 1,
            )
            if (day?.locked == true) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = stringResource(Res.string.locked_day_status),
                    tint = WeeklyFutureDayColor,
                    modifier = Modifier.padding(start = 1.dp).size(9.dp),
                )
            }
        }
    }
}

/** A column's signed difference; it shrinks rather than wrap when five digits do not fit. */
@Composable
private fun WeeklyColumnValue(difference: Int, color: Color) {
    BasicText(
        text = difference.signedGroupedDigits(),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 1.dp),
        style =
            TextStyle(
                color = color,
                fontSize = WeeklyColumnValueTextSize,
                fontFamily = interNumberFontFamily(),
                fontFeatureSettings = "tnum",
                textAlign = TextAlign.Center,
            ),
        maxLines = 1,
        softWrap = false,
        autoSize =
            TextAutoSize.StepBased(
                minFontSize = 7.sp,
                maxFontSize = WeeklyColumnValueTextSize,
                stepSize = 0.5.sp,
            ),
    )
}

private fun Int.signedGroupedDigits(): String =
    when {
        this > 0 -> "+"
        this < 0 -> "\u2212"
        else -> ""
    } + abs(this).toString().groupDigits()

private fun LocalDate.weekDayLabel(weekDayNamesShort: List<String>): String {
    val index = (dayOfWeek.isoDayNumber - 1).coerceIn(0, weekDayNamesShort.lastIndex)
    return weekDayNamesShort[index].trimEnd('.')
}

@Composable
internal fun WeeklySummaryFooter(
    model: WeekSummaryModel,
    weightStatus: WeeklyWeightStatus?,
    onWeightClick: () -> Unit,
    onWeightEntryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val remaining = model.totalGoal - model.totalEnergy
    val absoluteRemaining = abs(remaining)
    val estimatedWeightKg = (absoluteRemaining / 7700.0).formatKgEstimate()
    val weightChangeLabel =
        stringResource(
            if (remaining >= 0) Res.string.weekly_weight_lost
            else Res.string.weekly_weight_gained
        )

    WeeklyFooterWeightEstimate(
        icon = Icons.Filled.MonitorWeight,
        value = "$estimatedWeightKg kg",
        label = weightChangeLabel,
        weightStatus = weightStatus,
        onClick = onWeightClick,
        onWeightEntryClick = onWeightEntryClick,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun WeeklyFooterWeightEstimate(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    weightStatus: WeeklyWeightStatus?,
    onClick: () -> Unit,
    onWeightEntryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text =
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(value) }
            append(" ")
            append(label)
        }
    val staleLabel =
        when (weightStatus) {
            is WeeklyWeightStatus.Stale ->
                weightStatus.daysAgo?.let {
                    pluralStringResource(Res.plurals.weekly_weight_last_measured_days_ago, it, it)
                } ?: stringResource(Res.string.weekly_weight_not_measured)
            else -> null
        }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 40.dp).clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = GoalsTextColor,
                modifier = Modifier.size(22.dp).alpha(0.95f),
            )
            Spacer(Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = GoalsTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (staleLabel != null) {
                    Text(
                        text = staleLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = GoalsMutedTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (weightStatus is WeeklyWeightStatus.Recent) {
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${weightStatus.weightKg.formatKgMeasurement()} kg",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = GoalsTextColor,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Text(
                        text = measuredDaysAgoLabel(weightStatus.daysAgo),
                        style = MaterialTheme.typography.labelMedium,
                        color = GoalsMutedTextColor,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = GoalsMutedTextColor,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (weightStatus is WeeklyWeightStatus.Stale) {
            FilledTonalButton(
                onClick = onWeightEntryClick,
                colors =
                    ButtonDefaults.filledTonalButtonColors(
                        containerColor = GoalsTrackColor,
                        contentColor = GoalsProgressColor,
                    ),
                contentPadding = PaddingValues(start = 10.dp, end = 14.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(4.dp))
                Text(text = stringResource(Res.string.action_weigh_in), maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
private fun measuredDaysAgoLabel(daysAgo: Int): String =
    when (daysAgo) {
        0 -> stringResource(Res.string.weekly_weight_measured_today)
        1 -> stringResource(Res.string.weekly_weight_measured_yesterday)
        else -> pluralStringResource(Res.plurals.weekly_weight_measured_days_ago, daysAgo, daysAgo)
    }

@Composable
private fun CaloriesOverview(
    energy: Int,
    burnedEnergy: Int,
    burnedEnergyDelta: Int?,
    netEnergy: Int,
    energyGoal: Int,
    showEnergyGoalValue: Boolean,
    goalDisplayMode: GoalDisplayMode,
    goalDisplaySummaries: List<GoalDisplaySummaryModel>,
    ringGoalSummaries: List<GoalDisplaySummaryModel> = emptyList(),
    todayRemainingEnergy: Int? = null,
    todayEnergyGoalEditable: Boolean = false,
    onTodayEnergyGoalClick: () -> Unit = {},
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    gaugeSizePercent: Int,
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
    val goalZones =
        remember(currentSummary, ringGoalSummaries, netEnergy) {
            gaugeGoalZones(
                ringSummary = currentSummary,
                goalSummaries = ringGoalSummaries,
                netEnergy = netEnergy,
            )
        }

    CaloriesOverviewPageCard(
        energy = energy,
        burnedEnergy = burnedEnergy,
        burnedEnergyDelta = burnedEnergyDelta,
        netEnergy = netEnergy,
        summary = currentSummary,
        goalZones = goalZones,
        todayRemainingEnergy = todayRemainingEnergy.takeIf {
            currentSummary.mode == GoalDisplayMode.Normal
        },
        todayEnergyGoalEditable =
            todayEnergyGoalEditable && currentSummary.mode == GoalDisplayMode.Normal,
        onTodayEnergyGoalClick = onTodayEnergyGoalClick,
        onClick = onClick,
        onLongClick = onLongClick,
        gaugeSizePercent = gaugeSizePercent,
        modifier = modifier,
        footer = footer,
    )
}

/**
 * A supplemental goal (Optimized or Diet) that splits the energy ring into sections. The ring
 * keeps its own goal as the full sweep; [goalFraction] is this goal's share of it, and [fraction]
 * is where its marker sits, moved back from a marker it would crowd (see [spreadGaugeMarkers]).
 */
@Immutable
internal data class GaugeGoalZone(
    val mode: GoalDisplayMode,
    val goalFraction: Float,
    val fraction: Float,
    val remaining: Int,
) {
    val exceeded: Boolean
        get() = remaining < 0
}

/**
 * The supplemental goals that fit on the ring of [ringSummary], ordered along the ring. A goal at
 * or above the ring's own goal has no place on the ring, so it is left out.
 */
internal fun gaugeGoalZones(
    ringSummary: GoalDisplaySummaryModel,
    goalSummaries: List<GoalDisplaySummaryModel>,
    netEnergy: Int,
): List<GaugeGoalZone> {
    val ringGoal = ringSummary.percentageEnergyGoal
    if (ringSummary.energyGoal <= 0 || ringGoal <= 0) return emptyList()
    val goals =
        goalSummaries
            .filter { it.mode != ringSummary.mode && it.mode != GoalDisplayMode.Normal }
            .filter { it.energyGoal in 1 until ringGoal }
            .sortedBy { it.energyGoal }
    val goalFractions = goals.map { it.energyGoal.toFloat() / ringGoal }
    val markers = spreadGaugeMarkers(goalFractions)
    return goals.mapIndexed { index, goal ->
        GaugeGoalZone(
            mode = goal.mode,
            goalFraction = goalFractions[index],
            fraction = markers[index],
            remaining = goal.energyGoal - netEnergy,
        )
    }
}

private fun GoalDisplayMode.goalZoneColor(): Color =
    when (this) {
        GoalDisplayMode.Normal -> GoalsProgressColor
        GoalDisplayMode.Optimized -> OptimizedGoalZoneColor
        GoalDisplayMode.Diet -> DietGoalAccentColor
    }

/**
 * The unfilled ring under a goal section: its color faintly tinted onto the card. Optimized gets a
 * stronger blue of its own, since a faint tint of it is lost next to the normal track's blue.
 */
private fun GoalDisplayMode.goalZoneTrackColor(): Color =
    when (this) {
        GoalDisplayMode.Optimized -> OptimizedGoalZoneTrackColor
        else -> lerp(GoalsCardColor, goalZoneColor(), 0.22f)
    }

/**
 * Places each zone's value inside the ring, next to the marker at the zone's boundary. The label's
 * edge facing the ring keeps the same clearance whatever its size, so a wide four-digit value moves
 * further in instead of running into the ring.
 *
 * The [endLabel] belongs to the marker at the ring's end. It is centered in the ring's open bottom,
 * a little below the ring's ends, and gets only the width between their markers.
 */
@Composable
private fun GaugeLabels(
    zones: List<GaugeGoalZone>,
    diameter: Dp,
    modifier: Modifier = Modifier,
    endLabel: @Composable () -> Unit,
) {
    Layout(
        content = {
            zones.forEach { GaugeGoalZoneLabel(it) }
            endLabel()
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val radius = diameter.toPx() / 2f
        val endRadians = gaugeAngle(1f).toDouble() * PI / 180.0
        val endWidth =
            2f * radius * cos(endRadians).toFloat() - 2f * GaugeEndLabelGap.toPx()
        val placeables =
            measurables.mapIndexed { index, measurable ->
                if (index == measurables.lastIndex) {
                    measurable.measure(Constraints(maxWidth = endWidth.roundToInt().coerceAtLeast(0)))
                } else {
                    measurable.measure(Constraints())
                }
            }
        layout(constraints.maxWidth, constraints.maxHeight) {
            val centerX = constraints.maxWidth / 2f
            val centerY = GaugeStrokeWidth.toPx() / 2f + radius
            val clearance = GaugeZoneLabelClearance.toPx()

            val end = placeables.last()
            val endY = centerY + radius * sin(endRadians).toFloat()
            end.place(
                x = (centerX - end.width / 2f).roundToInt(),
                y = (endY + GaugeEndLabelDrop.toPx() - end.height / 2f).roundToInt(),
            )

            placeables.dropLast(1).forEachIndexed { index, placeable ->
                val radians = gaugeAngle(zones[index].fraction).toDouble() * PI / 180.0
                val cos = cos(radians).toFloat()
                val sin = sin(radians).toFloat()
                // Half the label's extent along the radius, from its center to the ring side.
                val halfExtent = abs(cos) * placeable.width / 2f + abs(sin) * placeable.height / 2f
                val labelRadius = radius - clearance - halfExtent
                val x = centerX + labelRadius * cos - placeable.width / 2f
                val y = centerY + labelRadius * sin - placeable.height / 2f
                placeable.place(x.roundToInt(), y.roundToInt())
            }
        }
    }
}

/** The remaining energy to a supplemental goal. */
@Composable
private fun GaugeGoalZoneLabel(zone: GaugeGoalZone) {
    val energyFormatter = LocalEnergyFormatter.current
    // A narrow no-break space keeps four-digit values compact and on one line.
    val value =
        energyFormatter
            .formatEnergy(abs(zone.remaining), withSuffix = false)
            .groupDigits()
            .replace(' ', ' ')
    val label =
        stringResource(
            if (zone.mode == GoalDisplayMode.Optimized) {
                Res.string.label_optimized
            } else {
                Res.string.label_diet
            }
        )
    val status = stringResource(if (zone.exceeded) Res.string.goal_too_much else Res.string.goal_left)
    val description = "$label: $value ${stringResource(Res.string.unit_kcal)} $status"

    GaugeValueLabel(
        value = value,
        description = description,
        exceeded = zone.exceeded,
        color = if (zone.exceeded) GoalsErrorColor else GoalsMutedTextColor,
        fontWeight = if (zone.exceeded) FontWeight.SemiBold else FontWeight.Medium,
    )
}

/**
 * A value on the ring. Above its goal it turns red and gets a warning sign instead of a plus, so
 * the overshoot does not rely on color alone.
 */
@Composable
private fun GaugeValueLabel(
    value: String,
    description: String,
    exceeded: Boolean,
    color: Color,
    fontWeight: FontWeight,
    fontSize: TextUnit = 12.sp,
    unit: String? = null,
) {
    Layout(
        content = {
            if (exceeded) {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = GoalsErrorColor,
                    modifier = Modifier.size(fontSize.value.dp),
                )
            }
            Text(
                text = value,
                color = color,
                style =
                    MaterialTheme.typography.labelMedium.copy(
                        fontFamily = interNumberFontFamily(),
                        fontSize = fontSize,
                        lineHeight = fontSize * 1.25f,
                    ),
                fontWeight = fontWeight,
                maxLines = 1,
            )
            if (unit != null) {
                Text(
                    text = unit,
                    modifier = Modifier.padding(start = 3.dp),
                    color = if (exceeded) GoalsErrorColor else GoalsMutedTextColor,
                    style =
                        MaterialTheme.typography.labelMedium.copy(
                            fontSize = 12.sp,
                            lineHeight = 15.sp,
                        ),
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                )
            }
        },
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
    ) { measurables, constraints ->
        val gap = 1.dp.roundToPx()
        val all = measurables.map { it.measure(Constraints()) }
        fun widthOf(items: List<Placeable>): Int = items.sumOf { it.width } + gap * (items.size - 1)
        // The unit is the first thing to go when the value does not fit, as with a large font.
        val shown =
            if (unit != null && widthOf(all) > constraints.maxWidth) all.dropLast(1) else all
        val valueIndex = if (exceeded) 1 else 0
        val valueText = all[valueIndex]
        val height = shown.maxOf { it.height }
        layout(widthOf(shown), height) {
            val valueY = (height - valueText.height) / 2
            var x = 0
            shown.forEachIndexed { index, placeable ->
                val y =
                    when {
                        index == valueIndex -> valueY
                        // The smaller unit shares the value's baseline.
                        index > valueIndex ->
                            valueY + valueText[FirstBaseline] - placeable[FirstBaseline]
                        else -> (height - placeable.height) / 2
                    }
                placeable.place(x, y)
                x += placeable.width + gap
            }
        }
    }
}

@Composable
private fun CaloriesOverviewPageCard(
    energy: Int,
    burnedEnergy: Int,
    burnedEnergyDelta: Int?,
    netEnergy: Int,
    summary: GoalDisplaySummaryModel,
    goalZones: List<GaugeGoalZone> = emptyList(),
    todayRemainingEnergy: Int? = null,
    todayEnergyGoalEditable: Boolean = false,
    onTodayEnergyGoalClick: () -> Unit = {},
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    gaugeSizePercent: Int,
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
            val gaugeSize = gaugeSize(maxWidth - horizontalPadding * 2, gaugeSizePercent)

            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .defaultMinSize(minHeight = 259.dp + gaugeSize.growth)
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
                    goalZones = goalZones,
                    todayRemainingEnergy = todayRemainingEnergy,
                    todayEnergyGoalEditable = todayEnergyGoalEditable,
                    onTodayEnergyGoalClick = onTodayEnergyGoalClick,
                    gaugeSizePercent = gaugeSizePercent,
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
    goalZones: List<GaugeGoalZone> = emptyList(),
    todayRemainingEnergy: Int? = null,
    todayEnergyGoalEditable: Boolean = false,
    onTodayEnergyGoalClick: () -> Unit = {},
    gaugeSizePercent: Int,
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
    val remainingLabel =
        stringResource(if (overflow) Res.string.goal_too_much else Res.string.goal_left)
    val todayRemainingOverflow = todayRemainingEnergy?.let { it < 0 } == true
    val todayRemainingText =
        todayRemainingEnergy?.let {
            val value = abs(it)
            val status =
                if (todayRemainingOverflow) {
                    " ${stringResource(Res.string.goal_too_much)}"
                } else {
                    ""
                }
            "${energyFormatter.formatEnergy(value, withSuffix = false).groupDigits()}$status"
        }

    BoxWithConstraints(
        modifier = modifier
    ) {
        val gaugeSize = gaugeSize(maxWidth, gaugeSizePercent)
        val gaugeDiameter = gaugeSize.diameter
        val compact = maxWidth < 320.dp
        val phoneWidth = maxWidth < 430.dp
        val caloriesHeight = (if (phoneWidth) 164.dp else 230.dp) + gaugeSize.growth
        // The side values move with the ring's size, so they stay level with it.
        val sideMetricTopPadding = (if (phoneWidth) 39.dp else 50.dp) * gaugeSize.scale
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
                        goalZones = goalZones,
                        exceeded = overflow,
                        diameter = gaugeDiameter,
                        supportingLabel =
                            todayRemainingText?.let {
                                stringResource(Res.string.goal_today_target)
                            },
                        supportingText = todayRemainingText,
                        supportingColor =
                            if (todayRemainingOverflow) GoalsErrorColor else GoalsMutedTextColor,
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
                            modifier =
                                Modifier.weight(1f)
                                    .clickable(
                                        enabled = todayEnergyGoalEditable,
                                        onClick = onTodayEnergyGoalClick,
                                    ),
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
                        goalZones = goalZones,
                        exceeded = overflow,
                        diameter = gaugeDiameter,
                        supportingLabel =
                            todayRemainingText?.let {
                                stringResource(Res.string.goal_today_target)
                            },
                        supportingText = todayRemainingText,
                        supportingColor =
                            if (todayRemainingOverflow) GoalsErrorColor else GoalsMutedTextColor,
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
                            modifier =
                                Modifier.fillMaxWidth()
                                    .clickable(
                                        enabled = todayEnergyGoalEditable,
                                        onClick = onTodayEnergyGoalClick,
                                    ),
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

internal fun List<GoalDisplaySummaryModel>.supplementalGoalSummaries(
    dietGoalDisplayModeEnabled: Boolean
): List<GoalDisplaySummaryModel> {
    val normalGoal = firstOrNull { it.mode == GoalDisplayMode.Normal }?.energyGoal
    return filter { summary ->
        when (summary.mode) {
            GoalDisplayMode.Normal -> false
            GoalDisplayMode.Optimized ->
                summary.showEnergyGoalValue &&
                    normalGoal != null &&
                    summary.energyGoal != normalGoal
            GoalDisplayMode.Diet -> summary.showEnergyGoalValue && dietGoalDisplayModeEnabled
        }
    }
}

internal fun GoalDisplayMode.availableOrNormal(
    availableGoalDisplayModes: List<GoalDisplayMode>
): GoalDisplayMode =
    if (this in availableGoalDisplayModes) {
        this
    } else {
        GoalDisplayMode.Normal
    }

internal fun GoalDisplayMode.goalDisplayModeAtOffset(
    availableGoalDisplayModes: List<GoalDisplayMode>,
    offset: Int,
): GoalDisplayMode {
    if (availableGoalDisplayModes.isEmpty()) return GoalDisplayMode.Normal
    val currentIndex = availableGoalDisplayModes.indexOf(this).takeIf { it >= 0 } ?: 0
    val targetIndex = (currentIndex + offset).mod(availableGoalDisplayModes.size)
    return availableGoalDisplayModes[targetIndex]
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
        GoalCardView.Normal -> GoalDisplayMode.Normal
        GoalCardView.Optimized -> GoalDisplayMode.Optimized
        GoalCardView.Diet -> GoalDisplayMode.Diet
    }

private fun GoalCardView.label(): String =
    when (this) {
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

/**
 * The ring's size on a card whose content is [width] wide. The ring is first fitted to the card and
 * then scaled by the user's [sizePercent], so a smaller setting still shrinks it at the lower
 * bound. [growth] is how much the scaling added, for the heights laid out around the ring.
 */
private class GaugeSize(val diameter: Dp, val growth: Dp, val scale: Float)

private fun gaugeSize(width: Dp, sizePercent: Int): GaugeSize {
    val fitted = (width * 0.48f).coerceIn(156.dp, 254.dp)
    val scale = sizePercent / 100f
    val diameter = fitted * scale
    return GaugeSize(diameter = diameter, growth = diameter - fitted, scale = scale)
}

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
    supportingColor: Color = GoalsMutedTextColor,
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
                color = supportingColor,
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
    exceeded: Boolean,
    diameter: Dp,
    modifier: Modifier = Modifier,
    goalZones: List<GaugeGoalZone> = emptyList(),
    supportingLabel: String? = null,
    supportingText: String? = null,
    supportingColor: Color = GoalsMutedTextColor,
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
            goalZones = goalZones,
            modifier = Modifier.size(width = diameter, height = diameter - 7.dp),
        )
        // The ring's own remaining energy is the last value on the ring, as it spans the full sweep.
        GaugeLabels(
            zones = goalZones,
            diameter = diameter,
            modifier = Modifier.matchParentSize(),
        ) {
            GaugeValueLabel(
                value = value.replace(' ', '\u202F'),
                description = "$value ${stringResource(Res.string.unit_kcal)} $label",
                exceeded = exceeded,
                color = if (exceeded) GoalsErrorColor else GoalsTextColor,
                fontWeight = FontWeight.SemiBold,
                // The card's main value, so it stands out from the goal values on the ring.
                fontSize = 16.sp,
                unit = stringResource(Res.string.unit_kcal),
            )
        }

        Column(
            modifier = Modifier.offset(y = diameter * 0.42f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (supportingLabel != null) {
                Text(
                    text = supportingLabel,
                    color = GoalsMutedTextColor,
                    style =
                        MaterialTheme.typography.labelMedium.copy(
                            fontSize = 12.sp,
                            lineHeight = 15.sp,
                        ),
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    modifier = Modifier.padding(top = 1.dp),
                    color = supportingColor,
                    style =
                        MaterialTheme.typography.labelMedium.copy(
                            fontFamily = numberFontFamily,
                            fontSize = 12.sp,
                            lineHeight = 15.sp,
                        ),
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private val GaugeStrokeWidth = 10.dp

/** Gap between the ring's center line and the nearest edge of a goal zone's value label. */
private val GaugeZoneLabelClearance = 10.dp
private val GaugeZoneMarkerRadius = 6.5.dp
private val GaugeZoneMarkerDotRadius = 3.5.dp

/** Room kept between each end of the ring and the remaining energy centered between them. */
private val GaugeEndLabelGap = 10.dp

/** How far the remaining energy's center sits below the ring's ends. */
private val GaugeEndLabelDrop = 3.dp
private const val GaugeGapAngle = 2.5f

/** The ring starts bottom left at 135° and sweeps 270° clockwise to the bottom right. */
private fun gaugeAngle(fraction: Float): Float = 135f + 270f * fraction

@Composable
private fun SemiCircleGauge(
    progress: Float,
    overflowProgress: Float,
    progressColor: Color,
    diameter: Dp,
    modifier: Modifier = Modifier,
    goalZones: List<GaugeGoalZone> = emptyList(),
) {
    Canvas(modifier = modifier) {
        if (goalZones.isNotEmpty()) {
            drawZonedGauge(
                progress = progress,
                overflowProgress = overflowProgress,
                progressColor = progressColor,
                diameter = diameter.toPx(),
                zones = goalZones,
            )
            return@Canvas
        }
        val strokeWidth = GaugeStrokeWidth.toPx()
        val arcDiameter = diameter.toPx()
        val topLeft = Offset(x = (size.width - arcDiameter) / 2f, y = strokeWidth / 2f)
        val arcSize = Size(width = arcDiameter, height = arcDiameter)

        // Above the goal the whole ring turns red instead of eating back into the progress.
        val overGoal = overflowProgress > 0f
        drawArc(
            color = if (overGoal) GoalsErrorColor else GoalsTrackColor,
            startAngle = gaugeAngle(0f),
            sweepAngle = 270f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
        val progressSweepAngle = 270f * progress.coerceIn(0f, 1f)
        if (!overGoal && progressSweepAngle > 0f) {
            drawArc(
                color = progressColor,
                startAngle = gaugeAngle(0f),
                sweepAngle = progressSweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
        drawGaugeMarker(
            center = Offset(size.width / 2f, strokeWidth / 2f + arcDiameter / 2f),
            radius = arcDiameter / 2f,
            fraction = 1f,
            color = progressColor,
        )
    }
}

/**
 * The same ring as [SemiCircleGauge], split into one section per goal zone and a final section up
 * to the ring's own goal. Each section is filled in its goal's color and separated by a small gap,
 * with a marker on each boundary.
 */
private fun DrawScope.drawZonedGauge(
    progress: Float,
    overflowProgress: Float,
    progressColor: Color,
    diameter: Float,
    zones: List<GaugeGoalZone>,
) {
    val strokeWidth = GaugeStrokeWidth.toPx()
    val topLeft = Offset(x = (size.width - diameter) / 2f, y = strokeWidth / 2f)
    val arcSize = Size(width = diameter, height = diameter)
    val center = Offset(topLeft.x + diameter / 2f, topLeft.y + diameter / 2f)
    val radius = diameter / 2f
    val halfGap = GaugeGapAngle / 2f

    fun arcPoint(angle: Float): Offset {
        val radians = angle.toDouble() * PI / 180.0
        return Offset(
            x = center.x + radius * cos(radians).toFloat(),
            y = center.y + radius * sin(radians).toFloat(),
        )
    }

    fun arc(color: Color, startAngle: Float, endAngle: Float) {
        if (endAngle <= startAngle) return
        drawArc(
            color = color,
            startAngle = startAngle,
            sweepAngle = endAngle - startAngle,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
        )
    }

    val boundaries = zones.map { it.fraction.coerceIn(0f, 1f) }
    val starts = listOf(0f) + boundaries
    val ends = boundaries + 1f
    val lastIndex = ends.lastIndex
    val colors = zones.map { it.mode.goalZoneColor() } + progressColor
    val trackColors = zones.map { it.mode.goalZoneTrackColor() } + GoalsTrackColor

    fun sectionStart(index: Int): Float = gaugeAngle(starts[index]) + if (index > 0) halfGap else 0f

    fun sectionEnd(index: Int): Float =
        gaugeAngle(ends[index]) - if (index < lastIndex) halfGap else 0f

    for (index in 0..lastIndex) {
        arc(trackColors[index], sectionStart(index), sectionEnd(index))
    }
    drawCircle(trackColors.first(), radius = strokeWidth / 2f, center = arcPoint(gaugeAngle(0f)))
    drawCircle(trackColors.last(), radius = strokeWidth / 2f, center = arcPoint(gaugeAngle(1f)))

    if (overflowProgress > 0f) {
        // Above the ring's own goal the whole ring turns red; the markers keep the sections.
        drawArc(
            color = GoalsErrorColor,
            startAngle = gaugeAngle(0f),
            sweepAngle = 270f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
    } else {
        val visibleProgress =
            gaugeRingPosition(
                progress = progress.coerceIn(0f, 1f),
                goalFractions = zones.map { it.goalFraction },
                markerFractions = boundaries,
            )
        if (visibleProgress > 0f) {
            drawCircle(colors.first(), radius = strokeWidth / 2f, center = arcPoint(gaugeAngle(0f)))
            for (index in 0..lastIndex) {
                if (visibleProgress <= starts[index]) break
                val end =
                    if (visibleProgress >= ends[index]) sectionEnd(index)
                    else gaugeAngle(visibleProgress)
                arc(colors[index], sectionStart(index), end)
            }
            val section = ends.indexOfFirst { visibleProgress <= it }.coerceAtLeast(0)
            drawCircle(
                color = colors[section],
                radius = strokeWidth / 2f,
                center = arcPoint(gaugeAngle(visibleProgress)),
            )
        }
    }

    // The markers keep their section's color; an exceeded goal shows in its red label instead.
    zones.forEach { zone ->
        drawGaugeMarker(center, radius, zone.fraction, zone.mode.goalZoneColor())
    }
    drawGaugeMarker(center, radius, 1f, progressColor)
}

/** A marker on the ring at [fraction] of its sweep, ringed in the card's color. */
private fun DrawScope.drawGaugeMarker(center: Offset, radius: Float, fraction: Float, color: Color) {
    val radians = gaugeAngle(fraction.coerceIn(0f, 1f)).toDouble() * PI / 180.0
    val point =
        Offset(
            x = center.x + radius * cos(radians).toFloat(),
            y = center.y + radius * sin(radians).toFloat(),
        )
    drawCircle(GoalsCardColor, radius = GaugeZoneMarkerRadius.toPx(), center = point)
    drawCircle(color = color, radius = GaugeZoneMarkerDotRadius.toPx(), center = point)
}

internal fun String.groupDigits(): String {
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

/** One decimal with a dot, matching [formatKgEstimate] on the same footer row. */
private fun Double.formatKgMeasurement(): String {
    val deciKg = (this * 10).roundToInt()
    return "${deciKg / 10}.${deciKg % 10}"
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
internal fun WeeklyGoalsSkeleton(
    modifier: Modifier = Modifier,
    shimmerEnabled: Boolean = true,
) {
    val shimmer =
        if (shimmerEnabled) rememberShimmer(shimmerBounds = ShimmerBounds.Window) else null

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
                        .defaultMinSize(minHeight = 342.dp)
                        .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                WeeklySkeletonBlock(
                    shimmer = shimmer,
                    modifier = Modifier.width(196.dp).height(32.dp).clip(RoundedCornerShape(8.dp)),
                )
                WeeklySkeletonBlock(
                    shimmer = shimmer,
                    modifier =
                        Modifier.fillMaxWidth().height(184.dp).clip(RoundedCornerShape(12.dp)),
                )
                WeeklySkeletonBlock(
                    shimmer = shimmer,
                    modifier = Modifier.width(112.dp).height(24.dp).clip(RoundedCornerShape(8.dp)),
                )
            }
        }
    }
}

@Composable
private fun WeeklySkeletonBlock(shimmer: Shimmer?, modifier: Modifier = Modifier) {
    if (shimmer == null) {
        Box(modifier = modifier.background(GoalsTrackColor))
    } else {
        SkeletonBlock(shimmer = shimmer, modifier = modifier)
    }
}

@Composable
internal fun GoalsCardSkeleton(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    gaugeSizePercent: Int = DEFAULT_GOAL_GAUGE_SIZE_PERCENT,
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
            val gaugeSize = gaugeSize(contentWidth, gaugeSizePercent)
            val caloriesHeight = (if (phoneWidth) 164.dp else 230.dp) + gaugeSize.growth
            val gaugeDiameter = gaugeSize.diameter

            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .defaultMinSize(minHeight = 259.dp + gaugeSize.growth)
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
                            Modifier.requiredWidth(125.dp * gaugeSize.scale)
                                .height(125.dp * gaugeSize.scale)
                                .clip(RoundedCornerShape(topStart = 120.dp, topEnd = 120.dp)),
                        )
                        Column(
                            modifier = Modifier.offset(y = 106.dp * gaugeSize.scale),
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

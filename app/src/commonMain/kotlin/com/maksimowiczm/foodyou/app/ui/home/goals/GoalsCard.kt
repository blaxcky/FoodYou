package com.maksimowiczm.foodyou.app.ui.home.goals

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Speed
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
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private val GoalsCardShape = RoundedCornerShape(24.dp)
private val GoalsCardColor = Color(0xFFFFFFFF)
private val GoalsTextColor = Color(0xFF202124)
private val GoalsMutedTextColor = Color(0xFF5F6368)
private val GoalsTrackColor = Color(0xFFE4EEF5)
private val GoalsProgressColor = Color(0xFF45AEE6)
private val GoalsErrorColor = Color(0xFFE25555)
private val OptimizedGoalAccentColor = GoalsProgressColor
private val DietGoalAccentColor = Color(0xFFC98A00)
private val FatTrackColor = Color(0xFFFFE5E5)
private val FatColor = Color(0xFFF4D5DC)
private val CarbsTrackColor = Color(0xFFFFF3DC)
private val CarbsColor = Color(0xFFF4E6C9)
private val ProteinTrackColor = Color(0xFFE3F7E9)
private val ProteinColor = Color(0xFFCFE6CD)

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
            dietGoalDisplayModeEnabled = model.dietGoalDisplayModeEnabled,
            proteins = model.proteins,
            proteinsGoal = model.proteinsGoal,
            carbohydrates = model.carbohydrates,
            carbohydratesGoal = model.carbohydratesGoal,
            fats = model.fats,
            fatsGoal = model.fatsGoal,
            onClick = { onClick(homeState.selectedDate.toEpochDays()) },
            onLongClick = onLongClick,
            onShowNextGoalDisplayMode = viewModel::showNextGoalDisplayMode,
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
internal fun GoalsCard(
    energy: Int,
    burnedEnergy: Int,
    burnedEnergyDelta: Int? = null,
    netEnergy: Int,
    energyGoal: Int,
    showEnergyGoalValue: Boolean = true,
    goalDisplayMode: GoalDisplayMode = GoalDisplayMode.Normal,
    dietGoalDisplayModeEnabled: Boolean = true,
    proteins: Int,
    proteinsGoal: Int,
    carbohydrates: Int,
    carbohydratesGoal: Int,
    fats: Int,
    fatsGoal: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onShowNextGoalDisplayMode: () -> Unit = {},
    onSelectGoalDisplayMode: (GoalDisplayMode) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val goal = energyGoal.coerceAtLeast(1)
    val energyProgress = (netEnergy.toFloat() / goal).coerceIn(0f, 1f)
    val proteinsProgress = goalProgress(proteins, proteinsGoal)
    val carbsProgress = goalProgress(carbohydrates, carbohydratesGoal)
    val fatsProgress = goalProgress(fats, fatsGoal)

    Column(modifier = modifier) {
        GoalDisplayModeButtons(
            goalDisplayMode = goalDisplayMode,
            dietGoalDisplayModeEnabled = dietGoalDisplayModeEnabled,
            onSelectGoalDisplayMode = onSelectGoalDisplayMode,
            modifier =
                Modifier.fillMaxWidth()
                    .height(38.dp)
                    .padding(end = 14.dp),
        )

        Spacer(Modifier.height(4.dp))

        FoodYouHomeCard(
            modifier = Modifier.fillMaxWidth(),
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
                    CaloriesOverview(
                        energy = energy,
                        burnedEnergy = burnedEnergy,
                        burnedEnergyDelta = burnedEnergyDelta,
                        netEnergy = netEnergy,
                        energyGoal = energyGoal,
                        showEnergyGoalValue = showEnergyGoalValue,
                        onShowNextGoalDisplayMode = onShowNextGoalDisplayMode,
                        progress = energyProgress,
                        modifier = Modifier.fillMaxWidth(),
                    )

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
            }
        }
    }
}

private fun goalProgress(value: Int, goal: Int): Float =
    if (goal <= 0) {
        0f
    } else {
        (value.toFloat() / goal).coerceIn(0f, 1f)
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
    val goalHeight = (day.goal.toFloat() / max).coerceIn(0f, 1f)
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
            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .fillMaxHeight(valueHeight)
                        .clip(RoundedCornerShape(6.dp))
                        .background(FatColor)
            )
            if (overflow) {
                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .fillMaxHeight((valueHeight - goalHeight).coerceAtLeast(0.02f))
                            .align(Alignment.TopCenter)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(Color(0xFFC57484))
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
    onShowNextGoalDisplayMode: () -> Unit,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val energyFormatter = LocalEnergyFormatter.current
    val left = energyGoal - netEnergy
    val goal = energyGoal.coerceAtLeast(1)
    val reached = (netEnergy.toFloat() / goal * 100).roundToInt().coerceAtLeast(0)
    val overflow = left < 0
    val remainingValue = if (overflow) -left else left
    val valueColor = if (overflow) GoalsErrorColor else GoalsTextColor
    val remainingLabel =
        stringResource(if (overflow) Res.string.goal_too_much else Res.string.goal_left)

    BoxWithConstraints(
        modifier =
            modifier
                .detectGoalDisplayModeSwipe(onShowNextGoalDisplayMode)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Transparent)
                .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        val gaugeDiameter = (maxWidth * 0.48f).coerceIn(156.dp, 254.dp)
        val compact = maxWidth < 320.dp
        val phoneWidth = maxWidth < 430.dp
        val caloriesHeight = if (phoneWidth) 164.dp else 230.dp
        val sideMetricTopPadding = if (phoneWidth) 39.dp else 50.dp
        val secondaryMetricTopPadding = 18.dp

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
                            energyFormatter.formatEnergy(energy, withSuffix = false).groupDigits(),
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
                            energyFormatter.formatEnergy(burnedEnergy, withSuffix = false)
                                .groupDigits(),
                        deltaValue = burnedEnergyDelta,
                        label = stringResource(Res.string.goal_burned),
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    )
                    SideMetric(
                        value =
                            if (showEnergyGoalValue) {
                                energyFormatter.formatEnergy(energyGoal, withSuffix = false)
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
                    value = energyFormatter.formatEnergy(energy, withSuffix = false).groupDigits(),
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
                            energyFormatter.formatEnergy(burnedEnergy, withSuffix = false)
                                .groupDigits(),
                        deltaValue = burnedEnergyDelta,
                        label = stringResource(Res.string.goal_burned),
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    )
                    SideMetric(
                        value =
                            if (showEnergyGoalValue) {
                                energyFormatter.formatEnergy(energyGoal, withSuffix = false)
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

    }
}

@Composable
private fun GoalDisplayModeButtons(
    goalDisplayMode: GoalDisplayMode,
    dietGoalDisplayModeEnabled: Boolean,
    onSelectGoalDisplayMode: (GoalDisplayMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = goalDisplayMode.label(),
            modifier = Modifier.weight(1f).padding(start = 16.dp, end = 8.dp),
            color =
                if (goalDisplayMode == GoalDisplayMode.Normal) {
                    GoalsTextColor
                } else {
                    goalDisplayMode.accentColor()
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
            mode = GoalDisplayMode.Normal,
            selected = goalDisplayMode == GoalDisplayMode.Normal,
            contentDescription = stringResource(Res.string.goal_display_mode_normal),
            onClick = onSelectGoalDisplayMode,
        )
        GoalDisplayModeButton(
            mode = GoalDisplayMode.Optimized,
            selected = goalDisplayMode == GoalDisplayMode.Optimized,
            contentDescription = stringResource(Res.string.goal_display_mode_optimized),
            onClick = onSelectGoalDisplayMode,
        )
        GoalDisplayModeButton(
            mode = GoalDisplayMode.Diet,
            selected = goalDisplayMode == GoalDisplayMode.Diet,
            enabled = dietGoalDisplayModeEnabled,
            contentDescription = stringResource(Res.string.goal_display_mode_diet),
            onClick = onSelectGoalDisplayMode,
        )
    }
}

@Composable
private fun GoalDisplayModeButton(
    mode: GoalDisplayMode,
    selected: Boolean,
    contentDescription: String,
    onClick: (GoalDisplayMode) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val accentColor = mode.accentColor()
    val shape = RoundedCornerShape(50)
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(34.dp)
                .clip(shape)
                .background(if (selected) accentColor.copy(alpha = 0.12f) else Color.Transparent)
                .then(
                    if (selected) {
                        Modifier.border(1.dp, accentColor.copy(alpha = 0.7f), shape)
                    } else {
                        Modifier
                    }
                )
                .clickable(enabled = enabled) { onClick(mode) },
    ) {
        Icon(
            imageVector =
                when (mode) {
                    GoalDisplayMode.Normal -> Icons.Filled.LocalFireDepartment
                    GoalDisplayMode.Optimized -> Icons.Filled.Speed
                    GoalDisplayMode.Diet -> Icons.Filled.MonitorWeight
                },
            contentDescription = contentDescription,
            tint =
                when {
                    !enabled -> GoalsMutedTextColor.copy(alpha = 0.34f)
                    selected -> accentColor
                    else -> GoalsMutedTextColor.copy(alpha = 0.62f)
                },
                modifier =
                    Modifier.size(18.dp)
                        .alpha(if (enabled) 1f else 0.54f),
        )
    }
}

private fun GoalDisplayMode.toSettingsGoalDisplayMode(): SettingsGoalDisplayMode =
    when (this) {
        GoalDisplayMode.Normal -> SettingsGoalDisplayMode.Normal
        GoalDisplayMode.Optimized -> SettingsGoalDisplayMode.Optimized
        GoalDisplayMode.Diet -> SettingsGoalDisplayMode.Diet
    }

private fun GoalDisplayMode.label(): String =
    when (this) {
        GoalDisplayMode.Normal -> "Normal"
        GoalDisplayMode.Optimized -> "Optimiert"
        GoalDisplayMode.Diet -> "Diät"
    }

private fun GoalDisplayMode.accentColor(): Color =
    when (this) {
        GoalDisplayMode.Normal -> GoalsProgressColor
        GoalDisplayMode.Optimized -> OptimizedGoalAccentColor
        GoalDisplayMode.Diet -> DietGoalAccentColor
    }

private fun Modifier.detectGoalDisplayModeSwipe(onSwipeDown: () -> Unit): Modifier =
    pointerInput(onSwipeDown) {
        val threshold = 48.dp.toPx()
        var totalX = 0f
        var totalY = 0f
        var toggled = false

        detectDragGestures(
            onDragStart = {
                totalX = 0f
                totalY = 0f
                toggled = false
            },
            onDrag = { _, dragAmount ->
                totalX += dragAmount.x
                totalY += dragAmount.y

                if (!toggled && totalY > threshold && totalY > abs(totalX) * 1.5f) {
                    toggled = true
                    onSwipeDown()
                }
            },
        )
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
    var deltaTextWidthPx by remember(delta) { mutableIntStateOf(0) }

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
                        val maxX = containerWidthPx - deltaTextWidthPx
                        IntOffset(
                            x = preferredX.coerceAtMost(maxX).coerceAtLeast(0),
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
                onTextLayout = { layout ->
                    deltaTextWidthPx =
                        ceil(layout.getLineRight(0) - layout.getLineLeft(0)).toInt()
                },
            )
        }
    }
}

@Composable
private fun GaugeMetric(
    value: String,
    label: String,
    progress: Float,
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
private fun SemiCircleGauge(progress: Float, diameter: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeWidth = 10.dp.toPx()
        val arcDiameter = diameter.toPx()
        val topLeft = Offset(x = (size.width - arcDiameter) / 2f, y = strokeWidth / 2f)
        val arcSize = Size(width = arcDiameter, height = arcDiameter)

        drawArc(
            color = GoalsTrackColor,
            startAngle = 135f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
        drawArc(
            color = GoalsProgressColor,
            startAngle = 135f,
            sweepAngle = 270f * progress.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
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
    modifier: Modifier = Modifier,
) {
    val barShape = RoundedCornerShape(50)
    Box(
        modifier =
            modifier
                .width(132.dp)
                .height(6.dp)
                .clip(barShape)
                .background(trackColor)
    ) {
        Box(
            modifier =
                Modifier.fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(barShape)
                    .background(if (overflow) GoalsErrorColor else color)
        )
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

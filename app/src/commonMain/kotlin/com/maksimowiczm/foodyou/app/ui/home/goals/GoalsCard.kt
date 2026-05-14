package com.maksimowiczm.foodyou.app.ui.home.goals

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.common.utility.LocalEnergyFormatter
import com.maksimowiczm.foodyou.app.ui.home.shared.FoodYouHomeCard
import com.maksimowiczm.foodyou.app.ui.home.shared.HomeState
import com.maksimowiczm.foodyou.common.compose.extension.toDp
import com.valentinilk.shimmer.Shimmer
import com.valentinilk.shimmer.shimmer
import foodyou.app.generated.resources.Res
import foodyou.app.generated.resources.goal_burned
import foodyou.app.generated.resources.goal_carbs_short
import foodyou.app.generated.resources.goal_eaten
import foodyou.app.generated.resources.goal_fat
import foodyou.app.generated.resources.goal_goal
import foodyou.app.generated.resources.goal_left
import foodyou.app.generated.resources.goal_protein
import foodyou.app.generated.resources.goal_reached_percentage
import foodyou.app.generated.resources.unit_gram_short
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private val GoalsCardShape = RoundedCornerShape(24.dp)
private val GoalsCardColor = Color(0xFFFFFFFF)
private val GoalsTextColor = Color(0xFF202124)
private val GoalsMutedTextColor = Color(0xFF5F6368)
private val GoalsTrackColor = Color(0xFFE3EDF7)
private val GoalsProgressColor = Color(0xFF45AEE6)
private val GoalsErrorColor = Color(0xFFE25555)
private val MacroTrackColor = Color(0xFFF7F9FA)
private val FatColor = Color(0xFFF4D5DC)
private val CarbsColor = Color(0xFFF4E6C9)
private val ProteinColor = Color(0xFFCFE6CD)

@Composable
internal fun GoalsCard(
    homeState: HomeState,
    onClick: (epochDay: Long) -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GoalsViewModel = koinViewModel(),
) {
    LaunchedEffect(homeState.selectedDate) { viewModel.setDate(homeState.selectedDate) }

    val model = viewModel.model.collectAsStateWithLifecycle().value

    if (model == null) {
        GoalsCardSkeleton(
            shimmer = homeState.shimmer,
            onClick = { onClick(homeState.selectedDate.toEpochDays()) },
            onLongClick = onLongClick,
            modifier = modifier,
        )
    } else {
        GoalsCard(
            energy = model.energy,
            burnedEnergy = model.burnedEnergy,
            netEnergy = model.netEnergy,
            energyGoal = model.energyGoal,
            proteins = model.proteins,
            proteinsGoal = model.proteinsGoal,
            carbohydrates = model.carbohydrates,
            carbohydratesGoal = model.carbohydratesGoal,
            fats = model.fats,
            fatsGoal = model.fatsGoal,
            onClick = { onClick(homeState.selectedDate.toEpochDays()) },
            onLongClick = onLongClick,
            modifier = modifier,
        )
    }
}

@Composable
internal fun GoalsCard(
    energy: Int,
    burnedEnergy: Int,
    netEnergy: Int,
    energyGoal: Int,
    proteins: Int,
    proteinsGoal: Int,
    carbohydrates: Int,
    carbohydratesGoal: Int,
    fats: Int,
    fatsGoal: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val goal = energyGoal.coerceAtLeast(1)
    val energyProgress =
        animateFloatAsState(
                targetValue = (netEnergy.toFloat() / goal).coerceIn(0f, 1f),
                animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
            )
            .value
    val proteinsProgress = animatedGoalProgress(proteins, proteinsGoal)
    val carbsProgress = animatedGoalProgress(carbohydrates, carbohydratesGoal)
    val fatsProgress = animatedGoalProgress(fats, fatsGoal)

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
                        .defaultMinSize(minHeight = 387.dp)
                        .padding(
                            start = horizontalPadding,
                            top = 31.dp,
                            end = horizontalPadding,
                            bottom = 27.dp,
                        ),
            ) {
                CaloriesOverview(
                    energy = energy,
                    burnedEnergy = burnedEnergy,
                    netEnergy = netEnergy,
                    energyGoal = energyGoal,
                    progress = energyProgress,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MacroGoal(
                        label = stringResource(Res.string.goal_fat),
                        value = fats,
                        goal = fatsGoal,
                        progress = fatsProgress,
                        color = FatColor,
                        modifier = Modifier.weight(1f),
                    )
                    MacroGoal(
                        label = stringResource(Res.string.goal_carbs_short),
                        value = carbohydrates,
                        goal = carbohydratesGoal,
                        progress = carbsProgress,
                        color = CarbsColor,
                        modifier = Modifier.weight(1f),
                    )
                    MacroGoal(
                        label = stringResource(Res.string.goal_protein),
                        value = proteins,
                        goal = proteinsGoal,
                        progress = proteinsProgress,
                        color = ProteinColor,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun animatedGoalProgress(value: Int, goal: Int): Float =
    animateFloatAsState(
            targetValue =
                if (goal <= 0) {
                    0f
                } else {
                    (value.toFloat() / goal).coerceIn(0f, 1f)
                },
            animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
        )
        .value

@Composable
private fun CaloriesOverview(
    energy: Int,
    burnedEnergy: Int,
    netEnergy: Int,
    energyGoal: Int,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val energyFormatter = LocalEnergyFormatter.current
    val left = energyGoal - netEnergy
    val goal = energyGoal.coerceAtLeast(1)
    val reached = (netEnergy.toFloat() / goal * 100).roundToInt().coerceAtLeast(0)
    val valueColor = if (left < 0) GoalsErrorColor else GoalsTextColor

    BoxWithConstraints(modifier = modifier) {
        val gaugeDiameter = (maxWidth * 0.45f).coerceIn(156.dp, 178.dp)
        val compact = maxWidth < 320.dp

        if (compact) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                GaugeMetric(
                    value = energyFormatter.formatEnergy(left, withSuffix = false).groupDigits(),
                    label = stringResource(Res.string.goal_left),
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
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    )
                    SideMetric(
                        value =
                            energyFormatter.formatEnergy(burnedEnergy, withSuffix = false)
                                .groupDigits(),
                        label = stringResource(Res.string.goal_burned),
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    )
                    SideMetric(
                        value =
                            energyFormatter.formatEnergy(energyGoal, withSuffix = false)
                                .groupDigits(),
                        label = stringResource(Res.string.goal_goal),
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().height(247.dp),
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
                    modifier = Modifier.weight(1f).padding(top = 50.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                )
                GaugeMetric(
                    value = energyFormatter.formatEnergy(left, withSuffix = false).groupDigits(),
                    label = stringResource(Res.string.goal_left),
                    progress = progress,
                    valueColor = valueColor,
                    diameter = gaugeDiameter,
                )
                Column(
                    modifier = Modifier.weight(1f).padding(top = 50.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(40.dp),
                ) {
                    SideMetric(
                        value =
                            energyFormatter.formatEnergy(burnedEnergy, withSuffix = false)
                                .groupDigits(),
                        label = stringResource(Res.string.goal_burned),
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    )
                    SideMetric(
                        value =
                            energyFormatter.formatEnergy(energyGoal, withSuffix = false)
                                .groupDigits(),
                        label = stringResource(Res.string.goal_goal),
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    )
                }
            }
        }
    }
}

private fun Dp.coerceIn(minimumValue: Dp, maximumValue: Dp): Dp =
    coerceAtLeast(minimumValue).coerceAtMost(maximumValue)

@Composable
private fun SideMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    supportingValue: String? = null,
    supportingLabel: String? = null,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = value,
            modifier = Modifier.fillMaxWidth(),
            color = GoalsTextColor,
            style =
                MaterialTheme.typography.titleLarge.copy(
                    fontSize = 22.sp,
                    lineHeight = 26.sp,
                ),
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
        )
        Text(
            text = label,
            modifier = Modifier.fillMaxWidth(),
            color = GoalsTextColor,
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
            Spacer(Modifier.height(6.dp))
            Text(
                text = supportingValue,
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
private fun GaugeMetric(
    value: String,
    label: String,
    progress: Float,
    valueColor: Color,
    diameter: Dp,
    modifier: Modifier = Modifier,
) {
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
                        fontSize = 36.sp,
                        lineHeight = 40.sp,
                    ),
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
            Text(
                text = label,
                color = GoalsMutedTextColor,
                style =
                    MaterialTheme.typography.labelLarge.copy(
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
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
        val strokeWidth = 13.dp.toPx()
        val arcDiameter = diameter.toPx()
        val topLeft = Offset(x = (size.width - arcDiameter) / 2f, y = strokeWidth / 2f)
        val arcSize = Size(width = arcDiameter, height = arcDiameter)

        drawArc(
            color = GoalsTrackColor,
            startAngle = 140f,
            sweepAngle = 260f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
        drawArc(
            color = GoalsProgressColor,
            startAngle = 140f,
            sweepAngle = 260f * progress.coerceIn(0f, 1f),
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

@Composable
private fun MacroGoal(
    label: String,
    value: Int,
    goal: Int,
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val gramShort = stringResource(Res.string.unit_gram_short)
    val valueColor = if (goal > 0 && value > goal) GoalsErrorColor else GoalsTextColor

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.fillMaxWidth(),
            color = GoalsTextColor,
            style =
                MaterialTheme.typography.labelMedium.copy(
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                ),
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
        )
        MacroProgressBar(progress = progress, color = color, overflow = goal > 0 && value > goal)
        Text(
            text = "$value/$goal $gramShort",
            modifier = Modifier.fillMaxWidth(),
            color = valueColor,
            style =
                MaterialTheme.typography.labelMedium.copy(
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
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
    color: Color,
    overflow: Boolean,
    modifier: Modifier = Modifier,
) {
    val barShape = RoundedCornerShape(50)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(barShape)
                .background(MacroTrackColor)
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
    shimmer: Shimmer,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FoodYouHomeCard(
        modifier = modifier,
        color = GoalsCardColor,
        shape = GoalsCardShape,
        onClick = onClick,
        onLongClick = onLongClick,
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .defaultMinSize(minHeight = 387.dp)
                    .padding(start = 48.dp, top = 31.dp, end = 48.dp, bottom = 27.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(247.dp)) {
                Box(
                    modifier =
                        Modifier.align(Alignment.TopCenter).size(width = 254.dp, height = 235.dp),
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
                            Modifier.width(74.dp)
                                .height(42.dp),
                        )
                        Spacer(Modifier.height(5.dp))
                        SkeletonBlock(
                            shimmer,
                            Modifier.width(44.dp)
                                .height(24.dp),
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

            Spacer(Modifier.height(36.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                repeat(3) {
                    Column(
                        modifier = Modifier.width(133.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
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

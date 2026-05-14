package com.maksimowiczm.foodyou.app.ui.home.goals

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
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

private val GoalsCardShape = RoundedCornerShape(26.dp)
private val GoalsCardColor = Color(0xFFFFFFFF)
private val GoalsTextColor = Color(0xFF25272D)
private val GoalsMutedTextColor = Color(0xFF6F7681)
private val GoalsTrackColor = Color(0xFFE3EDF7)
private val GoalsProgressColor = Color(0xFF45AEE6)
private val GoalsErrorColor = Color(0xFFE25555)
private val MacroTrackColor = Color(0xFFF5F7F8)
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
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .defaultMinSize(minHeight = 388.dp)
                    .padding(start = 48.dp, top = 32.dp, end = 48.dp, bottom = 28.dp),
        ) {
            CaloriesOverview(
                energy = energy,
                burnedEnergy = burnedEnergy,
                netEnergy = netEnergy,
                energyGoal = energyGoal,
                progress = energyProgress,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(36.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
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

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SideMetric(
            value = energyFormatter.formatEnergy(energy, withSuffix = false),
            label = stringResource(Res.string.goal_eaten),
            supportingValue = "$reached %",
            supportingLabel =
                stringResource(Res.string.goal_reached_percentage, reached).substringAfter("% "),
            modifier = Modifier.weight(1f),
        )

        GaugeMetric(
            value = energyFormatter.formatEnergy(left, withSuffix = false),
            unit = energyFormatter.suffix(),
            label = stringResource(Res.string.goal_left),
            progress = progress,
            valueColor = valueColor,
            modifier = Modifier.weight(1.9f),
        )

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(30.dp),
        ) {
            SideMetric(
                value = energyFormatter.formatEnergy(burnedEnergy, withSuffix = false),
                label = stringResource(Res.string.goal_burned),
                horizontalAlignment = Alignment.End,
            )
            SideMetric(
                value = energyFormatter.formatEnergy(energyGoal, withSuffix = false),
                label = stringResource(Res.string.goal_goal),
                horizontalAlignment = Alignment.End,
            )
        }
    }
}

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
            color = GoalsTextColor,
            style =
                MaterialTheme.typography.titleLarge.copy(
                    fontSize = 24.sp,
                    lineHeight = 28.sp,
                ),
            fontWeight = FontWeight.Normal,
            maxLines = 1,
        )
        Text(
            text = label,
            color = GoalsTextColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
        )
        if (supportingValue != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = supportingValue,
                color = GoalsMutedTextColor,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 15.sp),
                fontWeight = FontWeight.Normal,
                maxLines = 1,
            )
        }
        if (supportingLabel != null) {
            Text(
                text = supportingLabel,
                color = GoalsMutedTextColor,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 15.sp),
                fontWeight = FontWeight.Normal,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun GaugeMetric(
    value: String,
    unit: String,
    label: String,
    progress: Float,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.height(220.dp), contentAlignment = Alignment.TopCenter) {
        SemiCircleGauge(
            progress = progress,
            modifier = Modifier.requiredWidth(196.dp).height(168.dp),
        )

        Column(
            modifier = Modifier.padding(top = 58.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                color = valueColor,
                style =
                    MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 34.sp,
                        lineHeight = 38.sp,
                    ),
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
            Text(
                text = unit,
                color = GoalsMutedTextColor,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
            Text(
                text = label,
                color = GoalsMutedTextColor,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp),
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SemiCircleGauge(progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeWidth = 13.dp.toPx()
        val inset = strokeWidth / 2
        val arcWidth = size.width - strokeWidth
        val arcHeight = (size.height - strokeWidth) * 2.04f
        val topLeft = Offset(inset, inset)

        drawArc(
            color = GoalsTrackColor,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = topLeft,
            size = Size(width = arcWidth, height = arcHeight),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
        drawArc(
            color = GoalsProgressColor,
            startAngle = 180f,
            sweepAngle = 180f * progress.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = topLeft,
            size = Size(width = arcWidth, height = arcHeight),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
    }
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
            color = GoalsTextColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
        )
        MacroProgressBar(progress = progress, color = color, overflow = goal > 0 && value > goal)
        Text(
            text = "$value/$goal $gramShort",
            color = valueColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
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
                    .defaultMinSize(minHeight = 388.dp)
                    .padding(start = 48.dp, top = 32.dp, end = 48.dp, bottom = 28.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
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
                    SkeletonBlock(
                        shimmer,
                        Modifier.width(42.dp).height(MaterialTheme.typography.labelSmall.toDp()),
                    )
                }

                Box(
                    modifier = Modifier.weight(1.9f).height(220.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    SkeletonBlock(
                        shimmer,
                        Modifier.requiredWidth(196.dp)
                            .height(168.dp)
                            .clip(RoundedCornerShape(topStart = 120.dp, topEnd = 120.dp)),
                    )
                    Column(
                        modifier = Modifier.padding(top = 58.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        SkeletonBlock(
                            shimmer,
                            Modifier.width(74.dp)
                                .height(MaterialTheme.typography.headlineMedium.toDp()),
                        )
                        Spacer(Modifier.height(5.dp))
                        SkeletonBlock(
                            shimmer,
                            Modifier.width(34.dp)
                                .height(MaterialTheme.typography.labelMedium.toDp()),
                        )
                        Spacer(Modifier.height(5.dp))
                        SkeletonBlock(
                            shimmer,
                            Modifier.width(44.dp)
                                .height(MaterialTheme.typography.labelMedium.toDp()),
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(30.dp),
                ) {
                    SkeletonSideMetric(shimmer, Alignment.End)
                    SkeletonSideMetric(shimmer, Alignment.End)
                }
            }

            Spacer(Modifier.height(36.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                repeat(3) {
                    Column(
                        modifier = Modifier.weight(1f),
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

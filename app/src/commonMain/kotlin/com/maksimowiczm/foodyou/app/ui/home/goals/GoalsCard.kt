package com.maksimowiczm.foodyou.app.ui.home.goals

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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

private val GoalsCardShape = RoundedCornerShape(28.dp)
private val GoalsCardColor = Color(0xFFFFFFFF)
private val GoalsTextColor = Color(0xFF202126)
private val GoalsMutedTextColor = Color(0xFF8A8F98)
private val GoalsTrackColor = Color(0xFFDCEFF8)
private val GoalsProgressColor = Color(0xFF4CAFE2)
private val GoalsErrorColor = Color(0xFFE25555)
private val MacroTrackColor = Color(0xFFEFF3F6)
private val FatColor = Color(0xFFF2C99E)
private val CarbsColor = Color(0xFFA8D7EE)
private val ProteinColor = Color(0xFFB9DDB7)

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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            CaloriesOverview(
                energy = energy,
                burnedEnergy = burnedEnergy,
                netEnergy = netEnergy,
                energyGoal = energyGoal,
                progress = energyProgress,
                modifier = Modifier.fillMaxWidth(),
            )

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
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SideMetric(
            value = energyFormatter.formatEnergy(energy, withSuffix = false),
            label = stringResource(Res.string.goal_eaten),
            supporting = stringResource(Res.string.goal_reached_percentage, reached),
            modifier = Modifier.weight(1f),
        )

        GaugeMetric(
            value = energyFormatter.formatEnergy(left, withSuffix = false),
            unit = energyFormatter.suffix(),
            label = stringResource(Res.string.goal_left),
            progress = progress,
            valueColor = valueColor,
            modifier = Modifier.weight(1.45f),
        )

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(14.dp),
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
    supporting: String? = null,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value,
            color = GoalsTextColor,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Text(
            text = label,
            color = GoalsMutedTextColor,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
        if (supporting != null) {
            Text(
                text = supporting,
                color = GoalsMutedTextColor,
                style = MaterialTheme.typography.labelSmall,
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
    Box(modifier = modifier.height(104.dp), contentAlignment = Alignment.BottomCenter) {
        SemiCircleGauge(progress = progress, modifier = Modifier.fillMaxWidth().height(74.dp))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value,
                color = valueColor,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
            Text(
                text = unit,
                color = GoalsMutedTextColor,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
            Text(
                text = label,
                color = GoalsMutedTextColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SemiCircleGauge(progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeWidth = 9.dp.toPx()
        val inset = strokeWidth / 2
        val arcSize = Size(width = size.width - strokeWidth, height = (size.height - inset) * 2)
        val topLeft = Offset(inset, inset)

        drawArc(
            color = GoalsTrackColor,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
        drawArc(
            color = GoalsProgressColor,
            startAngle = 180f,
            sweepAngle = 180f * progress.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
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

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            text = label,
            color = GoalsMutedTextColor,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
        MacroProgressBar(progress = progress, color = color, overflow = goal > 0 && value > goal)
        Text(
            text = "$value/$goal $gramShort",
            color = valueColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
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
                .height(7.dp)
                .clip(barShape)
                .background(MacroTrackColor)
    ) {
        Box(
            modifier =
                Modifier.fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(7.dp)
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
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
                    modifier = Modifier.weight(1.45f).height(104.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    SkeletonBlock(
                        shimmer,
                        Modifier.fillMaxWidth()
                            .height(74.dp)
                            .clip(RoundedCornerShape(topStart = 80.dp, topEnd = 80.dp)),
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    SkeletonSideMetric(shimmer, Alignment.End)
                    SkeletonSideMetric(shimmer, Alignment.End)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                repeat(3) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        SkeletonBlock(
                            shimmer,
                            Modifier.width(48.dp)
                                .height(MaterialTheme.typography.labelMedium.toDp()),
                        )
                        SkeletonBlock(
                            shimmer,
                            Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(50)),
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

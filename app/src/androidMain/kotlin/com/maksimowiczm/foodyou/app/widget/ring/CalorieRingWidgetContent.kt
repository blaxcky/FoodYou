package com.maksimowiczm.foodyou.app.widget.ring

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.maksimowiczm.foodyou.R
import com.maksimowiczm.foodyou.app.widget.CalorieWidgetModel
import com.maksimowiczm.foodyou.app.widget.calorieWidgetLaunchIntent
import com.maksimowiczm.foodyou.app.widget.calorieWidgetProgress
import com.maksimowiczm.foodyou.app.widget.formatWidgetDate
import com.maksimowiczm.foodyou.app.widget.formatWidgetNumber
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
internal fun CalorieRingWidgetContent(model: CalorieWidgetModel) {
    val context = LocalContext.current
    val spec = calorieRingLayoutSpec(LocalSize.current)

    Box(
        modifier =
            GlanceModifier.fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .appWidgetBackground()
                .widgetCornerRadius()
                .clickable(actionStartActivity(calorieWidgetLaunchIntent(context)))
                .padding(horizontal = spec.horizontalPadding, vertical = spec.padding)
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            HeaderRow(context, model, spec)
            Spacer(modifier = GlanceModifier.height(CalorieRingWidgetSizes.HeaderSpacing))
            HeroRow(context, model, spec, modifier = GlanceModifier.fillMaxWidth().defaultWeight())
            if (spec.goalRows == GoalRowsMode.Inline) {
                Spacer(modifier = GlanceModifier.height(6.dp))
                InlineGoalsRow(context, model, spec)
            }
            if (spec.goalRows == GoalRowsMode.Bars) {
                Spacer(modifier = GlanceModifier.height(6.dp))
                GoalBarRow(
                    context = context,
                    spec = spec,
                    label = context.getString(R.string.widget_calories_optimized),
                    goalKcal = model.optimizedGoalKcal,
                    leftKcal = model.optimizedLeftKcal,
                    netKcal = model.netKcal,
                    disabledText = context.getString(R.string.widget_calories_placeholder),
                )
                Spacer(modifier = GlanceModifier.height(2.dp))
                GoalBarRow(
                    context = context,
                    spec = spec,
                    label = context.getString(R.string.widget_calories_diet),
                    goalKcal = model.dietGoalKcal,
                    leftKcal = model.dietLeftKcal,
                    netKcal = model.netKcal,
                    disabledText = context.getString(R.string.widget_calories_diet_disabled),
                )
            }
        }
    }
}

private fun GlanceModifier.widgetCornerRadius(): GlanceModifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        cornerRadius(android.R.dimen.system_app_widget_background_radius)
    } else {
        cornerRadius(18.dp)
    }

@Composable
private fun HeaderRow(context: Context, model: CalorieWidgetModel, spec: CalorieRingLayoutSpec) {
    val steps = context.formatWidgetNumber(model.countedSteps)
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(spec.headerHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_widget_calendar),
            contentDescription = null,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
            modifier = GlanceModifier.size(spec.headerHeight - 2.dp),
        )
        Spacer(modifier = GlanceModifier.width(6.dp))
        Text(
            text = context.formatWidgetDate(model.date),
            style =
                TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = spec.headerTextSize,
                    fontWeight = FontWeight.Medium,
                ),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        Image(
            provider = ImageProvider(R.drawable.ic_widget_walk),
            contentDescription = null,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
            modifier = GlanceModifier.size(spec.headerHeight - 4.dp),
        )
        Spacer(modifier = GlanceModifier.width(4.dp))
        Text(
            text = steps,
            style =
                TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = spec.headerTextSize,
                    fontWeight = FontWeight.Medium,
                ),
            maxLines = 1,
            modifier =
                GlanceModifier.semantics {
                    contentDescription =
                        context.getString(R.string.widget_calories_steps_format, steps)
                },
        )
    }
}

@Composable
private fun HeroRow(
    context: Context,
    model: CalorieWidgetModel,
    spec: CalorieRingLayoutSpec,
    modifier: GlanceModifier = GlanceModifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        RingWithCenter(context, model, spec)
        Spacer(modifier = GlanceModifier.width(12.dp))
        Row(
            modifier = GlanceModifier.defaultWeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EatenMetric(context, model, spec, GlanceModifier.defaultWeight())
            BurnedMetric(context, model, spec, GlanceModifier.defaultWeight())
        }
    }
}

@Composable
private fun EatenMetric(
    context: Context,
    model: CalorieWidgetModel,
    spec: CalorieRingLayoutSpec,
    modifier: GlanceModifier,
) =
    SideMetric(
        context = context,
        spec = spec,
        modifier = modifier,
        label = context.getString(R.string.widget_calories_eaten),
        value = model.eatenKcal,
        color = GlanceTheme.colors.primary,
    )

@Composable
private fun BurnedMetric(
    context: Context,
    model: CalorieWidgetModel,
    spec: CalorieRingLayoutSpec,
    modifier: GlanceModifier,
) =
    SideMetric(
        context = context,
        spec = spec,
        modifier = modifier,
        label = context.getString(R.string.widget_calories_burned),
        value = model.burnedKcal,
        color = GlanceTheme.colors.tertiary,
    )

@Composable
private fun InlineGoalsRow(context: Context, model: CalorieWidgetModel, spec: CalorieRingLayoutSpec) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(spec.goalRowHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InlineGoal(
            context = context,
            spec = spec,
            label = context.getString(R.string.widget_calories_optimized),
            leftKcal = model.optimizedLeftKcal,
            disabledText = context.getString(R.string.widget_calories_placeholder),
        )
        Spacer(modifier = GlanceModifier.width(16.dp))
        InlineGoal(
            context = context,
            spec = spec,
            label = context.getString(R.string.widget_calories_diet),
            leftKcal = model.dietLeftKcal,
            disabledText = context.getString(R.string.widget_calories_diet_disabled),
        )
    }
}

@Composable
private fun InlineGoal(
    context: Context,
    spec: CalorieRingLayoutSpec,
    label: String,
    leftKcal: Int?,
    disabledText: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style =
                TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = spec.goalTextSize),
            maxLines = 1,
        )
        Spacer(modifier = GlanceModifier.width(6.dp))
        if (leftKcal == null) {
            Text(
                text = disabledText,
                style =
                    TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = spec.goalTextSize,
                    ),
                maxLines = 1,
            )
        } else {
            GoalValue(context, spec, leftKcal)
        }
    }
}

@Composable
private fun RingWithCenter(context: Context, model: CalorieWidgetModel, spec: CalorieRingLayoutSpec) {
    val ring = spec.ring
    val density = context.resources.displayMetrics.density
    val sizePx = min((ring.value * density).roundToInt(), WidgetRingRenderer.MAX_PIXELS)
    val strokePx = spec.stroke.value * sizePx / ring.value
    val progress = calorieWidgetProgress(model.netKcal, model.normalGoalKcal)
    val arcs = ringArcs(progress.progress, progress.overflow)
    val valueSize = spec.ringValueTextSize
    val labelSize = spec.ringLabelTextSize
    val over = model.normalLeftKcal < 0
    val eaten = context.formatWidgetNumber(model.eatenKcal)
    val goal = context.formatWidgetNumber(model.normalGoalKcal)
    val left = context.formatWidgetNumber(model.normalLeftKcal)
    val centerValue = context.formatWidgetNumber(abs(model.normalLeftKcal))
    val centerLabel =
        context.getString(
            if (over) R.string.widget_calories_over else R.string.widget_calories_left_lowercase
        )
    Box(
        modifier =
            GlanceModifier.size(ring).semantics {
                contentDescription =
                    context.getString(R.string.widget_calorie_ring_ring_description, eaten, goal, left)
            },
        contentAlignment = Alignment.Center,
    ) {
        RingArc(
            bitmap = WidgetRingRenderer.track(sizePx, strokePx),
            color = GlanceTheme.colors.primaryContainer,
            ring = ring,
        )
        if (arcs.hasProgress) {
            RingArc(
                bitmap =
                    WidgetRingRenderer.arc(
                        sizePx = sizePx,
                        strokePx = strokePx,
                        startDeg = arcs.progressStartDeg,
                        sweepDeg = arcs.progressSweepDeg,
                        roundStart = true,
                        roundEnd = !arcs.hasGap,
                    ),
                color = GlanceTheme.colors.primary,
                ring = ring,
            )
        }
        if (arcs.hasGap) {
            RingArc(
                bitmap =
                    WidgetRingRenderer.arc(
                        sizePx = sizePx,
                        strokePx = strokePx,
                        startDeg = arcs.gapStartDeg,
                        sweepDeg = arcs.gapSweepDeg,
                        roundStart = false,
                    ),
                color = GlanceTheme.colors.surface,
                ring = ring,
            )
        }
        if (arcs.hasOverflow) {
            RingArc(
                bitmap =
                    WidgetRingRenderer.arc(
                        sizePx = sizePx,
                        strokePx = strokePx,
                        startDeg = arcs.overflowStartDeg,
                        sweepDeg = arcs.overflowSweepDeg,
                        roundStart = !arcs.hasGap,
                        roundEnd = true,
                    ),
                color = GlanceTheme.colors.error,
                ring = ring,
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = centerValue,
                style =
                    TextStyle(
                        color = if (over) GlanceTheme.colors.error else GlanceTheme.colors.onSurface,
                        fontSize = valueSize,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    ),
                maxLines = 1,
            )
            Text(
                text = centerLabel,
                style =
                    TextStyle(
                        color = if (over) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant,
                        fontSize = labelSize,
                        textAlign = TextAlign.Center,
                    ),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun RingArc(bitmap: Bitmap, color: ColorProvider, ring: Dp) {
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = null,
        colorFilter = ColorFilter.tint(color),
        modifier = GlanceModifier.size(ring),
    )
}

@Composable
private fun SideMetric(
    context: Context,
    spec: CalorieRingLayoutSpec,
    label: String,
    value: Int,
    color: ColorProvider,
    modifier: GlanceModifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style =
                TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = spec.metricLabelTextSize,
                    textAlign = TextAlign.Center,
                ),
            maxLines = 1,
        )
        Text(
            text = context.formatWidgetNumber(value),
            style =
                TextStyle(
                    color = color,
                    fontSize = spec.metricValueTextSize,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
            maxLines = 1,
        )
        Text(
            text = context.getString(R.string.widget_calories_unit_kcal),
            style =
                TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = spec.metricLabelTextSize,
                    textAlign = TextAlign.Center,
                ),
            maxLines = 1,
        )
    }
}

/** Remaining kcal of a goal with a "left" suffix; exceeded goals turn red with an "over" suffix. */
@Composable
private fun GoalValue(context: Context, spec: CalorieRingLayoutSpec, leftKcal: Int) {
    val over = leftKcal < 0
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = context.formatWidgetNumber(abs(leftKcal)),
            style =
                TextStyle(
                    color = if (over) GlanceTheme.colors.error else GlanceTheme.colors.onSurface,
                    fontSize = spec.goalValueTextSize,
                    fontWeight = FontWeight.Bold,
                ),
            maxLines = 1,
        )
        Spacer(modifier = GlanceModifier.width(3.dp))
        Text(
            text =
                context.getString(
                    if (over) R.string.widget_calories_over_lowercase
                    else R.string.widget_calories_left_lowercase
                ),
            style =
                TextStyle(
                    color = if (over) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant,
                    fontSize = spec.goalTextSize,
                ),
            maxLines = 1,
        )
    }
}

@Composable
private fun GoalBarRow(
    context: Context,
    spec: CalorieRingLayoutSpec,
    label: String,
    goalKcal: Int?,
    leftKcal: Int?,
    netKcal: Int,
    disabledText: String,
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(spec.goalRowHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style =
                TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = spec.goalTextSize),
            maxLines = 1,
            modifier = GlanceModifier.width(66.dp),
        )
        if (goalKcal == null || leftKcal == null) {
            Text(
                text = disabledText,
                style =
                    TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = spec.goalTextSize,
                    ),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
        } else {
            val progress = calorieWidgetProgress(netKcal, goalKcal)
            val overflowing = progress.overflow > 0f
            Box(modifier = GlanceModifier.defaultWeight().height(8.dp).cornerRadius(4.dp)) {
                LinearProgressIndicator(
                    progress = if (overflowing) progress.overflow else progress.progress,
                    modifier = GlanceModifier.fillMaxSize(),
                    color = if (overflowing) GlanceTheme.colors.error else GlanceTheme.colors.primary,
                    backgroundColor =
                        if (overflowing) GlanceTheme.colors.errorContainer
                        else GlanceTheme.colors.primaryContainer,
                )
            }
            Spacer(modifier = GlanceModifier.width(8.dp))
            Box(modifier = GlanceModifier.width(80.dp), contentAlignment = Alignment.CenterEnd) {
                GoalValue(context, spec, leftKcal)
            }
        }
    }
}

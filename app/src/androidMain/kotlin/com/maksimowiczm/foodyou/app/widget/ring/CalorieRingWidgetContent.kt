package com.maksimowiczm.foodyou.app.widget.ring

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
internal fun CalorieRingWidgetContent(model: CalorieWidgetModel) {
    val context = LocalContext.current
    val tall = isTallWidget()
    val padding =
        if (tall) CalorieRingWidgetSizes.TallPadding else CalorieRingWidgetSizes.CompactPadding
    val ring = ringDiameter(LocalSize.current.height, tall)

    Box(
        modifier =
            GlanceModifier.fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .appWidgetBackground()
                .widgetCornerRadius()
                .clickable(actionStartActivity(calorieWidgetLaunchIntent(context)))
                .padding(padding)
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            HeaderRow(context, model)
            Spacer(modifier = GlanceModifier.height(4.dp))
            HeroRow(context, model, ring, modifier = GlanceModifier.fillMaxWidth().defaultWeight())
            if (tall) {
                Spacer(modifier = GlanceModifier.height(8.dp))
                GoalBarRow(
                    context = context,
                    label = context.getString(R.string.widget_calories_optimized),
                    goalKcal = model.optimizedGoalKcal,
                    leftKcal = model.optimizedLeftKcal,
                    netKcal = model.netKcal,
                    disabledText = context.getString(R.string.widget_calories_placeholder),
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                GoalBarRow(
                    context = context,
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
private fun HeaderRow(context: Context, model: CalorieWidgetModel) {
    val steps = context.formatWidgetNumber(model.countedSteps)
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(CalorieRingWidgetSizes.HeaderHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = context.formatWidgetDate(model.date),
            style =
                TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        Image(
            provider = ImageProvider(R.drawable.ic_widget_walk),
            contentDescription = null,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
            modifier = GlanceModifier.size(16.dp),
        )
        Spacer(modifier = GlanceModifier.width(4.dp))
        Text(
            text = steps,
            style =
                TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 13.sp,
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
    ring: Dp,
    modifier: GlanceModifier = GlanceModifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        RingWithCenter(context, model, ring)
        Spacer(modifier = GlanceModifier.width(14.dp))
        Column(
            modifier = GlanceModifier.defaultWeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SideMetric(
                context = context,
                label = context.getString(R.string.widget_calories_eaten),
                value = model.eatenKcal,
                color = GlanceTheme.colors.primary,
            )
            Spacer(modifier = GlanceModifier.height(6.dp))
            SideMetric(
                context = context,
                label = context.getString(R.string.widget_calories_burned),
                value = model.burnedKcal,
                color = GlanceTheme.colors.tertiary,
            )
        }
    }
}

@Composable
private fun RingWithCenter(context: Context, model: CalorieWidgetModel, ring: Dp) {
    val density = context.resources.displayMetrics.density
    val sizePx = min((ring.value * density).roundToInt(), WidgetRingRenderer.MAX_PIXELS)
    val strokeDp = if (ring < 80.dp) 6.dp else 9.dp
    val strokePx = strokeDp.value * sizePx / ring.value
    val progress = calorieWidgetProgress(model.netKcal, model.normalGoalKcal)
    val arcs = ringArcs(progress.progress, progress.overflow)
    val valueSize = if (ring < 72.dp) 15.sp else if (ring < 88.dp) 18.sp else 20.sp
    val labelSize = if (ring < 72.dp) 9.sp else 10.sp
    val eaten = context.formatWidgetNumber(model.eatenKcal)
    val goal = context.formatWidgetNumber(model.normalGoalKcal)
    val left = context.formatWidgetNumber(model.normalLeftKcal)

    Box(
        modifier =
            GlanceModifier.size(ring).semantics {
                contentDescription =
                    context.getString(R.string.widget_calorie_ring_ring_description, eaten, goal, left)
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(WidgetRingRenderer.track(sizePx, strokePx)),
            contentDescription = null,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.surfaceVariant),
            modifier = GlanceModifier.size(ring),
        )
        if (arcs.hasProgress) {
            Image(
                provider =
                    ImageProvider(
                        WidgetRingRenderer.arc(
                            sizePx = sizePx,
                            strokePx = strokePx,
                            startDeg = arcs.progressStartDeg,
                            sweepDeg = arcs.progressSweepDeg,
                            roundCaps = !arcs.hasOverflow,
                        )
                    ),
                contentDescription = null,
                colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                modifier = GlanceModifier.size(ring),
            )
        }
        if (arcs.hasOverflow) {
            Image(
                provider =
                    ImageProvider(
                        WidgetRingRenderer.arc(
                            sizePx = sizePx,
                            strokePx = strokePx,
                            startDeg = arcs.overflowStartDeg,
                            sweepDeg = arcs.overflowSweepDeg,
                            roundCaps = false,
                        )
                    ),
                contentDescription = null,
                colorFilter = ColorFilter.tint(GlanceTheme.colors.error),
                modifier = GlanceModifier.size(ring),
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = left,
                style =
                    TextStyle(
                        color =
                            if (model.normalLeftKcal < 0) GlanceTheme.colors.error
                            else GlanceTheme.colors.onSurface,
                        fontSize = valueSize,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    ),
                maxLines = 1,
            )
            Text(
                text = context.getString(R.string.widget_calories_left),
                style =
                    TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = labelSize,
                        textAlign = TextAlign.Center,
                    ),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SideMetric(context: Context, label: String, value: Int, color: ColorProvider) {
    Column {
        Text(
            text = label,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
            maxLines = 1,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = context.formatWidgetNumber(value),
                style = TextStyle(color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            Spacer(modifier = GlanceModifier.width(3.dp))
            Text(
                text = context.getString(R.string.widget_calories_unit_kcal),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
                maxLines = 1,
                modifier = GlanceModifier.padding(bottom = 2.dp),
            )
        }
    }
}

@Composable
private fun GoalBarRow(
    context: Context,
    label: String,
    goalKcal: Int?,
    leftKcal: Int?,
    netKcal: Int,
    disabledText: String,
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(CalorieRingWidgetSizes.GoalRowHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
            maxLines = 1,
            modifier = GlanceModifier.width(62.dp),
        )
        if (goalKcal == null || leftKcal == null) {
            Text(
                text = disabledText,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
        } else {
            val progress = calorieWidgetProgress(netKcal, goalKcal)
            val overflowing = progress.overflow > 0f
            Box(modifier = GlanceModifier.defaultWeight().height(6.dp).cornerRadius(3.dp)) {
                LinearProgressIndicator(
                    progress = if (overflowing) 1f else progress.progress,
                    modifier = GlanceModifier.fillMaxSize(),
                    color = if (overflowing) GlanceTheme.colors.error else GlanceTheme.colors.primary,
                    backgroundColor = GlanceTheme.colors.surfaceVariant,
                )
                if (overflowing) {
                    LinearProgressIndicator(
                        progress = 1f - progress.overflow,
                        modifier = GlanceModifier.fillMaxSize(),
                        color = GlanceTheme.colors.primary,
                        backgroundColor = ColorProvider(Color.Transparent),
                    )
                }
            }
            Spacer(modifier = GlanceModifier.width(8.dp))
            Text(
                text = context.formatWidgetNumber(leftKcal),
                style =
                    TextStyle(
                        color =
                            if (leftKcal < 0) GlanceTheme.colors.error
                            else GlanceTheme.colors.onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                    ),
                maxLines = 1,
                modifier = GlanceModifier.width(56.dp),
            )
        }
    }
}

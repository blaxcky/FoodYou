package com.maksimowiczm.foodyou.app.widget.gauge

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
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
import com.maksimowiczm.foodyou.app.infrastructure.android.barcodeScanIntent
import com.maksimowiczm.foodyou.app.infrastructure.android.quickCaptureCameraIntent
import com.maksimowiczm.foodyou.app.widget.CalorieWidgetModel
import com.maksimowiczm.foodyou.app.widget.calorieWidgetLaunchIntent
import com.maksimowiczm.foodyou.app.widget.formatWidgetNumber
import com.maksimowiczm.foodyou.app.widget.ring.WidgetHeaderRow
import com.maksimowiczm.foodyou.app.widget.ring.widgetCornerRadius
import kotlin.math.abs

@Composable
internal fun CalorieGaugeWidgetContent(model: CalorieWidgetModel) {
    val context = LocalContext.current
    val gauge = calorieGauge(model)
    val spec =
        calorieGaugeLayoutSpec(
            size = LocalSize.current,
            fontScale = context.resources.configuration.fontScale,
            showLegend = gauge.zones.isNotEmpty(),
            offRingDepth = gauge.offRingDepth,
        )

    Box(
        modifier =
            GlanceModifier.fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .appWidgetBackground()
                .widgetCornerRadius()
                .clickable(actionStartActivity(calorieWidgetLaunchIntent(context)))
                .padding(horizontal = spec.horizontalPadding, vertical = spec.verticalPadding)
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            if (spec.showHeader) {
                WidgetHeaderRow(
                    context = context,
                    model = model,
                    height = CalorieGaugeWidgetSizes.HeaderHeight,
                    dateTextSize = CalorieGaugeWidgetSizes.HeaderDateTextSize,
                    dateIconSize = CalorieGaugeWidgetSizes.HeaderHeight - 4.dp,
                    stepsTextSize = CalorieGaugeWidgetSizes.HeaderStepsTextSize,
                )
                Spacer(modifier = GlanceModifier.height(CalorieGaugeWidgetSizes.HeaderSpacing))
            }
            Row(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SideMetric(
                    spec = spec,
                    value = context.formatWidgetNumber(model.eatenKcal),
                    label = context.getString(R.string.widget_calorie_gauge_eaten),
                    modifier = GlanceModifier.defaultWeight().ringSideGap(start = true),
                )
                Gauge(context, gauge, spec)
                SideMetric(
                    spec = spec,
                    value = context.formatWidgetNumber(model.burnedKcal),
                    label = context.getString(R.string.widget_calorie_gauge_burned),
                    modifier = GlanceModifier.defaultWeight().ringSideGap(start = false),
                )
            }
            if (spec.showLegend) {
                Spacer(modifier = GlanceModifier.height(spec.legendGap))
                GoalLegend(context, gauge, spec)
            }
            if (spec.showHeader) {
                Spacer(modifier = GlanceModifier.height(CalorieGaugeWidgetSizes.GaugeLift))
            }
        }
        // The shortcuts keep their distance from the widget's bottom edge, within thumb reach,
        // whatever the header and the goal legend take above them.
        Row(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.Bottom,
        ) {
            ActionButton(
                spec = spec,
                action =
                    SideAction(
                        icon = R.drawable.ic_widget_barcode_scanner,
                        description = context.getString(R.string.shortcut_scan_barcode_long),
                        onClick = actionStartActivity(barcodeScanIntent(context)),
                    ),
                modifier = GlanceModifier.defaultWeight().ringSideGap(start = true),
            )
            Spacer(modifier = GlanceModifier.width(spec.gaugeWidth))
            ActionButton(
                spec = spec,
                action =
                    SideAction(
                        icon = R.drawable.ic_widget_add_a_photo,
                        description =
                            context.getString(R.string.shortcut_quick_capture_camera_long),
                        onClick = actionStartActivity(quickCaptureCameraIntent(context)),
                    ),
                modifier = GlanceModifier.defaultWeight().ringSideGap(start = false),
            )
        }
    }
}

/** Room between a side column and the ring, on the ring's side. */
private fun GlanceModifier.ringSideGap(start: Boolean): GlanceModifier =
    if (start) padding(end = CalorieGaugeWidgetSizes.MetricGaugeGap)
    else padding(start = CalorieGaugeWidgetSizes.MetricGaugeGap)

@Composable
private fun Gauge(context: Context, gauge: CalorieGauge, spec: CalorieGaugeLayoutSpec) {
    Image(
        provider = ImageProvider(CalorieGaugeRenderer.render(context, spec, gauge)),
        contentDescription =
            goalDescription(context, R.string.widget_calories_normal, gauge.remainingKcal),
        contentScale = ContentScale.FillBounds,
        modifier = GlanceModifier.size(spec.gaugeWidth, spec.gaugeHeight),
    )
}

/**
 * The supplemental goals' remaining energy in one quiet row under the ring: a dot in the goal's
 * color and the number, as on the goals card. Only an exceeded goal says so, in red.
 */
@Composable
private fun GoalLegend(context: Context, gauge: CalorieGauge, spec: CalorieGaugeLayoutSpec) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        gauge.zones.forEachIndexed { index, zone ->
            if (index > 0) Spacer(modifier = GlanceModifier.width(spec.legendItemGap))
            val description =
                goalDescription(
                    context,
                    when (zone.kind) {
                        CalorieGaugeZoneKind.Optimized -> R.string.widget_calories_optimized
                        CalorieGaugeZoneKind.Diet -> R.string.widget_calories_diet
                    },
                    zone.remainingKcal,
                )
            val valueColor =
                if (zone.exceeded) GlanceTheme.colors.error else GlanceTheme.colors.onSurface
            Row(
                modifier = GlanceModifier.semantics { contentDescription = description },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_dot),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(ColorProvider(zone.kind.color())),
                    modifier = GlanceModifier.size(spec.legendDotSize),
                )
                Spacer(modifier = GlanceModifier.width(5.dp))
                Text(
                    text = context.formatWidgetNumber(abs(zone.remainingKcal)),
                    style =
                        TextStyle(
                            color = valueColor,
                            fontSize = spec.legendTextSize,
                            fontWeight = FontWeight.Medium,
                        ),
                    maxLines = 1,
                )
                if (zone.exceeded) {
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    Text(
                        text = context.getString(R.string.widget_calories_over_lowercase),
                        style =
                            TextStyle(
                                color = GlanceTheme.colors.error,
                                fontSize = spec.metricLabelTextSize,
                                fontWeight = FontWeight.Medium,
                            ),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** A goal's name with its remaining energy, as read out for the ring and the legend. */
private fun goalDescription(context: Context, label: Int, remainingKcal: Int): String =
    context.getString(
        R.string.widget_calorie_gauge_goal_description,
        context.getString(label),
        context.formatWidgetNumber(abs(remainingKcal)),
        context.getString(
            if (remainingKcal < 0) R.string.widget_calories_over_lowercase
            else R.string.widget_calories_left_lowercase
        ),
    )

/** A shortcut started from the icon under a side value. */
private class SideAction(@DrawableRes val icon: Int, val description: String, val onClick: Action)

/**
 * Eaten or burned energy beside the ring; the goal and its share are left to the ring. The value
 * opens the app. Its shortcut sits under it at the widget's bottom edge, and the room it took in
 * this column is kept so the value stays lifted above the ring's center.
 */
@Composable
private fun SideMetric(
    spec: CalorieGaugeLayoutSpec,
    value: String,
    label: String,
    modifier: GlanceModifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = GlanceModifier.height(spec.actionDrop))
        Text(
            text = value,
            style =
                TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = spec.metricValueTextSize,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                ),
            maxLines = 1,
        )
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
        Spacer(
            modifier =
                GlanceModifier.height(spec.actionGap + spec.actionDrop + spec.actionButtonSize)
        )
    }
}

/** A side value's shortcut icon; only the icon starts the shortcut. */
@Composable
private fun ActionButton(
    spec: CalorieGaugeLayoutSpec,
    action: SideAction,
    modifier: GlanceModifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.BottomCenter) {
        Box(
            modifier =
                GlanceModifier.size(spec.actionButtonSize)
                    .clickable(action.onClick)
                    .semantics { contentDescription = action.description },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(action.icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
                modifier = GlanceModifier.size(spec.actionIconSize),
            )
        }
    }
}

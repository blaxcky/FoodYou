package com.maksimowiczm.foodyou.app.widget.gauge

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
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
    val spec =
        calorieGaugeLayoutSpec(LocalSize.current, context.resources.configuration.fontScale)

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
                    modifier =
                        GlanceModifier.defaultWeight()
                            .padding(end = CalorieGaugeWidgetSizes.MetricGaugeGap)
                            .clickable(actionStartActivity(quickCaptureCameraIntent(context))),
                )
                Gauge(context, model, spec)
                SideMetric(
                    spec = spec,
                    value = context.formatWidgetNumber(model.burnedKcal),
                    label = context.getString(R.string.widget_calorie_gauge_burned),
                    modifier =
                        GlanceModifier.defaultWeight()
                            .padding(start = CalorieGaugeWidgetSizes.MetricGaugeGap)
                            .clickable(actionStartActivity(barcodeScanIntent(context))),
                )
            }
            if (spec.showHeader) {
                Spacer(modifier = GlanceModifier.height(CalorieGaugeWidgetSizes.GaugeLift))
            }
        }
    }
}

@Composable
private fun Gauge(context: Context, model: CalorieWidgetModel, spec: CalorieGaugeLayoutSpec) {
    val gauge = calorieGauge(model)
    Image(
        provider = ImageProvider(CalorieGaugeRenderer.render(context, spec, gauge)),
        contentDescription = gaugeDescription(context, gauge),
        contentScale = ContentScale.FillBounds,
        modifier = GlanceModifier.size(spec.gaugeWidth, spec.gaugeHeight),
    )
}

/** The values on the ring, read in the same order: normal goal first, then the goals on it. */
private fun gaugeDescription(context: Context, gauge: CalorieGauge): String {
    fun goal(label: Int, remainingKcal: Int): String =
        context.getString(
            R.string.widget_calorie_gauge_goal_description,
            context.getString(label),
            context.formatWidgetNumber(abs(remainingKcal)),
            context.getString(
                if (remainingKcal < 0) R.string.widget_calories_over_lowercase
                else R.string.widget_calories_left_lowercase
            ),
        )

    val zones =
        gauge.zones.map {
            goal(
                label =
                    when (it.kind) {
                        CalorieGaugeZoneKind.Optimized -> R.string.widget_calories_optimized
                        CalorieGaugeZoneKind.Diet -> R.string.widget_calories_diet
                    },
                remainingKcal = it.remainingKcal,
            )
        }
    return (listOf(goal(R.string.widget_calories_normal, gauge.remainingKcal)) + zones)
        .joinToString(", ")
}

/** Eaten or burned energy beside the ring; the goal and its share are left to the ring. */
@Composable
private fun SideMetric(
    spec: CalorieGaugeLayoutSpec,
    value: String,
    label: String,
    modifier: GlanceModifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
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
    }
}

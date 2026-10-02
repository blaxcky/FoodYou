package com.maksimowiczm.foodyou.app.ui.weight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.weight.domain.entity.DailyWeightEntry
import com.maksimowiczm.foodyou.weight.domain.usecase.calculateWeightGoalPosition
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.number
import org.koin.compose.viewmodel.koinViewModel

private val ReportBackground = Color(0xFFEAF5FC)
private val ReportPrimary = Color(0xFF1F6FB7)
private val ReportPrimaryDark = Color(0xFF175C9E)
private val ReportText = Color(0xFF17212B)
private val ReportMutedText = Color(0xFF5F7080)
private val ReportGrid = Color(0xFFD7E2EB)
private val ReportDivider = Color(0xFFEAF0F5)
private val ReportSoftButton = Color(0xFFD8ECFA)
private val ReportMeasurementDot = Color(0xFF9DC2E6)
private val ReportAwayFromGoal = Color(0xFFE8A060)
private val ReportCardShape = RoundedCornerShape(26.dp)
private val ChartHeight = 212.dp
private val ChartAxisWidth = 40.dp
private val ChartDateLabelHeight = 22.dp
private const val HistoryPreviewCount = 10
private const val WeightAdjustRepeatStartMillis = 550L
private const val WeightAdjustRepeatMillis = 175L
private const val HealthConnectScaleDeviceType = 3

@Composable
internal fun WeightReportScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WeightReportViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val permissionRequester =
        rememberHealthConnectWeightPermissionRequester(viewModel::onHealthConnectPermissionResult)

    WeightReportContent(
        state = state,
        onBack = onBack,
        onSaveWeight = viewModel::setWeight,
        onHealthConnectClick = permissionRequester::request,
        onToggleHidden = viewModel::setHidden,
        modifier = modifier,
    )
}

@Composable
internal fun WeightReportContent(
    state: WeightReportUiState,
    onBack: () -> Unit,
    onSaveWeight: (Double) -> Unit,
    onHealthConnectClick: () -> Unit,
    onToggleHidden: (String, Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    var draftWeightKg by rememberSaveable { mutableStateOf<Double?>(null) }
    draftWeightKg?.let { weightKg ->
        WeightEntryDialog(
            weightKg = weightKg,
            onWeightChange = { draftWeightKg = it },
            onDismiss = { draftWeightKg = null },
            onConfirm = {
                draftWeightKg?.let { confirmedWeightKg ->
                    draftWeightKg = null
                    onSaveWeight(confirmedWeightKg)
                }
            },
        )
    }

    var chartRange by rememberSaveable { mutableStateOf(WeightChartRange.OneYear) }
    val today = state.today ?: state.entries.maxOfOrNull { it.date }
    val chartModel =
        remember(state.entries, chartRange, today, state.targetWeightKg) {
            today?.let { buildWeightChartModel(state.entries, chartRange, it, state.targetWeightKg) }
        }

    Scaffold(modifier = modifier, containerColor = ReportBackground) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues).background(ReportBackground),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Surface(color = Color.White, tonalElevation = 0.dp, shadowElevation = 0.dp) {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        WeightReportHeader(subtitle = chartRange.label, onBack = onBack)
                        WeightTabs()
                        WeightRangeSelector(
                            selected = chartRange,
                            onSelect = { chartRange = it },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(top = 16.dp),
                        )
                        WeightChart(
                            model = chartModel,
                            range = chartRange,
                            targetWeightKg = state.targetWeightKg,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                        )
                    }
                }
            }
            item {
                CurrentWeightCard(
                    startWeightKg = state.startWeightKg,
                    currentWeightKg = state.todayWeightKg,
                    suggestedWeightKg = state.suggestedWeightKg,
                    progressWeightKg = state.currentWeightKg,
                    targetWeightKg = state.targetWeightKg,
                    lastMeasurementDate = state.entries.maxOfOrNull { it.date },
                    onEnterWeight = {
                        val base = state.todayWeightKg ?: state.suggestedWeightKg
                        draftWeightKg = base?.let { (round(it * 10.0) / 10.0).coerceAtLeast(0.1) }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
            val currentBmi = calculateBmi(state.currentWeightKg ?: state.suggestedWeightKg, state.heightCm)
            if (currentBmi != null) {
                item {
                    BmiCard(
                        bmi = currentBmi,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                }
            }
            if (state.healthConnectAvailable && !state.healthConnectPermissionGranted) {
                item {
                    HealthConnectCard(
                        onClick = onHealthConnectClick,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                }
            }
            if (state.entries.isNotEmpty()) {
                item {
                    HistoryCard(
                        entries = state.entries,
                        onToggleHidden = onToggleHidden,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                }
            }
            if (state.hiddenEntries.isNotEmpty()) {
                item {
                    var expanded by remember { mutableStateOf(false) }
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        TextButton(
                            onClick = { expanded = !expanded },
                            colors = ButtonDefaults.textButtonColors(contentColor = ReportPrimaryDark),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VisibilityOff,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Ausgeblendete Werte (${state.hiddenEntries.size})")
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector =
                                    if (expanded) Icons.Filled.KeyboardArrowUp
                                    else Icons.Filled.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        if (expanded) {
                            HistoryCard(
                                entries = state.hiddenEntries,
                                onToggleHidden = onToggleHidden,
                                showChanges = false,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeightReportHeader(subtitle: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArrowBackIconButton(onClick = onBack)
        Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                text = "Diätbericht",
                style = MaterialTheme.typography.headlineSmall,
                color = ReportText,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = ReportMutedText,
            )
        }
        IconButton(onClick = {}) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = "Einstellungen",
                tint = ReportText,
            )
        }
    }
}

@Composable
private fun WeightTabs(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(start = 24.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Gewicht",
                    style = MaterialTheme.typography.titleSmall,
                    color = ReportPrimaryDark,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                Box(
                    modifier =
                        Modifier.width(56.dp)
                            .height(3.dp)
                            .background(ReportPrimary, RoundedCornerShape(2.dp))
                )
            }
        }
        HorizontalDivider(color = ReportDivider)
    }
}

@Composable
private fun WeightRangeSelector(
    selected: WeightChartRange,
    onSelect: (WeightChartRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ranges = WeightChartRange.entries
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        ranges.forEachIndexed { index, range ->
            SegmentedButton(
                selected = range == selected,
                onClick = { onSelect(range) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = ranges.size),
                modifier = Modifier.semantics { contentDescription = range.label },
                colors =
                    SegmentedButtonDefaults.colors(
                        activeContainerColor = ReportSoftButton,
                        activeContentColor = ReportPrimaryDark,
                        activeBorderColor = ReportGrid,
                        inactiveContainerColor = Color.White,
                        inactiveContentColor = ReportText,
                        inactiveBorderColor = ReportGrid,
                    ),
                icon = {},
                label = { Text(range.shortLabel) },
            )
        }
    }
}

@Composable
private fun WeightChart(
    model: WeightChartModel?,
    range: WeightChartRange,
    targetWeightKg: Double?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(top = 14.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (model == null) {
            Box(
                modifier = Modifier.fillMaxWidth().height(ChartHeight),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Keine Messungen in diesem Zeitraum",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ReportMutedText,
                )
            }
        } else {
            WeightChartCanvas(model = model, range = range, targetWeightKg = targetWeightKg)
            WeightChartLegend(model = model, targetWeightKg = targetWeightKg)
        }
    }
}

@Composable
private fun WeightChartCanvas(
    model: WeightChartModel,
    range: WeightChartRange,
    targetWeightKg: Double?,
    modifier: Modifier = Modifier,
) {
    var selectedIndex by remember(model) { mutableStateOf<Int?>(null) }
    val textMeasurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = ReportMutedText)
    val tooltipStyle =
        MaterialTheme.typography.labelMedium.copy(color = Color.White, fontWeight = FontWeight.Medium)
    val shortRange = model.endDate.toEpochDays() - model.startDate.toEpochDays() <= 45

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(ChartHeight)
                .semantics { contentDescription = chartDescription(model, range) }
                .pointerInput(model) {
                    detectTapGestures { offset ->
                        val index =
                            model.nearestMeasurementIndex(
                                x = offset.x,
                                plotLeft = ChartAxisWidth.toPx(),
                                plotWidth = size.width - ChartAxisWidth.toPx() - 6.dp.toPx(),
                            )
                        selectedIndex = if (index == selectedIndex) null else index
                    }
                }
                .pointerInput(model) {
                    val plotLeft = ChartAxisWidth.toPx()
                    val plotWidth = size.width - plotLeft - 6.dp.toPx()
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            selectedIndex = model.nearestMeasurementIndex(offset.x, plotLeft, plotWidth)
                        }
                    ) { change, _ ->
                        selectedIndex = model.nearestMeasurementIndex(change.position.x, plotLeft, plotWidth)
                    }
                }
    ) {
        val plotLeft = ChartAxisWidth.toPx()
        val plotRight = size.width - 6.dp.toPx()
        val plotTop = 8.dp.toPx()
        val plotBottom = size.height - ChartDateLabelHeight.toPx()
        val plotWidth = plotRight - plotLeft
        val plotHeight = plotBottom - plotTop

        fun x(epochDay: Double) = plotLeft + model.xFraction(epochDay) * plotWidth
        fun y(weightKg: Double) = plotTop + model.yFraction(weightKg) * plotHeight

        model.gridWeightsKg.forEach { weight ->
            val gridY = y(weight)
            drawLine(ReportGrid, Offset(plotLeft, gridY), Offset(plotRight, gridY), 1.dp.toPx())
            val label = textMeasurer.measure(formatAxisWeight(weight), axisStyle)
            drawText(
                textLayoutResult = label,
                topLeft = Offset(plotLeft - 8.dp.toPx() - label.size.width, gridY - label.size.height / 2f),
            )
        }

        if (model.showsTarget && targetWeightKg != null) {
            val targetY = y(targetWeightKg)
            drawLine(
                color = ReportPrimary.copy(alpha = 0.55f),
                start = Offset(plotLeft, targetY),
                end = Offset(plotRight, targetY),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())),
            )
        }

        model.ticks.forEach { date ->
            val label = textMeasurer.measure(formatChartTick(date, shortRange), axisStyle)
            val left =
                (x(date.toEpochDays().toDouble()) - label.size.width / 2f)
                    .coerceAtMost(size.width - label.size.width)
                    .coerceAtLeast(plotLeft - 8.dp.toPx())
            drawText(textLayoutResult = label, topLeft = Offset(left, plotBottom + 6.dp.toPx()))
        }

        model.measurements.forEach { entry ->
            drawCircle(
                color = ReportMeasurementDot,
                radius = 3.dp.toPx(),
                center = Offset(x(entry.date.toEpochDays().toDouble()), y(entry.weightKg)),
            )
        }

        if (model.trend.size >= 2) {
            drawPath(
                path = monotoneCurve(model.trend.map { Offset(x(it.epochDay), y(it.weightKg)) }),
                color = ReportPrimary,
                style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

        selectedIndex
            ?.let { model.measurements.getOrNull(it) }
            ?.let { entry ->
                val center = Offset(x(entry.date.toEpochDays().toDouble()), y(entry.weightKg))
                drawLine(
                    color = ReportMutedText.copy(alpha = 0.5f),
                    start = Offset(center.x, plotTop),
                    end = Offset(center.x, plotBottom),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                )
                drawCircle(Color.White, radius = 7.dp.toPx(), center = center)
                drawCircle(ReportPrimaryDark, radius = 5.dp.toPx(), center = center)
                drawChartTooltip(
                    text =
                        textMeasurer.measure(
                            "${formatShortDate(entry.date)} · ${formatWeight(entry.weightKg)}",
                            tooltipStyle,
                        ),
                    anchor = center,
                    minX = plotLeft,
                    maxX = plotRight,
                    minY = plotTop,
                )
            }
    }
}

private fun DrawScope.drawChartTooltip(
    text: androidx.compose.ui.text.TextLayoutResult,
    anchor: Offset,
    minX: Float,
    maxX: Float,
    minY: Float,
) {
    val paddingH = 8.dp.toPx()
    val paddingV = 4.dp.toPx()
    val gap = 10.dp.toPx()
    val boxSize = Size(text.size.width + paddingH * 2, text.size.height + paddingV * 2)
    val left = (anchor.x - boxSize.width / 2f).coerceAtMost(maxX - boxSize.width).coerceAtLeast(minX)
    val above = anchor.y - gap - boxSize.height
    val top = if (above >= minY) above else anchor.y + gap
    drawRoundRect(
        color = ReportText,
        topLeft = Offset(left, top),
        size = boxSize,
        cornerRadius = CornerRadius(8.dp.toPx()),
    )
    drawText(textLayoutResult = text, topLeft = Offset(left + paddingH, top + paddingV))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeightChartLegend(
    model: WeightChartModel,
    targetWeightKg: Double?,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth().padding(start = ChartAxisWidth),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ChartLegendItem("Messung") {
            drawCircle(ReportMeasurementDot, radius = 3.dp.toPx(), center = center)
        }
        ChartLegendItem("Trend") {
            drawLine(
                color = ReportPrimary,
                start = Offset(0f, center.y),
                end = Offset(size.width, center.y),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        if (targetWeightKg != null) {
            val position =
                when {
                    model.showsTarget -> ""
                    targetWeightKg < model.minWeightKg -> " (unterhalb)"
                    else -> " (oberhalb)"
                }
            ChartLegendItem("Ziel ${formatWeight(targetWeightKg)}$position") {
                drawLine(
                    color = ReportPrimary.copy(alpha = 0.55f),
                    start = Offset(0f, center.y),
                    end = Offset(size.width, center.y),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
                )
            }
        }
    }
}

@Composable
private fun ChartLegendItem(label: String, glyph: DrawScope.() -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Canvas(modifier = Modifier.size(width = 14.dp, height = 8.dp), onDraw = glyph)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = ReportMutedText)
    }
}

@Composable
private fun CurrentWeightCard(
    startWeightKg: Double?,
    currentWeightKg: Double?,
    suggestedWeightKg: Double?,
    progressWeightKg: Double?,
    targetWeightKg: Double?,
    lastMeasurementDate: LocalDate?,
    onEnterWeight: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayWeight = currentWeightKg ?: suggestedWeightKg
    val startDelta = progressWeightKg?.let { current -> startWeightKg?.let { current - it } }
    val measuredLabel =
        if (currentWeightKg != null) "heute" else lastMeasurementDate?.let { formatShortDate(it) }
    Surface(
        modifier = modifier,
        shape = ReportCardShape,
        color = Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Dein Gewicht",
                            style = MaterialTheme.typography.labelLarge,
                            color = ReportMutedText,
                            modifier = Modifier.weight(1f),
                        )
                        if (displayWeight != null && measuredLabel != null) {
                            Text(
                                text = measuredLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = ReportMutedText,
                            )
                        }
                    }
                    Text(
                        text = formatHeroWeight(displayWeight),
                        style = MaterialTheme.typography.headlineLarge,
                        color = ReportText,
                        fontWeight = FontWeight.Bold,
                    )
                    if (startDelta != null && abs(startDelta) >= 0.05) {
                        Text(
                            text = "${formatWeightChangeSinceStart(startDelta)} seit Start",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ReportMutedText,
                        )
                    }
                }
                Button(
                    onClick = onEnterWeight,
                    enabled = displayWeight != null,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ReportPrimary),
                ) {
                    Text("Neues Gewicht eintragen")
                }
            }
            if (startWeightKg != null && targetWeightKg != null) {
                WeightGoalProgress(
                    startWeightKg = startWeightKg,
                    currentWeightKg = progressWeightKg,
                    targetWeightKg = targetWeightKg,
                )
            }
        }
    }
}

@Composable
private fun WeightEntryDialog(
    weightKg: Double,
    onWeightChange: (Double) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.padding(horizontal = 16.dp),
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        titleContentColor = ReportText,
        textContentColor = ReportText,
        title = { Text("Neues Gewicht eintragen") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = formatWeight(weightKg),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    WeightAdjustButton(
                        icon = { Icon(Icons.Filled.Remove, contentDescription = "0,1 kg abziehen") },
                        enabled = weightKg > 0.1,
                        onClick = { onWeightChange((round(weightKg * 10.0) - 1.0) / 10.0) },
                    )
                    Text("0,1 kg", style = MaterialTheme.typography.labelLarge)
                    WeightAdjustButton(
                        icon = { Icon(Icons.Filled.Add, contentDescription = "0,1 kg addieren") },
                        enabled = true,
                        onClick = { onWeightChange((round(weightKg * 10.0) + 1.0) / 10.0) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = ReportPrimaryDark),
            ) { Text("Übernehmen") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = ReportPrimaryDark),
            ) { Text("Abbrechen") }
        },
    )
}

@Composable
private fun WeightAdjustButton(
    icon: @Composable () -> Unit,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnClick by rememberUpdatedState(onClick)
    Surface(
        modifier =
            modifier
                .size(38.dp)
                .weightAdjustPressHandler(enabled = enabled, onClick = { currentOnClick() })
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    if (!enabled) disabled()
                    onClick {
                        if (enabled) currentOnClick()
                        enabled
                    }
                },
        shape = RoundedCornerShape(19.dp),
        color = if (enabled) ReportSoftButton else ReportDivider,
        contentColor = if (enabled) ReportPrimaryDark else ReportMutedText,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { icon() }
    }
}

private fun Modifier.weightAdjustPressHandler(enabled: Boolean, onClick: () -> Unit): Modifier =
    pointerInput(enabled) {
        if (!enabled) return@pointerInput
        coroutineScope {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                onClick()
                val repeatJob =
                    launch {
                        delay(WeightAdjustRepeatStartMillis)
                        while (isActive) {
                            onClick()
                            delay(WeightAdjustRepeatMillis)
                        }
                    }
                do {
                    val event = awaitPointerEvent()
                } while (event.changes.any { it.pressed })
                repeatJob.cancel()
            }
        }
    }

@Composable
private fun BmiCard(bmi: Double, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = ReportCardShape,
        color = Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.width(74.dp)) {
                Text(
                    text = "BMI",
                    style = MaterialTheme.typography.labelLarge,
                    color = ReportMutedText,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = formatOneDecimal(bmi),
                    style = MaterialTheme.typography.titleLarge,
                    color = ReportText,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = bmiCategoryLabel(bmi),
                    style = MaterialTheme.typography.bodySmall,
                    color = ReportMutedText,
                )
            }
            BmiScale(
                bmi = bmi,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private const val BmiScaleMin = 15.0
private const val BmiScaleMax = 40.0
private val BmiScaleSegments =
    listOf(
        Triple(BmiScaleMin, 18.5, Color(0xFF8FBCE6)),
        Triple(18.5, 25.0, Color(0xFF53B96A)),
        Triple(25.0, 30.0, Color(0xFFF0C84B)),
        Triple(30.0, BmiScaleMax, Color(0xFFE05A4F)),
    )

private fun bmiScaleFraction(bmi: Double): Float =
    ((bmi.coerceIn(BmiScaleMin, BmiScaleMax) - BmiScaleMin) / (BmiScaleMax - BmiScaleMin)).toFloat()

@Composable
private fun BmiScale(bmi: Double, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(modifier = Modifier.fillMaxWidth().height(18.dp)) {
            val barHeight = 8.dp.toPx()
            val barTop = (size.height - barHeight) / 2f
            val bar =
                Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = 0f,
                            top = barTop,
                            right = size.width,
                            bottom = barTop + barHeight,
                            cornerRadius = CornerRadius(barHeight / 2f),
                        )
                    )
                }
            clipPath(bar) {
                BmiScaleSegments.forEach { (from, to, color) ->
                    val left = bmiScaleFraction(from) * size.width
                    drawRect(
                        color = color,
                        topLeft = Offset(left, barTop),
                        size = Size(bmiScaleFraction(to) * size.width - left, barHeight),
                    )
                }
                BmiScaleSegments.drop(1).forEach { (from, _, _) ->
                    val boundary = bmiScaleFraction(from) * size.width
                    drawLine(
                        color = Color.White,
                        start = Offset(boundary, barTop),
                        end = Offset(boundary, barTop + barHeight),
                        strokeWidth = 2.dp.toPx(),
                    )
                }
            }

            val markerX = bmiScaleFraction(bmi) * size.width
            drawLine(
                color = Color.White,
                start = Offset(markerX, 0f),
                end = Offset(markerX, size.height),
                strokeWidth = 5.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = ReportText,
                start = Offset(markerX, 1.dp.toPx()),
                end = Offset(markerX, size.height - 1.dp.toPx()),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        val boundaries = listOf(BmiScaleMin) + BmiScaleSegments.map { it.second }
        FractionLabels(
            fractions = boundaries.map(::bmiScaleFraction),
            modifier = Modifier.fillMaxWidth(),
        ) {
            boundaries.forEach { value ->
                Text(
                    text = formatAxisWeight(value),
                    style = MaterialTheme.typography.labelSmall,
                    color = ReportMutedText,
                )
            }
        }
    }
}

/**
 * Places each child horizontally centred on its fraction of the width, kept inside the bounds and
 * pushed left of the following child when they would overlap.
 */
@Composable
private fun FractionLabels(
    fractions: List<Float>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val width = constraints.maxWidth
        val gap = 6.dp.roundToPx()
        val positions =
            placeables
                .mapIndexed { index, placeable ->
                    val center = fractions.getOrElse(index) { 0f } * width
                    (center - placeable.width / 2f)
                        .roundToInt()
                        .coerceAtMost(width - placeable.width)
                        .coerceAtLeast(0)
                }
                .toMutableList()
        for (index in positions.lastIndex - 1 downTo 0) {
            positions[index] =
                positions[index].coerceAtMost(positions[index + 1] - gap - placeables[index].width).coerceAtLeast(0)
        }
        layout(width, placeables.maxOfOrNull { it.height } ?: 0) {
            placeables.forEachIndexed { index, placeable -> placeable.placeRelative(positions[index], 0) }
        }
    }
}

@Composable
private fun HealthConnectCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = ReportCardShape, color = Color.White) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.MonitorWeight, contentDescription = null, tint = ReportPrimary)
            Text("Health Connect Gewicht synchronisieren", modifier = Modifier.weight(1f))
            Button(onClick = onClick) { Text("Verbinden") }
        }
    }
}

@Composable
private fun WeightGoalProgress(
    startWeightKg: Double,
    currentWeightKg: Double?,
    targetWeightKg: Double,
    modifier: Modifier = Modifier,
) {
    val position = calculateWeightGoalPosition(startWeightKg, currentWeightKg, targetWeightKg)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Dein Ziel",
                style = MaterialTheme.typography.labelLarge,
                color = ReportMutedText,
                modifier = Modifier.weight(1f),
            )
            if (position != null && currentWeightKg != null) {
                Text(
                    text =
                        if (position >= 1.0) "Ziel erreicht"
                        else "noch ${formatWeight(abs(targetWeightKg - currentWeightKg))}",
                    style = MaterialTheme.typography.labelLarge,
                    color = ReportPrimaryDark,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (position != null) {
            // Moving away from the target extends the bar to the left of the start marker.
            val startFraction = if (position < 0) (-position / (1 - position)).toFloat() else 0f
            val currentFraction = if (position < 0) 0f else position.coerceAtMost(1.0).toFloat()
            WeightGoalBar(
                position = position,
                startFraction = startFraction,
                currentFraction = currentFraction,
                startWeightKg = startWeightKg,
                currentWeightKg = currentWeightKg,
            )
            FractionLabels(fractions = listOf(startFraction, 1f), modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Start ${formatWeight(startWeightKg)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = ReportMutedText,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Flag,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = ReportPrimary,
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = formatWeight(targetWeightKg),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = ReportMutedText,
                    )
                }
            }
        }
    }
}

@Composable
private fun WeightGoalBar(
    position: Double,
    startFraction: Float,
    currentFraction: Float,
    startWeightKg: Double,
    currentWeightKg: Double?,
    modifier: Modifier = Modifier,
) {
    val description =
        if (position < 0 && currentWeightKg != null) {
            "${formatWeight(abs(currentWeightKg - startWeightKg))} weiter vom Ziel entfernt als zum Start"
        } else {
            "${(position.coerceIn(0.0, 1.0) * 100).roundToInt()} % des Weges zum Ziel geschafft"
        }
    Canvas(
        modifier =
            modifier.fillMaxWidth().height(16.dp).semantics { contentDescription = description }
    ) {
        val trackHeight = 8.dp.toPx()
        val radius = CornerRadius(trackHeight / 2f)
        val trackTop = (size.height - trackHeight) / 2f
        drawRoundRect(
            color = ReportSoftButton,
            topLeft = Offset(0f, trackTop),
            size = Size(size.width, trackHeight),
            cornerRadius = radius,
        )
        if (position < 0) {
            val startX = startFraction * size.width
            drawRoundRect(
                color = ReportAwayFromGoal,
                topLeft = Offset(0f, trackTop),
                size = Size(startX, trackHeight),
                cornerRadius = radius,
            )
            drawLine(
                color = ReportText,
                start = Offset(startX, 0f),
                end = Offset(startX, size.height),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        } else if (currentFraction > 0f) {
            drawRoundRect(
                color = ReportPrimary,
                topLeft = Offset(0f, trackTop),
                size = Size(currentFraction * size.width, trackHeight),
                cornerRadius = radius,
            )
        }
        val dotRadius = 6.dp.toPx()
        val ringRadius = 8.dp.toPx()
        val dotX = (currentFraction * size.width).coerceIn(ringRadius, size.width - ringRadius)
        drawCircle(Color.White, radius = ringRadius, center = Offset(dotX, size.height / 2f))
        drawCircle(
            color = if (position < 0) ReportText else ReportPrimaryDark,
            radius = dotRadius,
            center = Offset(dotX, size.height / 2f),
        )
    }
}

@Composable
private fun HistoryCard(
    entries: List<DailyWeightEntry>,
    onToggleHidden: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    showChanges: Boolean = true,
) {
    val changesByDate = remember(entries) {
        entries
            .zipWithNext()
            .associate { (newer, older) -> newer.date to newer.weightKg - older.weightKg }
    }
    var showAll by rememberSaveable { mutableStateOf(false) }
    val visibleEntries = if (showAll) entries else entries.take(HistoryPreviewCount)
    Surface(
        modifier = modifier,
        shape = ReportCardShape,
        color = Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            visibleEntries.forEachIndexed { index, entry ->
                val previous = visibleEntries.getOrNull(index - 1)
                if (previous == null || previous.date.year != entry.date.year || previous.date.month != entry.date.month) {
                    Text(
                        text = formatMonthHeader(entry.date),
                        style = MaterialTheme.typography.labelMedium,
                        color = ReportMutedText,
                        fontWeight = FontWeight.SemiBold,
                        modifier =
                            Modifier.padding(start = 18.dp, end = 18.dp, top = if (previous == null) 8.dp else 16.dp, bottom = 2.dp),
                    )
                } else {
                    HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = ReportDivider)
                }
                HistoryRow(
                    entry = entry,
                    changeKg = if (showChanges) changesByDate[entry.date] else null,
                    onToggleHidden = onToggleHidden,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (entries.size > visibleEntries.size) {
                TextButton(
                    onClick = { showAll = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = ReportPrimaryDark),
                    modifier = Modifier.padding(horizontal = 6.dp),
                ) {
                    Text("Alle ${entries.size} Werte anzeigen")
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    entry: DailyWeightEntry,
    changeKg: Double?,
    onToggleHidden: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .clickable(onClickLabel = "Optionen") { menuExpanded = true }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = formatShortDate(entry.date),
                    style = MaterialTheme.typography.bodyLarge,
                    color = ReportText,
                    fontWeight = FontWeight.Medium,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector =
                            if (entry.isFoodYouRecord) Icons.Filled.Edit
                            else if (entry.sourceDeviceType == HealthConnectScaleDeviceType) Icons.Filled.MonitorWeight
                            else Icons.Filled.Sync,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = ReportMutedText,
                    )
                    Spacer(Modifier.width(4.dp))
                    val appName = entry.sourcePackageName?.let { sourceAppLabel(it) }
                    Text(
                        text = if (entry.isFoodYouRecord) "FoodYou" else appName ?: "Health Connect",
                        style = MaterialTheme.typography.labelSmall,
                        color = ReportMutedText,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatWeight(entry.weightKg),
                    style = MaterialTheme.typography.titleMedium,
                    color = ReportText,
                    fontWeight = FontWeight.Bold,
                )
                if (changeKg != null && abs(changeKg) >= 0.05) {
                    Text(
                        text = formatSignedWeightChange(changeKg),
                        style = MaterialTheme.typography.bodySmall,
                        color = ReportMutedText,
                    )
                }
            }
        }
        Box(modifier = Modifier.align(Alignment.TopEnd)) {
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(if (entry.isHidden) "Wieder einblenden" else "Ausblenden") },
                    leadingIcon = {
                        Icon(
                            imageVector =
                                if (entry.isHidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onToggleHidden(entry.id, !entry.isHidden)
                    },
                )
            }
        }
    }
}

/** Smooth curve through [points] that never overshoots between them (Fritsch–Carlson). */
private fun monotoneCurve(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points[0].x, points[0].y)
    val count = points.size
    if (count == 1) return path
    val dx = FloatArray(count - 1) { points[it + 1].x - points[it].x }
    val slopes =
        FloatArray(count - 1) { if (dx[it] == 0f) 0f else (points[it + 1].y - points[it].y) / dx[it] }
    val tangents =
        FloatArray(count) { index ->
            when {
                index == 0 -> slopes[0]
                index == count - 1 -> slopes[count - 2]
                slopes[index - 1] * slopes[index] <= 0f -> 0f
                else -> (slopes[index - 1] + slopes[index]) / 2f
            }
        }
    for (index in 0 until count - 1) {
        if (slopes[index] == 0f) {
            tangents[index] = 0f
            tangents[index + 1] = 0f
            continue
        }
        val a = tangents[index] / slopes[index]
        val b = tangents[index + 1] / slopes[index]
        val length = a * a + b * b
        if (length > 9f) {
            val scale = 3f / sqrt(length)
            tangents[index] = scale * a * slopes[index]
            tangents[index + 1] = scale * b * slopes[index]
        }
    }
    for (index in 0 until count - 1) {
        val from = points[index]
        val to = points[index + 1]
        val third = dx[index] / 3f
        path.cubicTo(
            from.x + third,
            from.y + tangents[index] * third,
            to.x - third,
            to.y - tangents[index + 1] * third,
            to.x,
            to.y,
        )
    }
    return path
}

private fun WeightChartModel.nearestMeasurementIndex(x: Float, plotLeft: Float, plotWidth: Float): Int? =
    measurements.indices.minByOrNull { index ->
        abs(plotLeft + xFraction(measurements[index].date.toEpochDays().toDouble()) * plotWidth - x)
    }

private fun chartDescription(model: WeightChartModel, range: WeightChartRange): String {
    val weights = model.measurements.map { it.weightKg }
    return "Gewichtsverlauf, ${range.label}: ${weights.size} Messungen zwischen " +
        "${formatWeight(weights.minOrNull())} und ${formatWeight(weights.maxOrNull())}"
}

private fun calculateBmi(weightKg: Double?, heightCm: Double?): Double? {
    val weight = weightKg?.takeIf { it > 0.0 } ?: return null
    val heightM = heightCm?.takeIf { it > 0.0 }?.div(100.0) ?: return null
    return weight / (heightM * heightM)
}

private fun bmiCategoryLabel(bmi: Double): String =
    when {
        bmi < 18.5 -> "Untergewicht"
        bmi < 25.0 -> "Normalgewicht"
        bmi < 30.0 -> "Übergewicht"
        else -> "Adipositas"
    }

private fun formatWeight(value: Double?): String =
    value?.let { "${formatWeightInput(it)} kg" } ?: "-"

private fun formatHeroWeight(value: Double?) =
    buildAnnotatedString {
        if (value == null) {
            append("-")
        } else {
            append(formatWeightInput(value))
            withStyle(SpanStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium, color = ReportMutedText)) {
                append(" kg")
            }
        }
    }

private fun formatWeightInput(value: Double?): String =
    value?.let { (round(it * 10.0) / 10.0).toString().replace('.', ',') } ?: ""

private fun formatOneDecimal(value: Double): String =
    (round(value * 10.0) / 10.0).toString().replace('.', ',')

private fun formatAxisWeight(value: Double): String =
    if (value == round(value)) value.roundToInt().toString() else formatOneDecimal(value)

private fun formatWeightChangeSinceStart(deltaKg: Double): String {
    val value = formatWeightInput(abs(deltaKg))
    return if (deltaKg < 0) "$value kg abgenommen" else "$value kg zugenommen"
}

private fun formatSignedWeightChange(deltaKg: Double): String {
    val sign = if (deltaKg > 0) "+" else "-"
    return "$sign${formatWeightInput(abs(deltaKg))} kg"
}

private fun formatShortDate(date: LocalDate): String =
    "${germanWeekdayShort(date.dayOfWeek)}, ${date.day}. ${germanMonthShort(date.month.number)}"

private fun formatMonthHeader(date: LocalDate): String = "${germanMonthLong(date.month.number)} ${date.year}"

private fun formatChartTick(date: LocalDate, shortRange: Boolean): String =
    when {
        shortRange -> "${date.day}. ${germanMonthShort(date.month.number)}"
        date.month == Month.JANUARY -> date.year.toString()
        else -> germanMonthShort(date.month.number)
    }

private fun germanMonthLong(month: Int): String =
    when (month) {
        1 -> "Januar"
        2 -> "Februar"
        3 -> "März"
        4 -> "April"
        5 -> "Mai"
        6 -> "Juni"
        7 -> "Juli"
        8 -> "August"
        9 -> "September"
        10 -> "Oktober"
        11 -> "November"
        12 -> "Dezember"
        else -> ""
    }

private fun germanMonthShort(month: Int): String =
    when (month) {
        1 -> "Jan."
        2 -> "Feb."
        3 -> "März"
        4 -> "Apr."
        5 -> "Mai"
        6 -> "Juni"
        7 -> "Juli"
        8 -> "Aug."
        9 -> "Sep."
        10 -> "Okt."
        11 -> "Nov."
        12 -> "Dez."
        else -> ""
    }

private fun germanWeekdayShort(dayOfWeek: kotlinx.datetime.DayOfWeek): String =
    when (dayOfWeek) {
        kotlinx.datetime.DayOfWeek.MONDAY -> "Mo"
        kotlinx.datetime.DayOfWeek.TUESDAY -> "Di"
        kotlinx.datetime.DayOfWeek.WEDNESDAY -> "Mi"
        kotlinx.datetime.DayOfWeek.THURSDAY -> "Do"
        kotlinx.datetime.DayOfWeek.FRIDAY -> "Fr"
        kotlinx.datetime.DayOfWeek.SATURDAY -> "Sa"
        kotlinx.datetime.DayOfWeek.SUNDAY -> "So"
    }

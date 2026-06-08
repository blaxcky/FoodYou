package com.maksimowiczm.foodyou.app.ui.weight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.weight.domain.entity.DailyWeightEntry
import com.maksimowiczm.foodyou.weight.domain.usecase.calculateWeightGoalProgress
import kotlin.math.abs
import kotlin.math.round
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

private val ReportBackground = Color(0xFFEAF5FC)
private val ReportPrimary = Color(0xFF1F6FB7)
private val ReportPrimaryDark = Color(0xFF175C9E)
private val ReportText = Color(0xFF17212B)
private val ReportMutedText = Color(0xFF718292)
private val ReportGrid = Color(0xFFD7E2EB)
private val ReportSoftButton = Color(0xFFD8ECFA)
private val ReportCardShape = RoundedCornerShape(26.dp)
private const val WeightAdjustRepeatStartMillis = 550L
private const val WeightAdjustRepeatMillis = 175L

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
        onMinus = { viewModel.adjustWeight(-0.1) },
        onPlus = { viewModel.adjustWeight(0.1) },
        onHealthConnectClick = permissionRequester::request,
        modifier = modifier,
    )
}

@Composable
internal fun WeightReportContent(
    state: WeightReportUiState,
    onBack: () -> Unit,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onHealthConnectClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier, containerColor = ReportBackground) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues).background(ReportBackground),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Surface(color = Color.White, tonalElevation = 0.dp, shadowElevation = 0.dp) {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        WeightReportHeader(onBack = onBack)
                        WeightTabs()
                        WeightChart(
                            entries = state.chartEntries,
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
                    onMinus = onMinus,
                    onPlus = onPlus,
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
                        heightCm = state.heightCm,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WeightReportHeader(onBack: () -> Unit, modifier: Modifier = Modifier) {
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
                text = "1 Jahr",
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
        HorizontalDivider(color = Color(0xFFEAF0F5))
    }
}

@Composable
private fun WeightChart(
    entries: List<DailyWeightEntry>,
    targetWeightKg: Double?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(top = 18.dp, bottom = 24.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(
                modifier = Modifier.width(42.dp).height(188.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                chartWeightLabels(entries, targetWeightKg).forEach { label ->
                    Text(
                        text = formatWeightInput(label),
                        style = MaterialTheme.typography.labelSmall,
                        color = ReportMutedText,
                    )
                }
            }
            Canvas(modifier = Modifier.weight(1f).height(188.dp)) {
                val labels = chartWeightLabels(entries, targetWeightKg)
                val minWeight = labels.last()
                val maxWeight = labels.first()
                val minDate = entries.minOfOrNull { it.date.toEpochDays() } ?: 0
                val maxDate =
                    (entries.maxOfOrNull { it.date.toEpochDays() } ?: (minDate + 1))
                        .coerceAtLeast(minDate + 1)
                val weightRange = (maxWeight - minWeight).coerceAtLeast(0.1)

                repeat(5) { index ->
                    val y = size.height * index / 4f
                    drawLine(
                        color = ReportGrid,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(),
                    )
                }

                targetWeightKg?.let { target ->
                    val y = size.height - (((target - minWeight) / weightRange).toFloat() * size.height)
                    drawLine(
                        color = ReportPrimary.copy(alpha = 0.55f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 8.dp.toPx())),
                    )
                }

                if (entries.size >= 2) {
                    val points =
                        entries.sortedBy { it.date }.map { entry ->
                            val x =
                                ((entry.date.toEpochDays() - minDate).toFloat() /
                                    (maxDate - minDate)) * size.width
                            val y =
                                size.height -
                                    (((entry.weightKg - minWeight) / weightRange).toFloat() *
                                        size.height)
                            Offset(x, y)
                        }
                    points.zipWithNext().forEach { (a, b) ->
                        drawLine(
                            color = ReportPrimary,
                            start = a,
                            end = b,
                            strokeWidth = 4.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 50.dp, top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            chartDateLabels(entries).forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = ReportMutedText,
                )
            }
        }
    }
}

@Composable
private fun CurrentWeightCard(
    startWeightKg: Double?,
    currentWeightKg: Double?,
    suggestedWeightKg: Double?,
    progressWeightKg: Double?,
    targetWeightKg: Double?,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayWeight = currentWeightKg ?: suggestedWeightKg
    val progress = calculateWeightGoalProgress(startWeightKg, progressWeightKg, targetWeightKg)
    val startDelta = progressWeightKg?.let { current -> startWeightKg?.let { current - it } }
    Surface(
        modifier = modifier,
        shape = ReportCardShape,
        color = Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Dein Gewicht: ${formatWeight(displayWeight)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = ReportText,
                        fontWeight = FontWeight.Bold,
                    )
                    if (startDelta != null && startDelta != 0.0) {
                        Text(
                            text = formatWeightChangeSinceStart(startDelta),
                            style = MaterialTheme.typography.bodyMedium,
                            color = ReportMutedText,
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    WeightAdjustButton(
                        icon = { Icon(Icons.Filled.Remove, contentDescription = "0,1 kg abziehen") },
                        enabled = suggestedWeightKg != null,
                        onClick = onMinus,
                    )
                    Text(
                        text = "0,1 kg",
                        style = MaterialTheme.typography.labelLarge,
                        color = ReportText,
                        textAlign = TextAlign.Center,
                    )
                    WeightAdjustButton(
                        icon = { Icon(Icons.Filled.Add, contentDescription = "0,1 kg addieren") },
                        enabled = suggestedWeightKg != null,
                        onClick = onPlus,
                    )
                }
            }
            WeightGoalProgress(
                startWeightKg = startWeightKg,
                targetWeightKg = targetWeightKg,
                progress = progress,
            )
        }
    }
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
                .semantics {
                    role = Role.Button
                    if (!enabled) disabled()
                    onClick {
                        if (enabled) currentOnClick()
                        enabled
                    }
                },
        shape = RoundedCornerShape(19.dp),
        color = if (enabled) ReportSoftButton else Color(0xFFEAF0F5),
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
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column {
                    Text(
                        text = "BMI",
                        style = MaterialTheme.typography.titleMedium,
                        color = ReportText,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = bmiCategoryLabel(bmi),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ReportMutedText,
                    )
                }
                Text(
                    text = formatOneDecimal(bmi),
                    style = MaterialTheme.typography.headlineSmall,
                    color = ReportText,
                    fontWeight = FontWeight.Bold,
                )
            }
            BmiScale(
                bmi = bmi,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun BmiScale(bmi: Double, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(modifier = Modifier.fillMaxWidth().height(28.dp)) {
            val minBmi = 15.0
            val maxBmi = 40.0
            val markerBmi = bmi.coerceIn(minBmi, maxBmi)
            val y = size.height / 2f
            val strokeWidth = 12.dp.toPx()

            fun xFor(value: Double): Float =
                (((value - minBmi) / (maxBmi - minBmi)).toFloat() * size.width)

            drawLine(
                color = Color(0xFFF0C84B),
                start = Offset(0f, y),
                end = Offset(xFor(18.5), y),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Color(0xFF53B96A),
                start = Offset(xFor(18.5), y),
                end = Offset(xFor(25.0), y),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Butt,
            )
            drawLine(
                color = Color(0xFFF0C84B),
                start = Offset(xFor(25.0), y),
                end = Offset(xFor(30.0), y),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Butt,
            )
            drawLine(
                color = Color(0xFFE05A4F),
                start = Offset(xFor(30.0), y),
                end = Offset(size.width, y),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )

            val markerX = xFor(markerBmi)
            drawLine(
                color = Color.Black,
                start = Offset(markerX, y - 13.dp.toPx()),
                end = Offset(markerX, y + 13.dp.toPx()),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("15", style = MaterialTheme.typography.labelSmall, color = ReportMutedText)
            Text("18,5", style = MaterialTheme.typography.labelSmall, color = ReportMutedText)
            Text("25", style = MaterialTheme.typography.labelSmall, color = ReportMutedText)
            Text("30", style = MaterialTheme.typography.labelSmall, color = ReportMutedText)
            Text("40", style = MaterialTheme.typography.labelSmall, color = ReportMutedText)
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
    startWeightKg: Double?,
    targetWeightKg: Double?,
    progress: Float?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LinearProgressIndicator(
            progress = { progress ?: 0f },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = if (progress == null) ReportSoftButton else ReportPrimary,
            trackColor = ReportSoftButton,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatWeight(startWeightKg),
                style = MaterialTheme.typography.labelMedium,
                color = ReportMutedText,
            )
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
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

@Composable
private fun HistoryCard(
    entries: List<DailyWeightEntry>,
    heightCm: Double?,
    modifier: Modifier = Modifier,
) {
    val changesByDate = remember(entries) {
        entries
            .zipWithNext()
            .associate { (newer, older) -> newer.date to newer.weightKg - older.weightKg }
    }
    Surface(
        modifier = modifier,
        shape = ReportCardShape,
        color = Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            entries.forEachIndexed { index, entry ->
                HistoryRow(
                    entry = entry,
                    changeKg = changesByDate[entry.date],
                    heightCm = heightCm,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (index != entries.lastIndex) {
                    HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = Color(0xFFEAF0F5))
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    entry: DailyWeightEntry,
    changeKg: Double?,
    heightCm: Double?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatGermanDate(entry.date),
                style = MaterialTheme.typography.bodyLarge,
                color = ReportText,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = germanWeekday(entry.date.dayOfWeek),
                style = MaterialTheme.typography.bodySmall,
                color = ReportMutedText,
            )
        }
        Column(modifier = Modifier.width(54.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val bmi = calculateBmi(entry.weightKg, heightCm)
            if (bmi != null) {
                Text(
                    text = formatOneDecimal(bmi),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ReportText,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "BMI",
                    style = MaterialTheme.typography.labelSmall,
                    color = ReportMutedText,
                )
            }
        }
        Column(modifier = Modifier.width(70.dp), horizontalAlignment = Alignment.End) {
            Text(
                formatWeight(entry.weightKg),
                style = MaterialTheme.typography.titleMedium,
                color = ReportText,
                fontWeight = FontWeight.Bold,
            )
            if (changeKg != null && changeKg != 0.0) {
                Text(
                    text = formatSignedWeightChange(changeKg),
                    style = MaterialTheme.typography.bodySmall,
                    color = ReportMutedText,
                )
            }
        }
    }
}

private fun chartWeightLabels(entries: List<DailyWeightEntry>, targetWeightKg: Double?): List<Double> {
    val values = entries.map { it.weightKg } + listOfNotNull(targetWeightKg)
    val min = values.minOrNull() ?: 80.0
    val max = values.maxOrNull() ?: 110.0
    val lower = kotlin.math.floor((min - 1.0) / 5.0) * 5.0
    val upper = kotlin.math.ceil((max + 1.0) / 5.0) * 5.0
    val step = ((upper - lower) / 4.0).coerceAtLeast(1.0)
    return List(5) { index -> upper - step * index }
}

@Suppress("DEPRECATION")
private fun chartDateLabels(entries: List<DailyWeightEntry>): List<String> {
    if (entries.isEmpty()) return listOf("", "", "")
    val sorted = entries.sortedBy { it.date }
    return listOf(sorted.first(), sorted[sorted.lastIndex / 2], sorted.last()).map { entry ->
        "${germanMonthShort(entry.date.monthNumber)} ${entry.date.year}"
    }
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

private fun formatWeightInput(value: Double?): String =
    value?.let { (round(it * 10.0) / 10.0).toString().replace('.', ',') } ?: ""

private fun formatOneDecimal(value: Double): String =
    (round(value * 10.0) / 10.0).toString().replace('.', ',')

private fun formatWeightChangeSinceStart(deltaKg: Double): String {
    val value = formatWeightInput(abs(deltaKg))
    return if (deltaKg < 0) "$value kg abgenommen" else "$value kg zugenommen"
}

private fun formatSignedWeightChange(deltaKg: Double): String {
    val sign = if (deltaKg > 0) "+" else "-"
    return "$sign${formatWeightInput(abs(deltaKg))} kg"
}

@Suppress("DEPRECATION")
private fun formatGermanDate(date: kotlinx.datetime.LocalDate): String {
    val day = date.dayOfMonth.toString().padStart(2, '0')
    return "$day. ${germanMonthLong(date.monthNumber)} ${date.year}"
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

private fun germanWeekday(dayOfWeek: kotlinx.datetime.DayOfWeek): String =
    when (dayOfWeek) {
        kotlinx.datetime.DayOfWeek.MONDAY -> "Montag"
        kotlinx.datetime.DayOfWeek.TUESDAY -> "Dienstag"
        kotlinx.datetime.DayOfWeek.WEDNESDAY -> "Mittwoch"
        kotlinx.datetime.DayOfWeek.THURSDAY -> "Donnerstag"
        kotlinx.datetime.DayOfWeek.FRIDAY -> "Freitag"
        kotlinx.datetime.DayOfWeek.SATURDAY -> "Samstag"
        kotlinx.datetime.DayOfWeek.SUNDAY -> "Sonntag"
    }

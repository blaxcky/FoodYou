package com.maksimowiczm.foodyou.app.ui.weight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.weight.domain.entity.DailyWeightEntry
import com.maksimowiczm.foodyou.weight.domain.usecase.calculateWeightGoalProgress
import kotlin.math.round
import org.koin.compose.viewmodel.koinViewModel

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
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Gewicht") },
                navigationIcon = { ArrowBackIconButton(onClick = onBack) },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(12.dp),
        ) {
            item {
                WeightChartCard(entries = state.chartEntries, modifier = Modifier.fillMaxWidth())
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
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.healthConnectAvailable && !state.healthConnectPermissionGranted) {
                item {
                    HealthConnectCard(
                        onClick = onHealthConnectClick,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                Text(
                    text = "Gewichtshistorie",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (state.entries.isNotEmpty()) {
                item {
                    HistoryCard(entries = state.entries, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun WeightChartCard(entries: List<DailyWeightEntry>, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Letzte 12 Monate", style = MaterialTheme.typography.titleMedium)
            val lineColor = MaterialTheme.colorScheme.primary
            val trackColor = MaterialTheme.colorScheme.surfaceVariant
            Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                drawLine(
                    color = trackColor,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
                if (entries.size >= 2) {
                    val minDate = entries.minOf { it.date.toEpochDays() }
                    val maxDate =
                        entries.maxOf { it.date.toEpochDays() }.coerceAtLeast(minDate + 1)
                    val minWeight = entries.minOf { it.weightKg }
                    val maxWeight = entries.maxOf { it.weightKg }.coerceAtLeast(minWeight + 0.1)
                    val points =
                        entries.map { entry ->
                            val x =
                                ((entry.date.toEpochDays() - minDate).toFloat() /
                                    (maxDate - minDate)) * size.width
                            val y =
                                size.height -
                                    (((entry.weightKg - minWeight) / (maxWeight - minWeight))
                                        .toFloat() * size.height)
                            Offset(x, y)
                        }
                    points.zipWithNext().forEach { (a, b) ->
                        drawLine(
                            color = lineColor,
                            start = a,
                            end = b,
                            strokeWidth = stroke.width,
                            cap = StrokeCap.Round,
                        )
                    }
                }
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
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Dein Gewicht: ${formatWeight(displayWeight)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (startDelta != null && startDelta != 0.0) {
                        Text(
                            text = formatWeightChangeSinceStart(startDelta),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilledTonalIconButton(onClick = onMinus, enabled = suggestedWeightKg != null) {
                        Icon(Icons.Filled.Remove, contentDescription = "0,1 kg abziehen")
                    }
                    Text(
                        text = "0,1 kg",
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                    )
                    FilledTonalIconButton(onClick = onPlus, enabled = suggestedWeightKg != null) {
                        Icon(Icons.Filled.Add, contentDescription = "0,1 kg addieren")
                    }
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
private fun HealthConnectCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.MonitorWeight, contentDescription = null)
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
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LinearProgressIndicator(
            progress = { progress ?: 0f },
            modifier = Modifier.fillMaxWidth(),
            color =
                if (progress == null) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.primary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatWeight(startWeightKg),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = formatWeight(targetWeightKg),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HistoryCard(entries: List<DailyWeightEntry>, modifier: Modifier = Modifier) {
    val changesByDate = remember(entries) {
        entries
            .zipWithNext()
            .associate { (newer, older) -> newer.date to newer.weightKg - older.weightKg }
    }
    Card(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            entries.forEachIndexed { index, entry ->
                HistoryRow(
                    entry = entry,
                    changeKg = changesByDate[entry.date],
                    modifier = Modifier.fillMaxWidth(),
                )
                if (index != entries.lastIndex) {
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: DailyWeightEntry, changeKg: Double?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier.size(40.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.MonitorWeight, contentDescription = null)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.date.toString(), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = germanWeekday(entry.date.dayOfWeek),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatWeight(entry.weightKg),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (changeKg != null && changeKg != 0.0) {
                Text(
                    text = formatSignedWeightChange(changeKg),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatWeight(value: Double?): String =
    value?.let { "${formatWeightInput(it)} kg" } ?: "-"

private fun formatWeightInput(value: Double?): String =
    value?.let { (round(it * 10.0) / 10.0).toString().replace('.', ',') } ?: ""

private fun formatWeightChangeSinceStart(deltaKg: Double): String {
    val value = formatWeightInput(kotlin.math.abs(deltaKg))
    return if (deltaKg < 0) "$value kg abgenommen" else "$value kg zugenommen"
}

private fun formatSignedWeightChange(deltaKg: Double): String {
    val sign = if (deltaKg > 0) "+" else "-"
    return "$sign${formatWeightInput(kotlin.math.abs(deltaKg))} kg"
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

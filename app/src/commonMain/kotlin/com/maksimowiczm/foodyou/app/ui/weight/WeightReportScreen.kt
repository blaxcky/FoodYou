package com.maksimowiczm.foodyou.app.ui.weight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
                    currentWeightKg = state.todayWeightKg,
                    suggestedWeightKg = state.suggestedWeightKg,
                    onWeightChange = viewModel::setWeight,
                    onMinus = { viewModel.adjustWeight(-0.1) },
                    onPlus = { viewModel.adjustWeight(0.1) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.healthConnectAvailable && !state.healthConnectPermissionGranted) {
                item {
                    HealthConnectCard(
                        onClick = permissionRequester::request,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                GoalCard(
                    startWeightKg = state.startWeightKg,
                    currentWeightKg = state.currentWeightKg,
                    targetWeightKg = state.targetWeightKg,
                    onTargetWeightChange = viewModel::setTargetWeight,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(
                    text = "Gewichtshistorie",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(state.entries, key = { it.date.toEpochDays() }) { entry ->
                HistoryRow(entry = entry, modifier = Modifier.fillMaxWidth())
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
    currentWeightKg: Double?,
    suggestedWeightKg: Double?,
    onWeightChange: (Double) -> Unit,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf(formatWeightInput(currentWeightKg ?: suggestedWeightKg)) }
    LaunchedEffect(currentWeightKg, suggestedWeightKg) {
        text = formatWeightInput(currentWeightKg ?: suggestedWeightKg)
    }
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Aktuelles Gewicht", style = MaterialTheme.typography.titleMedium)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = onMinus, enabled = suggestedWeightKg != null) {
                    Icon(Icons.Filled.Remove, contentDescription = "0,1 kg abziehen")
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        parseWeight(it)?.let(onWeightChange)
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    suffix = { Text("kg") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                IconButton(onClick = onPlus, enabled = suggestedWeightKg != null) {
                    Icon(Icons.Filled.Add, contentDescription = "0,1 kg addieren")
                }
            }
            if (currentWeightKg == null && suggestedWeightKg != null) {
                Text("Vorschlag aus dem letzten Eintrag", style = MaterialTheme.typography.bodySmall)
            }
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
private fun GoalCard(
    startWeightKg: Double?,
    currentWeightKg: Double?,
    targetWeightKg: Double?,
    onTargetWeightChange: (Double?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var targetText by remember { mutableStateOf(formatWeightInput(targetWeightKg)) }
    LaunchedEffect(targetWeightKg) { targetText = formatWeightInput(targetWeightKg) }
    val progress = calculateWeightGoalProgress(startWeightKg, currentWeightKg, targetWeightKg)
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Ziel", style = MaterialTheme.typography.titleMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Metric("Start", startWeightKg)
                Metric("Aktuell", currentWeightKg)
                Metric("Ziel", targetWeightKg)
            }
            LinearProgressIndicator(
                progress = { progress ?: 0f },
                modifier = Modifier.fillMaxWidth(),
                color =
                    if (progress == null) MaterialTheme.colorScheme.surfaceVariant
                    else MaterialTheme.colorScheme.primary,
            )
            OutlinedTextField(
                value = targetText,
                onValueChange = {
                    targetText = it
                    onTargetWeightChange(parseWeight(it))
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Ziel setzen") },
                suffix = { Text("kg") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }
    }
}

@Composable
private fun RowScope.Metric(label: String, weightKg: Double?) {
    Column(modifier = Modifier.weight(1f)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(
            text = formatWeight(weightKg),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun HistoryRow(entry: DailyWeightEntry, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.MonitorWeight, contentDescription = null)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.date.toString(), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = entry.date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(formatWeight(entry.weightKg), style = MaterialTheme.typography.titleMedium)
        }
    }
}

private fun parseWeight(value: String): Double? =
    value.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }

private fun formatWeight(value: Double?): String =
    value?.let { "${formatWeightInput(it)} kg" } ?: "-"

private fun formatWeightInput(value: Double?): String =
    value?.let { (round(it * 10.0) / 10.0).toString().replace('.', ',') } ?: ""

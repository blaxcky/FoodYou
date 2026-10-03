package com.maksimowiczm.foodyou.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.common.compose.utility.LocalClipboardManager
import com.maksimowiczm.foodyou.common.compose.utility.LocalDateFormatter
import com.maksimowiczm.foodyou.sync.SyncLogRun
import com.maksimowiczm.foodyou.sync.SyncLogStatus
import com.maksimowiczm.foodyou.sync.SyncLogStep
import foodyou.app.generated.resources.*
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SyncLogContent(
    runs: List<SyncLogRun>,
    onBack: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val title = stringResource(Res.string.headline_sync_log)
    var showClearDialog by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
    val running = runs.any { it.status == SyncLogStatus.Running }
    LaunchedEffect(running) {
        while (running) {
            now = Clock.System.now().toEpochMilliseconds()
            delay(1_000)
        }
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { ArrowBackIconButton(onBack) },
                actions = {
                    IconButton(onClick = { clipboard.copy(title, formatSyncLog(runs, now)) }, enabled = runs.isNotEmpty()) {
                        Icon(Icons.Filled.ContentCopy, stringResource(Res.string.action_copy))
                    }
                    IconButton(onClick = { showClearDialog = true }, enabled = runs.any { it.status != SyncLogStatus.Running }) {
                        Icon(Icons.Outlined.DeleteSweep, stringResource(Res.string.action_clear))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(stringResource(Res.string.description_sync_log), Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (runs.isEmpty()) item {
                Text(stringResource(Res.string.neutral_sync_log_empty), Modifier.padding(16.dp))
            }
            items(runs.asReversed(), key = { it.id }) { run ->
                SyncLogRunCard(run, now, initiallyExpanded = run.id == runs.lastOrNull()?.id)
            }
        }
    }
    if (showClearDialog) AlertDialog(
        onDismissRequest = { showClearDialog = false },
        title = { Text(stringResource(Res.string.headline_sync_log_clear)) },
        text = { Text(stringResource(Res.string.description_sync_log_clear)) },
        confirmButton = { TextButton(onClick = { showClearDialog = false; onClear() }) {
            Text(stringResource(Res.string.action_clear))
        } },
        dismissButton = { TextButton(onClick = { showClearDialog = false }) {
            Text(stringResource(Res.string.action_cancel))
        } },
    )
}

@Composable
private fun SyncLogRunCard(run: SyncLogRun, now: Long, initiallyExpanded: Boolean) {
    var expanded by rememberSaveable(run.id) { mutableStateOf(initiallyExpanded) }
    OutlinedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                Text(run.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    stringResource(if (expanded) Res.string.action_sync_log_collapse else Res.string.action_sync_log_expand))
            }
            Text(LocalDateFormatter.current.formatDateTime(
                Instant.fromEpochMilliseconds(run.startedAtMillis).toLocalDateTime(TimeZone.currentSystemDefault())),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${run.status.label()} · ${runDuration(run, now)}", style = MaterialTheme.typography.labelLarge,
                color = if (run.status == SyncLogStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }
        if (expanded) {
            HorizontalDivider()
            SelectionContainer {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    run.steps.forEach { step ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${step.number}. ${step.title}", style = MaterialTheme.typography.titleSmall)
                            Text(stepTiming(step, now), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${step.status.label()}${step.detail.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (step.status == SyncLogStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    if (run.omittedSteps > 0) Text("${run.omittedSteps} weitere Detail-Schritte nicht gespeichert (Limit: 200).",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

internal fun formatSyncDuration(millis: Long): String {
    val duration = millis.coerceAtLeast(0)
    if (duration < 1_000) return "$duration ms"
    val seconds = duration / 1_000
    val fraction = (duration % 1_000) / 100
    return if (seconds < 60) "$seconds,$fraction s" else "${seconds / 60} min ${seconds % 60},$fraction s"
}

private fun SyncLogStatus.label(): String = when (this) {
    SyncLogStatus.Running -> "Läuft"
    SyncLogStatus.Success -> "Abgeschlossen"
    SyncLogStatus.Skipped -> "Übersprungen"
    SyncLogStatus.Failed -> "Fehler"
    SyncLogStatus.Cancelled -> "Abgebrochen"
    SyncLogStatus.Interrupted -> "Durch App-Neustart unterbrochen"
}

private fun runDuration(run: SyncLogRun, now: Long): String =
    run.durationMillis?.let(::formatSyncDuration)
        ?: if (run.status == SyncLogStatus.Running) formatSyncDuration(now - run.startedAtMillis) else "Dauer unbekannt"

private fun stepTiming(step: SyncLogStep, now: Long): String {
    val duration = step.durationMillis?.let(::formatSyncDuration)
        ?: if (step.status == SyncLogStatus.Running) formatSyncDuration(now - step.startedAtMillis) else "unbekannt"
    return "Start ${syncLogTime(step.startedAtMillis)} · +${formatSyncDuration(step.offsetMillis)}\n" +
        "Dauer $duration" + (step.parentNumber?.let { " · Teil von Schritt $it" } ?: "")
}

private fun syncLogTime(millis: Long): String {
    val time = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault()).time
    return "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}:" +
        "${time.second.toString().padStart(2, '0')}.${(time.nanosecond / 1_000_000).toString().padStart(3, '0')}"
}

internal fun formatSyncLog(runs: List<SyncLogRun>, now: Long): String = buildString {
    appendLine("FoodYou Sync-Protokoll")
    appendLine("Schritte in Startreihenfolge. Zeitversatz (+) ab Laufbeginn. Schritte können parallel laufen; Dauern überlappen.")
    runs.forEach { run ->
        appendLine()
        appendLine("${Instant.fromEpochMilliseconds(run.startedAtMillis).toLocalDateTime(TimeZone.currentSystemDefault())} · ${run.title}")
        appendLine("${run.status.label()} · Gesamtdauer ${runDuration(run, now)}")
        run.steps.forEach { step ->
            appendLine("${step.number}. ${step.title}")
            appendLine("   ${stepTiming(step, now).replace("\n", " · ")}")
            appendLine("   ${step.status.label()}${step.detail.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()}")
        }
        if (run.omittedSteps > 0) appendLine("${run.omittedSteps} weitere Detail-Schritte nicht gespeichert (Limit: 200).")
    }
}

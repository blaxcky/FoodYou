package com.maksimowiczm.foodyou.ai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun AiSettingsScreen(onBack: () -> Unit) {
    val controller: AiController = koinInject()
    val settings by controller.settings.collectAsStateWithLifecycle()
    val downloads by controller.downloads.collectAsStateWithLifecycle()
    val analysis by controller.analysis.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var report by remember { mutableStateOf<String?>(null) }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    report?.let { text -> AiDiagnosticDialog(text, { clipboard.setText(androidx.compose.ui.text.AnnotatedString(text)) }, { report = null }) }
    var saving by remember { mutableStateOf(false) }
    AiSettingsContent(settings, downloads, analysis.running || saving, message,
        onBack = onBack,
        onSave = { provider, model, visionOnCpu, key, delete ->
            saving = true
            scope.launch {
                try {
                    controller.saveSettings(provider, model, visionOnCpu, key, delete)
                    message = "Einstellungen gespeichert."
                } catch (e: CancellationException) { throw e
                } catch (_: Exception) { message = "Speichern fehlgeschlagen. Modellname und API-Key prüfen."
                } finally { saving = false }
            }
        },
        onTest = {
            saving = true
            scope.launch {
                try { message = controller.testConnection() } finally { saving = false }
            }
        },
        onDownload = controller::startDownload,
        onPause = controller::pauseDownload,
        onDelete = controller::deleteModel,
        onDiagnostics = { scope.launch { report = controller.diagnosticReport() } },
    )
}

@Composable
internal fun AiSettingsContent(
    settings: AiSettings,
    downloads: Map<AiProvider, ModelDownloadState>,
    busy: Boolean,
    message: String?,
    onBack: () -> Unit,
    onSave: (provider: AiProvider, model: String, visionOnCpu: Boolean, newKey: String?, deleteKey: Boolean) -> Unit,
    onTest: () -> Unit,
    onDownload: (AiProvider) -> Unit,
    onPause: () -> Unit,
    onDelete: (AiProvider) -> Unit,
    onDiagnostics: () -> Unit = {},
) {
    var provider by rememberSaveable(settings.provider) { mutableStateOf(settings.provider) }
    var model by rememberSaveable(settings.model) { mutableStateOf(settings.model) }
    var visionOnCpu by rememberSaveable(settings.visionOnCpu) { mutableStateOf(settings.visionOnCpu) }
    // A plaintext API key is deliberately neither saveable nor persisted in UI state bundles.
    var key by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    val localModel = provider.localModel
    val download = downloads[provider] ?: ModelDownloadState(total = localModel?.size ?: GemmaModel.E4B.size)
    val dirty = provider != settings.provider || model != settings.model ||
        visionOnCpu != settings.visionOnCpu || key.isNotEmpty()
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Gemma-Modell löschen?") },
        text = { Text("Für die nächste lokale Analyse muss das Modell erneut heruntergeladen werden.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete(provider) }) { Text("Löschen") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
    )
    Scaffold(topBar = { TopAppBar(title = { Text("KI") }, navigationIcon = { ArrowBackIconButton(onBack) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Gewichte von Waagenfotos erkennen", style = MaterialTheme.typography.titleMedium)
            Text("Alle Ergebnisse sind Vorschläge. Du bestätigst oder korrigierst jedes Gewicht in der Schnellerfassung.")
            AiProvider.entries.forEach { value ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RadioButton(selected = provider == value, onClick = { provider = value }, enabled = !busy && !download.running)
                    Text(value.label)
                }
            }
            if (localModel != null) {
                Text("${localModel.displayName} · etwa ${localModel.approximateSize} GB", style = MaterialTheme.typography.titleMedium)
                Text("Nach dem Download bleiben die Fotos auf deinem Smartphone. Die Analyse benötigt freien Arbeitsspeicher und läuft nur bei geöffneter App.")
                Text(when {
                    download.ready -> "Modell bereit"
                    download.verifying -> "Prüfsumme wird kontrolliert …"
                    else -> "${download.bytes / 1_000_000} / ${download.total / 1_000_000} MB"
                })
                if (download.running) {
                    LinearProgressIndicator(progress = { (download.bytes.toDouble() / download.total).toFloat() }, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(onClick = onPause) { Text("Pausieren") }
                } else {
                    if (!download.ready) Button(onClick = { onDownload(provider) }, enabled = !busy) {
                        Text(if (download.bytes > 0) "Download fortsetzen" else "Gemma herunterladen")
                    }
                    if (download.ready || download.bytes > 0) TextButton(onClick = { confirmDelete = true }, enabled = !busy) { Text("Modell und Download löschen") }
                }
                download.message?.let { Text(it) }
                Text("Der Download pausiert beim Verlassen der App. Modellquelle: LiteRT Community / Google, Apache 2.0.", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Bildanalyse auf der CPU")
                        Text("Langsamer. Zum Vergleich, falls Anzeigen mit der Grafikeinheit nicht erkannt werden.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = visionOnCpu, onCheckedChange = { visionOnCpu = it }, enabled = !busy)
                }
            } else {
                Text("Bei der Analyse werden die Fotos an Google gesendet. Es gilt das Kontingent deines API-Keys; API-Nutzung kann Kosten verursachen.")
                OutlinedTextField(value = key, onValueChange = { key = it },
                    label = { Text(if (settings.hasApiKey) "API-Key ersetzen (gespeichert)" else "Google-AI-Studio-API-Key") },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                        autoCorrectEnabled = false,
                    ),
                    enabled = !busy, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Gemini-Modell") },
                    singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = onTest, enabled = !busy && !dirty && settings.hasApiKey) { Text("Verbindung testen") }
                if (dirty) Text("Vor dem Verbindungstest bitte speichern.", style = MaterialTheme.typography.bodySmall)
                if (settings.hasApiKey) TextButton(onClick = { key = ""; onSave(provider, model, visionOnCpu, null, true) }, enabled = !busy) { Text("API-Key entfernen") }
            }
            Button(onClick = {
                onSave(provider, model, visionOnCpu, key.takeIf { it.isNotBlank() }, false)
                key = ""
            }, enabled = !busy && !download.running && model.isNotBlank()) { Text("Einstellungen speichern") }
            OutlinedButton(onClick = onDiagnostics) { Text("KI-Diagnosebericht") }
            message?.let { Text(it) }
        }
    }
}

/** Slim status line above the photo grid; the primary action lives in [AiAnalysisActionButton]. */
@Composable
internal fun AiAnalysisControls(
    provider: AiProvider,
    progress: AnalysisProgress,
    canStart: Boolean,
    hasPhotos: Boolean,
    onReanalyze: () -> Unit,
    onSettings: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        if (progress.running) {
            val fraction = if (progress.total > 0) progress.completed.toFloat() / progress.total else 0f
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(3.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Outlined.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            val status = when {
                progress.running ->
                    "Analysiere · ${progress.completed} von ${progress.total} · ${progress.recognized} erkannt"
                !canStart -> "${provider.label} · nicht eingerichtet"
                progress.total > 0 ->
                    "${provider.label} · ${progress.recognized} von ${progress.total} erkannt"
                else -> provider.label
            }
            Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (provider == AiProvider.Gemini) {
                    Text(
                        "Analysierte Fotos werden an Google gesendet",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (progress.paused && !progress.running) {
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Angehalten",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Weitere KI-Optionen")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Alle offenen Fotos erneut analysieren") },
                        leadingIcon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
                        enabled = hasPhotos && canStart && !progress.running,
                        onClick = {
                            menuOpen = false
                            onReanalyze()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("KI-Einstellungen") },
                        leadingIcon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onSettings()
                        },
                    )
                }
            }
        }
        progress.message?.let {
            Text(
                it,
                modifier = Modifier.padding(start = 40.dp, end = 16.dp, bottom = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

/** Extended FAB next to the camera button: starts or cancels weight recognition. */
@Composable
internal fun AiAnalysisActionButton(
    progress: AnalysisProgress,
    canStart: Boolean,
    hasPhotos: Boolean,
    onStart: () -> Unit,
    onCancel: () -> Unit,
) {
    when {
        progress.running ->
            ExtendedFloatingActionButton(
                onClick = onCancel,
                icon = { Icon(Icons.Outlined.Stop, contentDescription = null) },
                text = { Text("Abbrechen") },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        hasPhotos && canStart ->
            ExtendedFloatingActionButton(
                onClick = onStart,
                icon = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null) },
                text = { Text("Gewichte erkennen") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
    }
}

@Composable
internal fun AiDiagnosticDialog(report: String, onCopy: () -> Unit, onDismiss: () -> Unit) {
    val summary = remember(report) { summarizeAiDiagnosticReport(report) }
    var showDetails by remember(report) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("KI-Diagnose") },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Kurzauswertung", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
                Text(summary.headline, style = MaterialTheme.typography.titleMedium)
                summary.details.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                TextButton(onClick = { showDetails = !showDetails }, contentPadding = PaddingValues(0.dp)) {
                    Text(if (showDetails) "Technische Details ausblenden" else "Technische Details anzeigen")
                }
                if (showDetails) {
                    HorizontalDivider()
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(report, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onCopy) { Text("Bericht kopieren") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
    )
}

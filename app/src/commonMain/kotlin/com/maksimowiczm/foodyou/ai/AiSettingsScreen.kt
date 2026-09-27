package com.maksimowiczm.foodyou.ai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
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
    val download by controller.download.collectAsStateWithLifecycle()
    val analysis by controller.analysis.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var report by remember { mutableStateOf<String?>(null) }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    report?.let { text -> AiDiagnosticDialog(text, { clipboard.setText(androidx.compose.ui.text.AnnotatedString(text)) }, { report = null }) }
    var saving by remember { mutableStateOf(false) }
    AiSettingsContent(settings, download, analysis.running || saving, message,
        onBack = onBack,
        onSave = { provider, model, key, delete ->
            saving = true
            scope.launch {
                try {
                    controller.saveSettings(provider, model, key, delete)
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
    download: ModelDownloadState,
    busy: Boolean,
    message: String?,
    onBack: () -> Unit,
    onSave: (AiProvider, String, String?, Boolean) -> Unit,
    onTest: () -> Unit,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onDelete: () -> Unit,
    onDiagnostics: () -> Unit = {},
) {
    var provider by rememberSaveable(settings.provider) { mutableStateOf(settings.provider) }
    var model by rememberSaveable(settings.model) { mutableStateOf(settings.model) }
    // A plaintext API key is deliberately neither saveable nor persisted in UI state bundles.
    var key by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    val dirty = provider != settings.provider || model != settings.model || key.isNotEmpty()
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Gemma-Modell löschen?") },
        text = { Text("Für die nächste lokale Analyse muss das Modell erneut heruntergeladen werden.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Löschen") } },
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
            if (provider == AiProvider.Local) {
                Text("Gemma 4 E4B · etwa 3,66 GB", style = MaterialTheme.typography.titleMedium)
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
                    if (!download.ready) Button(onClick = onDownload, enabled = !busy) {
                        Text(if (download.bytes > 0) "Download fortsetzen" else "Gemma herunterladen")
                    }
                    if (download.ready || download.bytes > 0) TextButton(onClick = { confirmDelete = true }, enabled = !busy) { Text("Modell und Download löschen") }
                }
                download.message?.let { Text(it) }
                Text("Der Download pausiert beim Verlassen der App. Modellquelle: LiteRT Community / Google, Apache 2.0.", style = MaterialTheme.typography.bodySmall)
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
                if (settings.hasApiKey) TextButton(onClick = { key = ""; onSave(provider, model, null, true) }, enabled = !busy) { Text("API-Key entfernen") }
            }
            Button(onClick = {
                onSave(provider, model, key.takeIf { it.isNotBlank() }, false)
                key = ""
            }, enabled = !busy && !download.running && model.isNotBlank()) { Text("Einstellungen speichern") }
            OutlinedButton(onClick = onDiagnostics) { Text("KI-Diagnosebericht") }
            message?.let { Text(it) }
        }
    }
}

@Composable
internal fun AiAnalysisControls(
    provider: AiProvider,
    progress: AnalysisProgress,
    canStart: Boolean,
    hasPhotos: Boolean,
    onStart: () -> Unit,
    onReanalyze: () -> Unit,
    onCancel: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(provider.label, style = MaterialTheme.typography.labelLarge)
        if (provider == AiProvider.Gemini) Text("Die analysierten Fotos werden an Google gesendet.", style = MaterialTheme.typography.bodySmall)
        if (progress.running) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            TextButton(onClick = onCancel) { Text("Analyse abbrechen") }
        } else {
            Button(onClick = onStart, enabled = hasPhotos && canStart) { Text("Gewichte mit KI erkennen") }
            if (hasPhotos && canStart) TextButton(onClick = onReanalyze) { Text("Alle offenen Fotos erneut analysieren") }
            if (!canStart) Text("Bitte zuerst das Modell herunterladen oder den API-Key einrichten.")
            TextButton(onClick = onSettings) { Text("KI-Einstellungen") }
        }
        if (progress.total > 0) Text("${progress.completed} von ${progress.total} Fotos – ${progress.recognized} Gewichte erkannt")
        progress.message?.let { Text(it) }
    }
}

@Composable
internal fun AiDiagnosticDialog(report: String, onCopy: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("KI-Diagnosebericht") },
        text = {
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(report, Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onCopy) { Text("Kopieren") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
    )
}

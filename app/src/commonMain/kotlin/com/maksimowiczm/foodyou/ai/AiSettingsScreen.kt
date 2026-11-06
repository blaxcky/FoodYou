package com.maksimowiczm.foodyou.ai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Scale
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.Troubleshoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import foodyou.app.generated.resources.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Unsaved edits of the AI screen. New API keys only live here, never in saveable UI state. */
internal data class AiSettingsDraft(
    val provider: AiProvider,
    val geminiModel: String,
    val visionOnCpu: Boolean,
    val openAi: OpenAiSettings,
    val claude: ClaudeSettings,
    val nutritionProvider: NutritionProvider,
    val backgroundPhotoAnalysisEnabled: Boolean = false,
    val geminiKey: String? = null,
    val openAiKey: String? = null,
    val claudeKey: String? = null,
)

/** Survives recreation without the new API keys, which never enter the saved instance state. */
private val AiSettingsDraftSaver = listSaver<AiSettingsDraft, Any?>(
    save = {
        listOf(it.provider.name, it.geminiModel, it.visionOnCpu, it.nutritionProvider.name,
            it.openAi.photo.model, it.openAi.photo.effort, it.openAi.nutrition.model, it.openAi.nutrition.effort,
            it.openAi.photoParallelism, it.claude.photo.model, it.claude.photo.effort, it.claude.nutrition.model,
            it.claude.nutrition.effort, it.claude.photoParallelism, it.backgroundPhotoAnalysisEnabled)
    },
    restore = { v ->
        AiSettingsDraft(
            provider = AiProvider.valueOf(v[0] as String),
            geminiModel = v[1] as String,
            visionOnCpu = v[2] as Boolean,
            openAi = OpenAiSettings(OpenAiTask(v[4] as String, v[5] as String?), OpenAiTask(v[6] as String, v[7] as String?),
                photoParallelism = v[8] as Int),
            claude = ClaudeSettings(ClaudeTask(v[9] as String, v[10] as String?), ClaudeTask(v[11] as String, v[12] as String?),
                photoParallelism = v[13] as Int),
            nutritionProvider = NutritionProvider.valueOf(v[3] as String),
            backgroundPhotoAnalysisEnabled = v.getOrNull(14) as? Boolean ?: false,
        )
    },
)

internal fun AiSettings.toDraft() = AiSettingsDraft(
    provider, model, visionOnCpu, openAi.copy(hasApiKey = false), claude.copy(hasApiKey = false), nutritionProvider, backgroundPhotoAnalysisEnabled,
)

/** Keeps the user's edits and takes every untouched field from the newly saved settings. */
private fun AiSettingsDraft.rebase(old: AiSettingsDraft, new: AiSettingsDraft): AiSettingsDraft {
    fun <T> pick(current: T, before: T, after: T) = if (current == before) after else current
    return copy(
        backgroundPhotoAnalysisEnabled = pick(backgroundPhotoAnalysisEnabled, old.backgroundPhotoAnalysisEnabled, new.backgroundPhotoAnalysisEnabled),
        provider = pick(provider, old.provider, new.provider),
        geminiModel = pick(geminiModel, old.geminiModel, new.geminiModel),
        visionOnCpu = pick(visionOnCpu, old.visionOnCpu, new.visionOnCpu),
        openAi = pick(openAi, old.openAi, new.openAi),
        claude = pick(claude, old.claude, new.claude),
        nutritionProvider = pick(nutritionProvider, old.nutritionProvider, new.nutritionProvider),
    )
}

private fun AiSettingsDraft.key(provider: AiProvider): String? = when (provider) {
    AiProvider.OpenAI -> openAiKey
    AiProvider.Claude -> claudeKey
    else -> geminiKey
}

private fun AiSettingsDraft.withKey(provider: AiProvider, key: String?) = when (provider) {
    AiProvider.OpenAI -> copy(openAiKey = key)
    AiProvider.Claude -> copy(claudeKey = key)
    else -> copy(geminiKey = key)
}

private fun AiSettings.hasKey(provider: AiProvider): Boolean = when (provider) {
    AiProvider.Gemini -> hasApiKey
    AiProvider.OpenAI -> openAi.hasApiKey
    AiProvider.Claude -> claude.hasApiKey
    AiProvider.Local, AiProvider.LocalE2B -> true
}

private val NutritionProvider.keyProvider: AiProvider
    get() = when (this) {
        NutritionProvider.OpenAI -> AiProvider.OpenAI
        NutritionProvider.Claude -> AiProvider.Claude
    }

private val keyProviders = listOf(AiProvider.Gemini, AiProvider.OpenAI, AiProvider.Claude)

@Composable
fun AiSettingsScreen(onBack: () -> Unit) {
    val controller: AiController = koinInject()
    val settings by controller.settings.collectAsStateWithLifecycle()
    val downloads by controller.downloads.collectAsStateWithLifecycle()
    val analysis by controller.analysis.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var report by remember { mutableStateOf<String?>(null) }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    report?.let { text -> AiDiagnosticDialog(text, { clipboard.setText(androidx.compose.ui.text.AnnotatedString(text)) }, { report = null }) }
    var saving by remember { mutableStateOf(false) }
    val savedMessage = stringResource(Res.string.ai_settings_saved)
    val failedMessage = stringResource(Res.string.ai_settings_save_failed)
    val keyRemovedMessage = stringResource(Res.string.ai_settings_key_removed)
    // Runs one controller action at a time and reports its outcome in the snackbar.
    val run: (suspend () -> String) -> Unit = { action ->
        saving = true
        scope.launch {
            val message = try { action() } finally { saving = false }
            // Test results and errors are the only feedback here, so they stay longer than a confirmation.
            val confirmation = message == savedMessage || message == keyRemovedMessage
            snackbar.showSnackbar(message, withDismissAction = true,
                duration = if (confirmation) SnackbarDuration.Short else SnackbarDuration.Long)
        }
    }
    AiSettingsContent(settings, downloads, analysis.running || saving, snackbar,
        onBack = onBack,
        onSave = { draft -> run {
            try {
                val saved = controller.settings.value
                if (draft.provider != saved.provider || draft.geminiModel != saved.model ||
                    draft.visionOnCpu != saved.visionOnCpu || draft.geminiKey != null)
                    controller.saveSettings(draft.provider, draft.geminiModel, draft.visionOnCpu, draft.geminiKey)
                if (draft.openAi != saved.openAi.copy(hasApiKey = false) || draft.openAiKey != null)
                    controller.saveOpenAi(draft.openAi, draft.openAiKey)
                if (draft.claude != saved.claude.copy(hasApiKey = false) || draft.claudeKey != null)
                    controller.saveClaude(draft.claude, draft.claudeKey)
                if (draft.nutritionProvider != saved.nutritionProvider) controller.saveNutritionProvider(draft.nutritionProvider)
                if (draft.backgroundPhotoAnalysisEnabled != saved.backgroundPhotoAnalysisEnabled)
                    controller.saveBackgroundPhotoAnalysis(draft.backgroundPhotoAnalysisEnabled)
                controller.reconcileBackgroundAnalysis()
                savedMessage
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { failedMessage }
        } },
        onRemoveKey = { provider -> run {
            try {
                // Removes only the key; unsaved edits stay in the draft.
                val saved = controller.settings.value
                when (provider) {
                    AiProvider.OpenAI -> controller.saveOpenAi(saved.openAi, null, true)
                    AiProvider.Claude -> controller.saveClaude(saved.claude, null, true)
                    else -> controller.saveSettings(saved.provider, saved.model, saved.visionOnCpu, null, true)
                }
                keyRemovedMessage
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { failedMessage }
        } },
        onTest = { photo -> run {
            val saved = controller.settings.value
            if (photo) when (saved.provider) {
                AiProvider.OpenAI -> controller.testOpenAi(false)
                AiProvider.Claude -> controller.testClaude(false)
                else -> controller.testConnection()
            } else when (saved.nutritionProvider) {
                NutritionProvider.OpenAI -> controller.testOpenAi(true)
                NutritionProvider.Claude -> controller.testClaude(true)
            }
        } },
        onDownload = controller::startDownload,
        onPause = controller::pauseDownload,
        onDelete = controller::deleteModel,
        onDiagnostics = { scope.launch { report = controller.diagnosticReport() } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AiSettingsContent(
    settings: AiSettings,
    downloads: Map<AiProvider, ModelDownloadState>,
    busy: Boolean,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    onBack: () -> Unit = {},
    onSave: (AiSettingsDraft) -> Unit = {},
    onRemoveKey: (AiProvider) -> Unit = {},
    onTest: (photo: Boolean) -> Unit = {},
    onDownload: (AiProvider) -> Unit = {},
    onPause: () -> Unit = {},
    onDelete: (AiProvider) -> Unit = {},
    onDiagnostics: () -> Unit = {},
) {
    var baseline by rememberSaveable(stateSaver = AiSettingsDraftSaver) { mutableStateOf(settings.toDraft()) }
    var draft by rememberSaveable(stateSaver = AiSettingsDraftSaver) { mutableStateOf(baseline) }
    LaunchedEffect(settings) {
        val saved = settings.toDraft()
        draft = draft.rebase(baseline, saved)
        baseline = saved
    }
    var editingKey by remember { mutableStateOf<AiProvider?>(null) }
    var confirmRemoveKey by remember { mutableStateOf<AiProvider?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val provider = draft.provider
    val localModel = provider.localModel
    val download = downloads[provider] ?: ModelDownloadState(total = localModel?.size ?: GemmaModel.E4B.size)
    val dirty = draft != baseline
    val baseChanged = provider != baseline.provider || draft.geminiModel != baseline.geminiModel ||
        draft.visionOnCpu != baseline.visionOnCpu || draft.geminiKey != null
    val valid = draft.geminiModel.isNotBlank() && listOf(
        draft.openAi.photo.model, draft.openAi.nutrition.model, draft.claude.photo.model, draft.claude.nutrition.model,
    ).none(String::isBlank)
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Gemma-Modell löschen?") },
        text = { Text("Für die nächste lokale Analyse muss das Modell erneut heruntergeladen werden.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete(provider) }) { Text("Löschen") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
    )
    confirmRemoveKey?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmRemoveKey = null },
            title = { Text(stringResource(Res.string.ai_settings_key_remove_title, target.label)) },
            text = { Text(stringResource(Res.string.ai_settings_key_remove_text, target.label)) },
            confirmButton = {
                TextButton(onClick = { confirmRemoveKey = null; onRemoveKey(target) }) {
                    Text(stringResource(Res.string.ai_settings_key_remove))
                }
            },
            dismissButton = { TextButton(onClick = { confirmRemoveKey = null }) { Text("Abbrechen") } },
        )
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text("KI") }, navigationIcon = { ArrowBackIconButton(onBack) }) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (dirty) AiSaveBar(
                canSave = valid && !busy && !(download.running && baseChanged),
                onDiscard = { draft = baseline; editingKey = null },
                onSave = {
                    onSave(draft)
                    draft = draft.copy(geminiKey = null, openAiKey = null, claudeKey = null)
                    editingKey = null
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            AiSettingsSection(stringResource(Res.string.ai_settings_photos), Icons.Outlined.Scale)
            Text(
                stringResource(Res.string.ai_settings_photos_intro),
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AiSettingsGroup {
                AiProvider.entries.forEach { value ->
                    val selected = provider == value
                    val enabled = !busy && !download.running
                    AiSettingsRow(
                        headline = value.localModel?.displayName ?: value.label,
                        supporting = providerStatus(value, downloads, settings),
                        leading = { AiRowIcon(value.icon, selected) },
                        trailing = { RadioButton(selected = selected, onClick = null, enabled = enabled) },
                        selected = selected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { draft = draft.copy(provider = value) },
                    )
                }
            }
            AiNote(provider.privacyNote, Icons.Outlined.Shield)
            if (!settings.hasKey(provider)) MissingKeyNote(provider)
            AiSettingsGroup {
                when (provider) {
                    AiProvider.Local, AiProvider.LocalE2B -> {
                        val model = requireNotNull(localModel)
                        AiSettingsBlock {
                            Text("${model.displayName} · etwa ${model.approximateSize} GB", style = MaterialTheme.typography.titleSmall)
                            Text(
                                when {
                                    download.ready -> "Modell bereit"
                                    download.verifying -> "Prüfsumme wird kontrolliert …"
                                    else -> "${download.bytes / 1_000_000} / ${download.total / 1_000_000} MB"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (download.running) LinearProgressIndicator(
                                progress = { (download.bytes.toDouble() / download.total).toFloat() },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (download.running) {
                                    OutlinedButton(onClick = onPause) { Text("Pausieren") }
                                } else {
                                    if (!download.ready) FilledTonalButton(onClick = { onDownload(provider) }, enabled = !busy) {
                                        Text(if (download.bytes > 0) "Download fortsetzen" else "Gemma herunterladen")
                                    }
                                    if (download.ready || download.bytes > 0) TextButton(onClick = { confirmDelete = true }, enabled = !busy) {
                                        Text("Modell und Download löschen")
                                    }
                                }
                            }
                            download.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                            Text(
                                "Die Analyse benötigt freien Arbeitsspeicher und läuft nur bei geöffneter App. " +
                                    "Der Download pausiert beim Verlassen der App. Modellquelle: LiteRT Community / Google, Apache 2.0.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        AiSettingsRow(
                            headline = stringResource(Res.string.ai_settings_vision_cpu),
                            supporting = stringResource(Res.string.ai_settings_vision_cpu_help),
                            trailing = { Switch(checked = draft.visionOnCpu, onCheckedChange = null, enabled = !busy) },
                            checked = draft.visionOnCpu,
                            enabled = !busy,
                            role = Role.Switch,
                            onClick = { draft = draft.copy(visionOnCpu = !draft.visionOnCpu) },
                        )
                    }
                    AiProvider.Gemini -> AiSettingsBlock {
                        OutlinedTextField(value = draft.geminiModel, onValueChange = { draft = draft.copy(geminiModel = it) },
                            label = { Text(stringResource(Res.string.ai_settings_gemini_model)) },
                            singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                    }
                    AiProvider.OpenAI -> {
                        CloudTaskFields(draft.openAi.photo.model, draft.openAi.photo.effort, openAiModelChoices(),
                            ::supportedEfforts, !busy) { model, effort ->
                            draft = draft.copy(openAi = draft.openAi.copy(photo = OpenAiTask(model, effort)))
                        }
                        ParallelismRow(draft.openAi.photoParallelism, !busy) {
                            draft = draft.copy(openAi = draft.openAi.copy(photoParallelism = it))
                        }
                    }
                    AiProvider.Claude -> {
                        CloudTaskFields(draft.claude.photo.model, draft.claude.photo.effort, claudeModelChoices(),
                            ::claudeSupportedEfforts, !busy) { model, effort ->
                            draft = draft.copy(claude = draft.claude.copy(photo = ClaudeTask(model, effort)))
                        }
                        ParallelismRow(draft.claude.photoParallelism, !busy) {
                            draft = draft.copy(claude = draft.claude.copy(photoParallelism = it))
                        }
                    }
                }
            }

            AiSettingsGroup {
                AiSettingsRow(
                    headline = "Fotos automatisch im Hintergrund erkennen",
                    supporting = if (localModel != null) "Nur für Cloud-Anbieter verfügbar."
                        else "Neue und bisher nicht analysierte Fotos werden automatisch übertragen – auch über Mobilfunk. Die API-Nutzung kann Kosten verursachen.",
                    trailing = { Switch(checked = draft.backgroundPhotoAnalysisEnabled, onCheckedChange = null, enabled = !busy && localModel == null) },
                    checked = draft.backgroundPhotoAnalysisEnabled,
                    enabled = !busy && localModel == null,
                    role = Role.Switch,
                    onClick = { draft = draft.copy(backgroundPhotoAnalysisEnabled = !draft.backgroundPhotoAnalysisEnabled) },
                )
            }

            AiSettingsSection(stringResource(Res.string.ai_settings_nutrition), Icons.Outlined.TravelExplore)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                NutritionProvider.entries.forEachIndexed { index, value ->
                    SegmentedButton(
                        selected = draft.nutritionProvider == value,
                        onClick = { draft = draft.copy(nutritionProvider = value) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = NutritionProvider.entries.size),
                        enabled = !busy,
                    ) { Text(value.label) }
                }
            }
            val nutritionKeyProvider = draft.nutritionProvider.keyProvider
            AiNote(
                stringResource(Res.string.ai_settings_nutrition_privacy,
                    if (nutritionKeyProvider == AiProvider.Claude) "Anthropic" else "OpenAI"),
                Icons.Outlined.Shield,
            )
            if (!settings.hasKey(nutritionKeyProvider)) MissingKeyNote(nutritionKeyProvider)
            AiSettingsGroup {
                when (draft.nutritionProvider) {
                    NutritionProvider.OpenAI -> CloudTaskFields(draft.openAi.nutrition.model, draft.openAi.nutrition.effort,
                        openAiModelChoices(), ::supportedEfforts, !busy) { model, effort ->
                        draft = draft.copy(openAi = draft.openAi.copy(nutrition = OpenAiTask(model, effort)))
                    }
                    NutritionProvider.Claude -> CloudTaskFields(draft.claude.nutrition.model, draft.claude.nutrition.effort,
                        claudeModelChoices(), ::claudeSupportedEfforts, !busy) { model, effort ->
                        draft = draft.copy(claude = draft.claude.copy(nutrition = ClaudeTask(model, effort)))
                    }
                }
            }

            AiSettingsSection(stringResource(Res.string.ai_settings_keys), Icons.Outlined.Key)
            AiSettingsGroup {
                keyProviders.forEach { target ->
                    val saved = settings.hasKey(target)
                    val forPhotos = provider == target
                    val forNutrition = nutritionKeyProvider == target
                    AiSettingsRow(
                        headline = target.label,
                        supporting = stringResource(when {
                            forPhotos && forNutrition -> Res.string.ai_settings_used_both
                            forPhotos -> Res.string.ai_settings_used_photos
                            forNutrition -> Res.string.ai_settings_used_nutrition
                            else -> Res.string.ai_settings_used_none
                        }),
                        trailing = { KeyActions(target, saved, !busy, { editingKey = target }, { confirmRemoveKey = target }) },
                        enabled = !busy,
                        onClick = { editingKey = if (editingKey == target) null else target },
                    )
                    if (editingKey == target) AiSettingsBlock {
                        OutlinedTextField(
                            value = draft.key(target).orEmpty(),
                            onValueChange = { draft = draft.withKey(target, it.takeIf(String::isNotBlank)) },
                            label = { Text(stringResource(Res.string.ai_settings_key_new, target.label)) },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            AiSettingsSection(stringResource(Res.string.ai_settings_check), Icons.Outlined.Troubleshoot)
            AiSettingsGroup {
                val saveFirst = if (dirty) stringResource(Res.string.ai_settings_save_first) else null
                if (localModel == null) AiSettingsRow(
                    headline = stringResource(Res.string.ai_settings_test_photo),
                    supporting = saveFirst ?: when (provider) {
                        AiProvider.OpenAI -> "${settings.openAi.photo.model} · ${aiEffortLabel(settings.openAi.photo.effort)}"
                        AiProvider.Claude -> "${settings.claude.photo.model} · ${aiEffortLabel(settings.claude.photo.effort)}"
                        else -> settings.model
                    },
                    trailing = { Icon(Icons.Outlined.PlayArrow, contentDescription = null) },
                    enabled = !busy && !dirty && settings.hasKey(provider),
                    onClick = { onTest(true) },
                )
                AiSettingsRow(
                    headline = stringResource(Res.string.ai_settings_test_nutrition),
                    supporting = saveFirst ?: stringResource(Res.string.ai_settings_test_nutrition_detail, nutritionKeyProvider.label),
                    trailing = { Icon(Icons.Outlined.PlayArrow, contentDescription = null) },
                    enabled = !busy && !dirty && settings.hasKey(nutritionKeyProvider),
                    onClick = { onTest(false) },
                )
                AiSettingsRow(
                    headline = stringResource(Res.string.ai_settings_diagnostics),
                    supporting = stringResource(Res.string.ai_settings_diagnostics_detail),
                    trailing = { Icon(Icons.Outlined.ChevronRight, contentDescription = null) },
                    onClick = onDiagnostics,
                )
            }
        }
    }
}

@Composable
private fun providerStatus(provider: AiProvider, downloads: Map<AiProvider, ModelDownloadState>, settings: AiSettings): String {
    val model = provider.localModel ?: return stringResource(
        if (settings.hasKey(provider)) Res.string.ai_settings_status_cloud_key else Res.string.ai_settings_status_cloud_no_key,
    )
    val download = downloads[provider]
    return when {
        download?.ready == true -> stringResource(Res.string.ai_settings_status_local_ready)
        download?.running == true -> stringResource(Res.string.ai_settings_status_local_loading)
        else -> stringResource(Res.string.ai_settings_status_local_size, model.approximateSize)
    }
}

@Composable
private fun openAiModelChoices(): List<Pair<String, String>> = openAiModels.zip(listOf(
    stringResource(Res.string.openai_luna), stringResource(Res.string.openai_terra), stringResource(Res.string.openai_sol), stringResource(Res.string.openai_astra),
))

@Composable
private fun claudeModelChoices(): List<Pair<String, String>> = claudeModels.zip(listOf(
    stringResource(Res.string.claude_luna), stringResource(Res.string.claude_sol), stringResource(Res.string.claude_astra),
))

@Composable
private fun ParallelismRow(value: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    AiDropdownRow(
        label = stringResource(Res.string.ai_settings_parallelism),
        value = value.toString(),
        supporting = stringResource(Res.string.ai_settings_parallelism_help),
        enabled = enabled,
    ) { close ->
        (1..5).forEach { option ->
            DropdownMenuItem(text = { Text(option.toString()) }, onClick = { close(); onChange(option) })
        }
    }
}

@Composable
private fun AiNote(text: String, icon: ImageVector, error: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MissingKeyNote(provider: AiProvider) {
    AiNote(stringResource(Res.string.ai_settings_key_missing, provider.label), Icons.Outlined.Key, error = true)
}

@Composable
private fun KeyActions(provider: AiProvider, saved: Boolean, enabled: Boolean, onEdit: () -> Unit, onRemove: () -> Unit) {
    if (!saved) {
        TextButton(onClick = onEdit, enabled = enabled) { Text(stringResource(Res.string.ai_settings_key_add)) }
        return
    }
    var menu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
            Text(stringResource(Res.string.ai_settings_key_saved), Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
        Box {
            IconButton(onClick = { menu = true }, enabled = enabled) {
                Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(Res.string.ai_settings_key_more, provider.label))
            }
            DropdownMenu(menu && enabled, { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(Res.string.ai_settings_key_replace)) }, onClick = { menu = false; onEdit() })
                DropdownMenuItem(text = { Text(stringResource(Res.string.ai_settings_key_remove)) }, onClick = { menu = false; onRemove() })
            }
        }
    }
}

/** Shown only with unsaved edits, so the screen never has more than one filled button. */
@Composable
private fun AiSaveBar(canSave: Boolean, onDiscard: () -> Unit, onSave: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
                .padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(Res.string.ai_settings_unsaved),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onDiscard) { Text(stringResource(Res.string.ai_settings_discard)) }
            Button(onClick = onSave, enabled = canSave) { Text(stringResource(Res.string.ai_settings_save)) }
        }
    }
}


/**
 * Slim status line above the photo grid; the primary action lives in [AiAnalysisActionButton].
 * The provider chip opens [AiProviderSheet], which owns the privacy note and the last run's details.
 */
@Composable
internal fun AiAnalysisControls(
    provider: AiProvider,
    progress: AnalysisProgress,
    canStart: Boolean,
    photos: PhotoAnalysisSummary,
    onReanalyze: () -> Unit,
    onSettings: () -> Unit,
    onResume: () -> Unit = {},
    modelLabel: String? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    val paused = progress.paused && !progress.running && !progress.queued
    val canReanalyze = photos.total > 0 && canStart && !progress.running && !progress.queued
    Column(Modifier.fillMaxWidth()) {
        if (progress.running) {
            val fraction = if (progress.total > 0) progress.completed.toFloat() / progress.total else 0f
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(3.dp))
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Narrow screens and large fonts move the status below the chip so neither gets squeezed.
            val compact = maxWidth < 360.dp || LocalDensity.current.fontScale > 1.3f
            val status: @Composable () -> Unit = {
                when {
                    progress.queued && !progress.running -> Text("Erkennung eingeplant", style = MaterialTheme.typography.bodyMedium)
                    progress.running -> Text(
                        if (provider == AiProvider.OpenAI || provider == AiProvider.Claude) "${progress.completed} von ${progress.total} Fotos verarbeitet"
                        else "Analysiere ${(progress.completed + 1).coerceAtMost(progress.total)} von ${progress.total}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    !canStart -> Text(
                        "Nicht eingerichtet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    paused -> Text(
                        "Angehalten",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error,
                    )
                    else -> photos.describe()?.let { PhotoSummaryText(it, photos) }
                }
            }
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AssistChip(
                        onClick = { sheetOpen = true },
                        label = { Text(provider.shortLabel, maxLines = 1) },
                        leadingIcon = {
                            Icon(
                                provider.icon,
                                contentDescription = null,
                                modifier = Modifier.size(AssistChipDefaults.IconSize),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        trailingIcon = {
                            Icon(
                                Icons.Outlined.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(AssistChipDefaults.IconSize),
                            )
                        },
                    )
                    Box(Modifier.weight(1f).padding(vertical = 6.dp)) { if (!compact) status() }
                    if (progress.running && !compact) {
                        AnalysisStopwatch(progress.elapsedMillis)
                    }
                    when {
                        !progress.running && !canStart -> TextButton(onClick = onSettings) { Text("Einrichten") }
                        paused && photos.suggested < photos.total -> TextButton(onClick = onResume) { Text("Fortsetzen") }
                        else -> Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = "Weitere KI-Optionen")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Alle offenen Fotos erneut analysieren") },
                                    leadingIcon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
                                    enabled = canReanalyze,
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
                }
                if (compact) {
                    Column(
                        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        status()
                        if (progress.running) AnalysisStopwatch(progress.elapsedMillis)
                    }
                }
            }
        }
        progress.message?.let {
            Text(
                it,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
    if (sheetOpen) {
        AiProviderSheet(
            provider = provider,
            modelLabel = modelLabel,
            progress = progress,
            photos = photos,
            canReanalyze = canReanalyze,
            onReanalyze = onReanalyze,
            onSettings = onSettings,
            onDismiss = { sheetOpen = false },
        )
    }
}

@Composable
private fun PhotoSummaryText(text: String, photos: PhotoAnalysisSummary) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        when {
            photos.withoutSuggestion > 0 -> Icon(
                Icons.Outlined.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            photos.suggested == photos.total -> Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiProviderSheet(
    provider: AiProvider,
    modelLabel: String?,
    progress: AnalysisProgress,
    photos: PhotoAnalysisSummary,
    canReanalyze: Boolean,
    onReanalyze: () -> Unit,
    onSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Icon(provider.icon, contentDescription = null, modifier = Modifier.padding(10.dp).size(24.dp))
                }
                Column {
                    Text(provider.label, style = MaterialTheme.typography.titleLarge)
                    modelLabel?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(
                        Icons.Outlined.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(provider.privacyNote, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Column {
                if (progress.total > 0 && !progress.running) {
                    val count = if (progress.total == 1) "1 Foto" else "${progress.total} Fotos"
                    AiSheetRow("Letzter Durchlauf", "$count · ${formatAnalysisDuration(progress.elapsedMillis)}")
                }
                photos.describe()?.let { AiSheetRow("Offene Fotos", it) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { onDismiss(); onSettings() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Einstellungen")
                }
                FilledTonalButton(
                    onClick = { onDismiss(); onReanalyze() },
                    enabled = canReanalyze,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Alle erneut")
                }
            }
        }
    }
}

@Composable
private fun AiSheetRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
        )
    }
}

private val AiProvider.icon: ImageVector
    get() = if (localModel == null) Icons.Outlined.Cloud else Icons.Outlined.PhoneAndroid

private val AiProvider.privacyNote: String
    get() = when (this) {
        AiProvider.Claude -> "Fotos werden zur Analyse an Anthropic gesendet. Es gilt das Kontingent deines API-Keys; die API-Nutzung kann Kosten verursachen."
        AiProvider.OpenAI -> "Fotos werden zur Analyse an OpenAI gesendet. Es gilt das Kontingent deines API-Keys; die API-Nutzung kann Kosten verursachen."
        AiProvider.Gemini -> "Fotos werden zur Analyse an Google gesendet. Es gilt das Kontingent deines API-Keys; die API-Nutzung kann Kosten verursachen."
        AiProvider.Local, AiProvider.LocalE2B -> "Die Analyse läuft auf diesem Gerät; Fotos verlassen es nicht."
    }

internal fun formatAnalysisDuration(elapsedMillis: Long): String {
    val seconds = elapsedMillis.coerceAtLeast(0) / 1_000
    return when {
        seconds == 0L -> "unter 1 s"
        seconds < 60 -> "$seconds s"
        else -> formatAnalysisElapsedTime(elapsedMillis)
    }
}

@Composable
private fun AnalysisStopwatch(elapsedMillis: Long) {
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            Icons.Outlined.Timer,
            contentDescription = "Laufzeit",
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            formatAnalysisElapsedTime(elapsedMillis),
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
        )
    }
}

internal fun formatAnalysisElapsedTime(elapsedMillis: Long): String {
    val seconds = elapsedMillis.coerceAtLeast(0) / 1_000
    val minutesAndSeconds = "${(seconds / 60 % 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}"
    return if (seconds >= 3_600) "${(seconds / 3_600).toString().padStart(2, '0')}:$minutesAndSeconds"
    else minutesAndSeconds
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
        progress.running || progress.queued ->
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

package com.maksimowiczm.foodyou.app.ui.food.quickcapture

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Fastfood
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.app.ui.food.diary.quickadd.QuickAddCsvError
import com.maksimowiczm.foodyou.app.ui.food.diary.quickadd.QuickAddCsvParseResult
import com.maksimowiczm.foodyou.app.ui.food.diary.quickadd.QuickAddCsvParser
import com.maksimowiczm.foodyou.app.ui.food.diary.quickadd.stringResource
import com.maksimowiczm.foodyou.app.ui.food.pending.PendingProductPhoto
import com.maksimowiczm.foodyou.app.ui.food.pending.PendingProductPhotoCapture
import com.maksimowiczm.foodyou.common.compose.extension.LaunchedCollectWithLifecycle
import com.maksimowiczm.foodyou.common.compose.utility.LocalClipboardManager
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.food.domain.entity.QuickCaptureFoodName
import com.maksimowiczm.foodyou.food.domain.entity.QuickCaptureLogEntry
import com.maksimowiczm.foodyou.food.domain.entity.QuickCaptureLogGroup
import com.maksimowiczm.foodyou.food.domain.entity.QuickCaptureWeightMode
import com.maksimowiczm.foodyou.food.domain.entity.formatQuickCaptureWeight
import com.maksimowiczm.foodyou.food.domain.entity.quickCaptureGroups
import com.maksimowiczm.foodyou.food.domain.entity.quickCapturePrompt
import com.maksimowiczm.foodyou.food.domain.usecase.CaptureQuickCapturePhotoUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.CompleteQuickCaptureAfterUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.DeleteQuickCaptureEntriesUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.ObserveQuickCaptureUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.SaveQuickCaptureEntryResult
import com.maksimowiczm.foodyou.food.domain.usecase.SaveQuickCaptureEntryUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.UpdateQuickCaptureLibraryUseCase
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
import foodyou.app.generated.resources.Res
import foodyou.app.generated.resources.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

data class QuickCaptureTransferRequest(
    val entryIds: List<Long>,
    val foodName: String,
    val weightInGrams: Double,
)

internal enum class QuickCaptureTab { Log, Photos, Library }

internal enum class QuickCaptureFormError { Name, Weight }

@Composable
fun QuickCaptureScreen(
    onBack: () -> Unit,
    onPhoto: (Long) -> Unit,
    onTransfer: (QuickCaptureTransferRequest) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: QuickCaptureViewModel = koinViewModel()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val names by viewModel.names.collectAsStateWithLifecycle()
    val aggregate by viewModel.aggregateSameFoods.collectAsStateWithLifecycle()
    val formError by viewModel.formError.collectAsStateWithLifecycle()
    val cameraOpen by viewModel.cameraOpen.collectAsStateWithLifecycle()
    val copiedEntryIds by viewModel.copiedEntryIds.collectAsStateWithLifecycle()
    val csvImportState by viewModel.csvImportState.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableStateOf(QuickCaptureTab.Log) }
    var showEntryForm by rememberSaveable { mutableStateOf(false) }
    var showCsvImport by rememberSaveable { mutableStateOf(false) }
    var csvText by rememberSaveable { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val copiedMessage = stringResource(Res.string.neutral_quick_capture_prompt_copied)
    val importedMessage = stringResource(Res.string.neutral_quick_capture_csv_imported)

    LaunchedCollectWithLifecycle(viewModel.events) { event ->
        when (event) {
            QuickCaptureUiEvent.EntrySaved -> showEntryForm = false
            QuickCaptureUiEvent.CsvImported -> {
                showCsvImport = false
                csvText = ""
                coroutineScope.launch { snackbar.showSnackbar(importedMessage) }
            }
        }
    }

    if (showEntryForm) {
        QuickCaptureEntrySheet(
            names = names,
            error = formError,
            onDismiss = {
                viewModel.resetFormError()
                showEntryForm = false
            },
            onSave = viewModel::save,
        )
    }
    if (showCsvImport) {
        QuickCaptureCsvImportDialog(
            csv = csvText,
            state = csvImportState,
            onCsvChange = {
                csvText = it
                viewModel.resetCsvImportError()
            },
            onDismiss = {
                viewModel.resetCsvImportError()
                showCsvImport = false
                csvText = ""
            },
            onImport = { viewModel.importCsv(csvText) },
        )
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.headline_quick_capture)) },
                navigationIcon = {
                    ArrowBackIconButton(
                        if (cameraOpen) viewModel::closeCamera else onBack
                    )
                },
            )
        },
        floatingActionButton = {
            QuickCaptureFloatingActionButton(
                selectedTab = selectedTab,
                cameraOpen = cameraOpen,
                onAddEntry = {
                    viewModel.resetFormError()
                    showEntryForm = true
                },
                onOpenCamera = viewModel::openCamera,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!cameraOpen) {
                PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                    QuickCaptureTab.entries.forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            text = {
                                Text(
                                    stringResource(
                                        when (tab) {
                                            QuickCaptureTab.Log -> Res.string.headline_quick_capture_log
                                            QuickCaptureTab.Photos -> Res.string.headline_quick_capture_photos
                                            QuickCaptureTab.Library -> Res.string.headline_quick_capture_library
                                        }
                                    )
                                )
                            },
                        )
                    }
                }
            }

            when {
                cameraOpen ->
                    PendingProductPhotoCapture(
                        photoCount = entries.count { it.isPendingPhoto },
                        photoDirectory = QUICK_CAPTURE_PHOTO_DIRECTORY,
                        onPhotoTaken = viewModel::capturePhoto,
                        onClose = viewModel::closeCamera,
                        modifier = Modifier.fillMaxSize(),
                    )
                selectedTab == QuickCaptureTab.Log ->
                    QuickCaptureLog(
                        entries = entries,
                        aggregate = aggregate,
                        onAggregateChange = viewModel::setAggregateSameFoods,
                        onCompleteAfter = viewModel::completeAfter,
                        onDelete = { viewModel.delete(listOf(it)) },
                        onClearCompleted = { viewModel.delete(entries.filter { it.isCompleted }) },
                        onCopyPrompt = {
                            val groups = entries.quickCaptureGroups(aggregate)
                            if (groups.isNotEmpty()) {
                                clipboard.copy("FoodYou quick capture prompt", groups.quickCapturePrompt())
                                viewModel.rememberCopiedBatch(
                                    groups.flatMap { group -> group.entries.map { it.id } }
                                )
                                coroutineScope.launch { snackbar.showSnackbar(copiedMessage) }
                            }
                        },
                        hasCopiedBatch = copiedEntryIds.isNotEmpty(),
                        onQuickAdd = {
                            viewModel.resetCsvImportError()
                            showCsvImport = true
                        },
                        onTransfer = { group ->
                            onTransfer(
                                QuickCaptureTransferRequest(
                                    entryIds = group.entries.map { it.id },
                                    foodName = group.foodName,
                                    weightInGrams = group.weightInGrams,
                                )
                            )
                        },
                    )
                selectedTab == QuickCaptureTab.Photos ->
                    QuickCapturePhotos(
                        entries = entries.filter { it.isPendingPhoto },
                        onPhoto = onPhoto,
                        onDelete = { viewModel.delete(listOf(it)) },
                    )
                else ->
                    QuickCaptureLibrary(
                        names = names,
                        onRename = viewModel::renameName,
                        onDelete = viewModel::deleteName,
                    )
            }
        }
    }
}

@Composable
internal fun QuickCaptureFloatingActionButton(
    selectedTab: QuickCaptureTab,
    cameraOpen: Boolean,
    onAddEntry: () -> Unit,
    onOpenCamera: () -> Unit,
) {
    if (cameraOpen) return

    when (selectedTab) {
        QuickCaptureTab.Log ->
            FloatingActionButton(onClick = onAddEntry) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription =
                        stringResource(Res.string.action_quick_capture_open_entry_form),
                )
            }
        QuickCaptureTab.Photos ->
            FloatingActionButton(onClick = onOpenCamera) {
                Icon(
                    Icons.Outlined.PhotoCamera,
                    contentDescription =
                        stringResource(Res.string.action_quick_capture_take_photo),
                )
            }
        QuickCaptureTab.Library -> Unit
    }
}

@Composable
internal fun QuickCaptureLog(
    entries: List<QuickCaptureLogEntry>,
    aggregate: Boolean,
    onAggregateChange: (Boolean) -> Unit,
    onCompleteAfter: (Long, Double) -> Unit,
    onDelete: (QuickCaptureLogEntry) -> Unit,
    onClearCompleted: () -> Unit,
    onCopyPrompt: () -> Unit,
    hasCopiedBatch: Boolean,
    onQuickAdd: () -> Unit,
    onTransfer: (QuickCaptureLogGroup) -> Unit,
) {
    var completed by rememberSaveable { mutableStateOf(false) }
    var afterEntry by remember { mutableStateOf<QuickCaptureLogEntry?>(null) }
    var deleteEntry by remember { mutableStateOf<QuickCaptureLogEntry?>(null) }
    var confirmClearCompleted by remember { mutableStateOf(false) }
    val expandedGroups = remember { mutableStateListOf<String>() }
    val visibleEntries = entries.filter { it.isCompleted == completed && !it.isPendingPhoto }
    val groups = if (completed) emptyList() else entries.quickCaptureGroups(aggregate)

    afterEntry?.let { entry ->
        AfterWeightDialog(
            entry = entry,
            onDismiss = { afterEntry = null },
            onSave = {
                onCompleteAfter(entry.id, it)
                afterEntry = null
            },
        )
    }
    deleteEntry?.let { entry ->
        QuickCaptureDeleteConfirmationDialog(
            target = QuickCaptureDeleteTarget.Entry,
            onDismiss = { deleteEntry = null },
            onConfirm = {
                deleteEntry = null
                onDelete(entry)
            },
        )
    }
    if (confirmClearCompleted) {
        QuickCaptureDeleteConfirmationDialog(
            target = QuickCaptureDeleteTarget.CompletedEntries(visibleEntries.size),
            onDismiss = { confirmClearCompleted = false },
            onConfirm = {
                confirmClearCompleted = false
                onClearCompleted()
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = !completed,
                    onClick = { completed = false },
                    label = { Text(stringResource(Res.string.headline_quick_capture_open)) },
                )
                FilterChip(
                    selected = completed,
                    onClick = { completed = true },
                    label = { Text(stringResource(Res.string.headline_quick_capture_completed)) },
                )
            }
        }
        if (!completed) {
            item {
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.headline_quick_capture_aggregate)) },
                    leadingContent = {
                        Checkbox(checked = aggregate, onCheckedChange = onAggregateChange)
                    },
                    trailingContent = {
                        Row {
                            IconButton(onClick = onCopyPrompt, enabled = groups.isNotEmpty()) {
                                Icon(
                                    Icons.Outlined.ContentCopy,
                                    contentDescription = stringResource(Res.string.action_quick_capture_copy_prompt),
                                )
                            }
                            IconButton(onClick = onQuickAdd, enabled = hasCopiedBatch) {
                                Icon(
                                    Icons.Outlined.Bolt,
                                    contentDescription =
                                        stringResource(Res.string.action_quick_capture_import_csv),
                                )
                            }
                        }
                    },
                )
            }
        } else if (visibleEntries.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { confirmClearCompleted = true }) {
                        Icon(Icons.Outlined.Delete, contentDescription = null)
                        Text(stringResource(Res.string.action_quick_capture_clear_completed))
                    }
                }
            }
        }

        if (visibleEntries.isEmpty()) {
            item {
                EmptyQuickCaptureState(
                    text = stringResource(Res.string.neutral_quick_capture_empty_log),
                    modifier = Modifier.fillMaxWidth().padding(40.dp),
                )
            }
        } else if (completed) {
            items(visibleEntries, key = { it.id }) { entry ->
                QuickCaptureCompletedRow(entry = entry, onDelete = { deleteEntry = entry })
            }
        } else {
            val pending = visibleEntries.filter { !it.isReady }
            items(pending, key = { "pending-${it.id}" }) { entry ->
                QuickCapturePendingRow(
                    entry = entry,
                    onCompleteAfter = { afterEntry = entry },
                    onDelete = { deleteEntry = entry },
                )
            }
            items(groups, key = { it.key }) { group ->
                val expanded = group.key in expandedGroups
                QuickCaptureGroupRow(
                    group = group,
                    expanded = expanded,
                    onToggleExpanded = {
                        if (expanded) expandedGroups.remove(group.key) else expandedGroups.add(group.key)
                    },
                    onTransfer = { onTransfer(group) },
                    onDelete = { deleteEntry = it },
                )
            }
        }
    }
}

@Composable
internal fun QuickCaptureCsvImportDialog(
    csv: String,
    state: QuickCaptureCsvImportState,
    onCsvChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onImport: () -> Unit,
    autoFocus: Boolean = true,
) {
    val focusRequester = remember { FocusRequester() }
    val isSubmitting = state == QuickCaptureCsvImportState.Submitting

    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            delay(100)
            val _ = runCatching { focusRequester.requestFocus() }
        }
    }

    BasicAlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
    ) {
        QuickCaptureCsvImportDialogCard(
            csv = csv,
            state = state,
            onCsvChange = onCsvChange,
            onDismiss = onDismiss,
            onImport = onImport,
            focusRequester = focusRequester,
            autoFocus = autoFocus,
        )
    }
}

@Composable
internal fun QuickCaptureCsvImportDialogCard(
    csv: String,
    state: QuickCaptureCsvImportState,
    onCsvChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onImport: () -> Unit,
    focusRequester: FocusRequester = remember { FocusRequester() },
    autoFocus: Boolean = true,
) {
    val isSubmitting = state == QuickCaptureCsvImportState.Submitting
    val error =
        when (state) {
            is QuickCaptureCsvImportState.InvalidCsv -> state.error.stringResource()
            QuickCaptureCsvImportState.NoMeal ->
                stringResource(Res.string.error_quick_capture_csv_no_meal)
            QuickCaptureCsvImportState.SavingFailed ->
                stringResource(Res.string.error_quick_capture_csv_save)
            QuickCaptureCsvImportState.Idle,
            QuickCaptureCsvImportState.Submitting -> null
        }

    Surface(
        modifier = Modifier.fillMaxWidth().widthIn(min = 280.dp, max = 560.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
    ) {
        Column(Modifier.padding(24.dp)) {
            Text(
                stringResource(Res.string.headline_quick_capture_csv_import),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.description_quick_capture_csv_import))
                OutlinedTextField(
                    value = csv,
                    onValueChange = onCsvChange,
                    modifier =
                        Modifier.fillMaxWidth()
                            .focusProperties { canFocus = autoFocus }
                            .focusRequester(focusRequester),
                    label = { Text(stringResource(Res.string.headline_quick_add_csv)) },
                    supportingText = error?.let { { Text(it) } },
                    isError = error != null,
                    minLines = 3,
                    maxLines = 6,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (!isSubmitting) onImport() }),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                    Text(stringResource(Res.string.action_cancel))
                }
                TextButton(onClick = onImport, enabled = !isSubmitting) {
                    Text(stringResource(Res.string.action_quick_capture_import_csv_confirm))
                }
            }
        }
    }
}

@Composable
internal fun QuickCaptureEntrySheet(
    names: List<QuickCaptureFoodName>,
    error: QuickCaptureFormError?,
    onDismiss: () -> Unit,
    onSave: (String, QuickCaptureWeightMode, Double?, Double?, Double?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var requestInitialFocus by remember { mutableStateOf(false) }

    LaunchedEffect(sheetState) {
        snapshotFlow { sheetState.currentValue }.first { it == SheetValue.Expanded }
        requestInitialFocus = true
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier.fillMaxHeight(),
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(Res.string.headline_quick_capture_new_entry),
                style = MaterialTheme.typography.titleLarge,
            )
            QuickCaptureEntryForm(
                names = names,
                error = error,
                onSave = onSave,
                requestInitialFocus = requestInitialFocus,
            )
        }
    }
}

@Composable
internal fun QuickCaptureEntryForm(
    names: List<QuickCaptureFoodName>,
    error: QuickCaptureFormError?,
    onSave: (String, QuickCaptureWeightMode, Double?, Double?, Double?) -> Unit,
    modifier: Modifier = Modifier,
    requestInitialFocus: Boolean = false,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(QuickCaptureWeightMode.Direct) }
    var direct by rememberSaveable { mutableStateOf("") }
    var before by rememberSaveable { mutableStateOf("") }
    var after by rememberSaveable { mutableStateOf("") }
    val nameFocus = remember { FocusRequester() }
    val weightFocus = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    fun submit() {
        onSave(
            name,
            mode,
            direct.toLocalizedDouble(),
            before.toLocalizedDouble(),
            after.toLocalizedDouble(),
        )
    }

    LaunchedEffect(requestInitialFocus) {
        if (requestInitialFocus) {
            nameFocus.requestFocus()
            keyboardController?.show()
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        QuickCaptureNameField(
            value = name,
            onValueChange = { name = it },
            names = names,
            onNameConfirmed = { selected ->
                name = selected
                weightFocus.requestFocus()
            },
            isError = error == QuickCaptureFormError.Name,
            textFieldModifier = Modifier.focusRequester(nameFocus),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = mode == QuickCaptureWeightMode.Direct,
                onClick = { mode = QuickCaptureWeightMode.Direct },
                label = { Text(stringResource(Res.string.headline_quick_capture_direct)) },
            )
            FilterChip(
                selected = mode == QuickCaptureWeightMode.BeforeAfter,
                onClick = { mode = QuickCaptureWeightMode.BeforeAfter },
                label = { Text(stringResource(Res.string.headline_quick_capture_before_after)) },
            )
        }
        if (mode == QuickCaptureWeightMode.Direct) {
            WeightField(
                value = direct,
                onValueChange = { direct = it },
                label = stringResource(Res.string.headline_quick_capture_direct),
                modifier = Modifier.focusRequester(weightFocus),
                onDone = ::submit,
                isError = error == QuickCaptureFormError.Weight,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WeightField(
                    value = before,
                    onValueChange = { before = it },
                    label = stringResource(Res.string.headline_quick_capture_before),
                    modifier = Modifier.weight(1f).focusRequester(weightFocus),
                )
                WeightField(
                    value = after,
                    onValueChange = { after = it },
                    label = stringResource(Res.string.headline_quick_capture_after),
                    modifier = Modifier.weight(1f),
                    onDone = ::submit,
                    isError = error == QuickCaptureFormError.Weight,
                )
            }
        }
        if (error != null) {
            Text(
                stringResource(
                    if (error == QuickCaptureFormError.Name) Res.string.error_quick_capture_name
                    else Res.string.error_quick_capture_weight
                ),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Button(onClick = ::submit, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Text(stringResource(Res.string.action_quick_capture_add))
        }
    }
}

@Composable
internal fun QuickCaptureNameField(
    value: String,
    onValueChange: (String) -> Unit,
    names: List<QuickCaptureFoodName>,
    onNameConfirmed: (String) -> Unit,
    isError: Boolean = false,
    modifier: Modifier = Modifier,
    textFieldModifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val suggestions =
        remember(value, names) {
            if (value.isBlank()) emptyList()
            else names.filter { it.name.contains(value, ignoreCase = true) }.take(8)
        }
    val menuExpanded = expanded && focused && suggestions.isNotEmpty()
    ExposedDropdownMenuBox(
        expanded = menuExpanded,
        onExpandedChange = { expanded = it && value.isNotBlank() },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = it.isNotBlank()
            },
            modifier =
                textFieldModifier
                    .onFocusChanged {
                        focused = it.isFocused
                        if (!it.isFocused) expanded = false
                    }
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                    .fillMaxWidth(),
            label = { Text(stringResource(Res.string.product_name)) },
            singleLine = true,
            isError = isError,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions =
                KeyboardActions(
                    onNext = {
                        expanded = false
                        onNameConfirmed(value)
                    }
                ),
        )
        ExposedDropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { expanded = false },
        ) {
            suggestions.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion.name) },
                    onClick = {
                        expanded = false
                        onNameConfirmed(suggestion.name)
                    },
                )
            }
        }
    }
}

@Composable
private fun WeightField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    onDone: (() -> Unit)? = null,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        suffix = { Text("g") },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
    )
}

@Composable
private fun QuickCapturePendingRow(
    entry: QuickCaptureLogEntry,
    onCompleteAfter: () -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(entry.foodName.orEmpty()) },
        supportingContent = {
            Column {
                Text(stringResource(Res.string.headline_quick_capture_waiting_after))
                Text(stringResource(Res.string.description_quick_capture_pending_excluded))
            }
        },
        leadingContent = {
            entry.photoPath?.let {
                PendingProductPhoto(
                    photoPath = it,
                    photoDirectory = QUICK_CAPTURE_PHOTO_DIRECTORY,
                    modifier = Modifier.size(56.dp),
                )
            }
        },
        trailingContent = {
            Row {
                IconButton(onClick = onCompleteAfter) {
                    Icon(Icons.Outlined.Edit, contentDescription = stringResource(Res.string.action_quick_capture_save_after))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = stringResource(Res.string.action_delete))
                }
            }
        },
    )
}

@Composable
private fun QuickCaptureGroupRow(
    group: QuickCaptureLogGroup,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onTransfer: () -> Unit,
    onDelete: (QuickCaptureLogEntry) -> Unit,
) {
    Column {
        ListItem(
            modifier = Modifier.clickable(enabled = group.entries.size > 1, onClick = onToggleExpanded),
            headlineContent = { Text(group.foodName) },
            supportingContent = {
                if (group.entries.size > 1) {
                    Text(stringResource(Res.string.headline_quick_capture_items, group.entries.size))
                }
            },
            leadingContent = {
                group.entries.firstNotNullOfOrNull { it.photoPath }?.let {
                    PendingProductPhoto(
                        photoPath = it,
                        photoDirectory = QUICK_CAPTURE_PHOTO_DIRECTORY,
                        modifier = Modifier.size(56.dp),
                    )
                }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${group.weightInGrams.formatQuickCaptureWeight()} g")
                    IconButton(onClick = onTransfer) {
                        Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = stringResource(Res.string.action_quick_capture_to_diary))
                    }
                    if (group.entries.size == 1) {
                        IconButton(onClick = { onDelete(group.entries.single()) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(Res.string.action_delete))
                        }
                    }
                }
            },
        )
        if (expanded) {
            group.entries.forEach { entry ->
                ListItem(
                    headlineContent = { Text("${requireNotNull(entry.effectiveWeightInGrams).formatQuickCaptureWeight()} g") },
                    leadingContent = { Icon(Icons.Outlined.Fastfood, contentDescription = null) },
                    trailingContent = {
                        IconButton(onClick = { onDelete(entry) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(Res.string.action_delete))
                        }
                    },
                )
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun QuickCaptureCompletedRow(
    entry: QuickCaptureLogEntry,
    onDelete: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(entry.foodName.orEmpty()) },
        supportingContent = { Text("${entry.effectiveWeightInGrams?.formatQuickCaptureWeight().orEmpty()} g") },
        leadingContent = { Icon(Icons.Outlined.Check, contentDescription = null) },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(Res.string.action_delete))
            }
        },
    )
}

@Composable
internal fun QuickCapturePhotos(
    entries: List<QuickCaptureLogEntry>,
    onPhoto: (Long) -> Unit,
    onDelete: (QuickCaptureLogEntry) -> Unit,
) {
    var deletePhoto by remember { mutableStateOf<QuickCaptureLogEntry?>(null) }
    deletePhoto?.let { entry ->
        QuickCaptureDeleteConfirmationDialog(
            target = QuickCaptureDeleteTarget.Photo,
            onDismiss = { deletePhoto = null },
            onConfirm = {
                deletePhoto = null
                onDelete(entry)
            },
        )
    }
    if (entries.isEmpty()) {
        EmptyQuickCaptureState(
            text = stringResource(Res.string.neutral_quick_capture_empty_photos),
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        gridItems(entries.sortedBy { it.createdAt }, key = { it.id }) { entry ->
            val processPhotoLabel = stringResource(Res.string.action_quick_capture_process_photo)
            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable(onClickLabel = processPhotoLabel) { onPhoto(entry.id) }
            ) {
                PendingProductPhoto(
                    photoPath = requireNotNull(entry.photoPath),
                    photoDirectory = QUICK_CAPTURE_PHOTO_DIRECTORY,
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                )
                FilledTonalIconButton(
                    onClick = { deletePhoto = entry },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = stringResource(Res.string.action_delete))
                }
            }
        }
    }
}

@Composable
internal fun QuickCaptureLibrary(
    names: List<QuickCaptureFoodName>,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var editing by remember { mutableStateOf<QuickCaptureFoodName?>(null) }
    var deleteName by remember { mutableStateOf<QuickCaptureFoodName?>(null) }
    editing?.let { name ->
        RenameFoodNameDialog(
            name = name,
            onDismiss = { editing = null },
            onSave = {
                onRename(name.id, it)
                editing = null
            },
        )
    }
    deleteName?.let { name ->
        QuickCaptureDeleteConfirmationDialog(
            target = QuickCaptureDeleteTarget.FoodName(name.name),
            onDismiss = { deleteName = null },
            onConfirm = {
                deleteName = null
                onDelete(name.id)
            },
        )
    }
    if (names.isEmpty()) {
        EmptyQuickCaptureState(
            text = stringResource(Res.string.neutral_quick_capture_empty_library),
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    LazyColumn {
        items(names, key = { it.id }) { name ->
            ListItem(
                headlineContent = { Text(name.name) },
                supportingContent = {
                    Text(stringResource(Res.string.headline_quick_capture_usage, name.usageCount))
                },
                trailingContent = {
                    Row {
                        IconButton(onClick = { editing = name }) {
                            Icon(Icons.Outlined.Edit, contentDescription = stringResource(Res.string.action_quick_capture_rename))
                        }
                        IconButton(onClick = { deleteName = name }) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(Res.string.action_delete))
                        }
                    }
                },
            )
        }
    }
}

internal sealed interface QuickCaptureDeleteTarget {
    data object Entry : QuickCaptureDeleteTarget

    data class CompletedEntries(val count: Int) : QuickCaptureDeleteTarget

    data object Photo : QuickCaptureDeleteTarget

    data class FoodName(val name: String) : QuickCaptureDeleteTarget
}

@Composable
internal fun QuickCaptureDeleteConfirmationDialog(
    target: QuickCaptureDeleteTarget,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val title =
        when (target) {
            QuickCaptureDeleteTarget.Entry ->
                stringResource(Res.string.headline_quick_capture_delete_entry)
            is QuickCaptureDeleteTarget.CompletedEntries ->
                stringResource(Res.string.headline_quick_capture_delete_completed)
            QuickCaptureDeleteTarget.Photo ->
                stringResource(Res.string.headline_quick_capture_delete_photo)
            is QuickCaptureDeleteTarget.FoodName ->
                stringResource(Res.string.headline_quick_capture_delete_name)
        }
    val description =
        when (target) {
            QuickCaptureDeleteTarget.Entry ->
                stringResource(Res.string.description_quick_capture_delete_entry)
            is QuickCaptureDeleteTarget.CompletedEntries ->
                stringResource(
                    Res.string.description_quick_capture_delete_completed,
                    target.count,
                )
            QuickCaptureDeleteTarget.Photo ->
                stringResource(Res.string.description_quick_capture_delete_photo)
            is QuickCaptureDeleteTarget.FoodName ->
                stringResource(
                    Res.string.description_quick_capture_delete_name,
                    target.name,
                )
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(description) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(Res.string.action_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}

@Composable
private fun EmptyQuickCaptureState(text: String, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun AfterWeightDialog(
    entry: QuickCaptureLogEntry,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit,
) {
    var value by rememberSaveable { mutableStateOf("") }
    val parsed = value.toLocalizedDouble()
    val before = entry.beforeWeightInGrams ?: 0.0
    val valid = parsed != null && parsed >= 0.0 && parsed < before
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.foodName.orEmpty()) },
        text = {
            WeightField(
                value = value,
                onValueChange = { value = it },
                label = stringResource(Res.string.headline_quick_capture_after),
                isError = value.isNotBlank() && !valid,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(requireNotNull(parsed)) }, enabled = valid) {
                Text(stringResource(Res.string.action_quick_capture_save_after))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

@Composable
private fun RenameFoodNameDialog(
    name: QuickCaptureFoodName,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by rememberSaveable(name.id) { mutableStateOf(name.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.action_quick_capture_rename)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(stringResource(Res.string.product_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(value) }, enabled = value.isNotBlank()) {
                Text(stringResource(Res.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

private fun String.toLocalizedDouble(): Double? = replace(',', '.').toDoubleOrNull()

internal class QuickCaptureViewModel(
    observe: ObserveQuickCaptureUseCase,
    private val saveEntry: SaveQuickCaptureEntryUseCase,
    private val capture: CaptureQuickCapturePhotoUseCase,
    private val completeAfter: CompleteQuickCaptureAfterUseCase,
    private val deleteEntries: DeleteQuickCaptureEntriesUseCase,
    private val updateLibrary: UpdateQuickCaptureLibraryUseCase,
    private val settingsRepository: UserPreferencesRepository<Settings>,
    private val csvParser: QuickAddCsvParser,
    private val csvImporter: QuickCaptureCsvImporter,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val entries =
        observe.entries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val names =
        observe.foodNames().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val aggregateSameFoods =
        settingsRepository.observe()
            .map { it.quickCaptureAggregateSameFoods }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val cameraOpen = MutableStateFlow(false)
    val formError = MutableStateFlow<QuickCaptureFormError?>(null)
    val copiedEntryIds = savedStateHandle.getStateFlow(CopiedEntryIdsKey, emptyList<Long>())
    val csvImportState = MutableStateFlow<QuickCaptureCsvImportState>(QuickCaptureCsvImportState.Idle)
    private val eventChannel = Channel<QuickCaptureUiEvent>()
    val events = eventChannel.receiveAsFlow()

    fun openCamera() { cameraOpen.value = true }

    fun closeCamera() { cameraOpen.value = false }

    fun capturePhoto(path: String) {
        viewModelScope.launch { capture.capture(path) }
    }

    fun save(
        name: String,
        mode: QuickCaptureWeightMode,
        direct: Double?,
        before: Double?,
        after: Double?,
    ) {
        viewModelScope.launch {
            when (
                    saveEntry.save(
                        foodName = name,
                        weightMode = mode,
                        directWeightInGrams = direct,
                        beforeWeightInGrams = before,
                        afterWeightInGrams = after,
                    )
                ) {
                    is SaveQuickCaptureEntryResult.Saved -> {
                        formError.value = null
                        eventChannel.send(QuickCaptureUiEvent.EntrySaved)
                    }
                    SaveQuickCaptureEntryResult.InvalidName ->
                        formError.value = QuickCaptureFormError.Name
                    SaveQuickCaptureEntryResult.InvalidWeight ->
                        formError.value = QuickCaptureFormError.Weight
                }
        }
    }

    fun resetFormError() {
        formError.value = null
    }

    fun rememberCopiedBatch(entryIds: List<Long>) {
        savedStateHandle[CopiedEntryIdsKey] = entryIds.distinct()
    }

    fun resetCsvImportError() {
        if (csvImportState.value != QuickCaptureCsvImportState.Submitting) {
            csvImportState.value = QuickCaptureCsvImportState.Idle
        }
    }

    fun importCsv(csv: String) {
        if (csvImportState.value == QuickCaptureCsvImportState.Submitting) return
        val entryIds = copiedEntryIds.value
        if (entryIds.isEmpty()) return

        csvImportState.value = QuickCaptureCsvImportState.Submitting
        viewModelScope.launch {
            try {
                when (val parsed = csvParser.parse(csv)) {
                    is QuickAddCsvParseResult.Failure -> {
                        csvImportState.value = QuickCaptureCsvImportState.InvalidCsv(parsed.error)
                    }
                    is QuickAddCsvParseResult.Success -> {
                        when (csvImporter.import(parsed.data, entryIds)) {
                            QuickCaptureCsvImportResult.NoMeal ->
                                csvImportState.value = QuickCaptureCsvImportState.NoMeal
                            QuickCaptureCsvImportResult.Success -> {
                                savedStateHandle[CopiedEntryIdsKey] = emptyList<Long>()
                                csvImportState.value = QuickCaptureCsvImportState.Idle
                                eventChannel.send(QuickCaptureUiEvent.CsvImported)
                            }
                        }
                    }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                csvImportState.value = QuickCaptureCsvImportState.SavingFailed
            }
        }
    }

    fun completeAfter(id: Long, after: Double) {
        viewModelScope.launch { completeAfter.complete(id, after) }
    }

    fun delete(entries: List<QuickCaptureLogEntry>) {
        viewModelScope.launch { deleteEntries.delete(entries) }
    }

    fun renameName(id: Long, name: String) {
        viewModelScope.launch { updateLibrary.rename(id, name) }
    }

    fun deleteName(id: Long) {
        viewModelScope.launch { updateLibrary.delete(id) }
    }

    fun setAggregateSameFoods(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.update { copy(quickCaptureAggregateSameFoods = value) }
        }
    }
}

internal sealed interface QuickCaptureUiEvent {
    data object EntrySaved : QuickCaptureUiEvent

    data object CsvImported : QuickCaptureUiEvent
}

internal sealed interface QuickCaptureCsvImportState {
    data object Idle : QuickCaptureCsvImportState

    data object Submitting : QuickCaptureCsvImportState

    data class InvalidCsv(val error: QuickAddCsvError) : QuickCaptureCsvImportState

    data object NoMeal : QuickCaptureCsvImportState

    data object SavingFailed : QuickCaptureCsvImportState
}

private const val CopiedEntryIdsKey = "quickCaptureCopiedEntryIds"

internal const val QUICK_CAPTURE_PHOTO_DIRECTORY = "food-snap-photos"

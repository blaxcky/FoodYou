package com.maksimowiczm.foodyou.app.ui.food.quickcapture

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Fastfood
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.app.ui.food.pending.PendingProductPhoto
import com.maksimowiczm.foodyou.app.ui.food.pending.PendingProductPhotoCapture
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

data class QuickCaptureTransferRequest(
    val entryIds: List<Long>,
    val foodName: String,
    val weightInGrams: Double,
)

private enum class QuickCaptureTab { Log, Photos, Library }

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
    val formSavedTick by viewModel.formSavedTick.collectAsStateWithLifecycle()
    val cameraOpen by viewModel.cameraOpen.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableStateOf(QuickCaptureTab.Log) }
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val copiedMessage = stringResource(Res.string.neutral_quick_capture_prompt_copied)

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
            if (selectedTab == QuickCaptureTab.Photos && !cameraOpen) {
                FloatingActionButton(onClick = viewModel::openCamera) {
                    Icon(
                        Icons.Outlined.PhotoCamera,
                        contentDescription = stringResource(Res.string.action_quick_capture_take_photo),
                    )
                }
            }
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
                        modifier = Modifier.fillMaxSize(),
                    )
                selectedTab == QuickCaptureTab.Log ->
                    QuickCaptureLog(
                        entries = entries,
                        names = names,
                        aggregate = aggregate,
                        formError = formError,
                        formSavedTick = formSavedTick,
                        onAggregateChange = viewModel::setAggregateSameFoods,
                        onSave = viewModel::save,
                        onCompleteAfter = viewModel::completeAfter,
                        onDelete = { viewModel.delete(listOf(it)) },
                        onClearCompleted = { viewModel.delete(entries.filter { it.isCompleted }) },
                        onCopyPrompt = {
                            val groups = entries.quickCaptureGroups(aggregate)
                            if (groups.isNotEmpty()) {
                                clipboard.copy("FoodYou quick capture prompt", groups.quickCapturePrompt())
                                coroutineScope.launch { snackbar.showSnackbar(copiedMessage) }
                            }
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
internal fun QuickCaptureLog(
    entries: List<QuickCaptureLogEntry>,
    names: List<QuickCaptureFoodName>,
    aggregate: Boolean,
    formError: QuickCaptureFormError?,
    formSavedTick: Int,
    onAggregateChange: (Boolean) -> Unit,
    onSave: (String, QuickCaptureWeightMode, Double?, Double?, Double?) -> Unit,
    onCompleteAfter: (Long, Double) -> Unit,
    onDelete: (QuickCaptureLogEntry) -> Unit,
    onClearCompleted: () -> Unit,
    onCopyPrompt: () -> Unit,
    onTransfer: (QuickCaptureLogGroup) -> Unit,
) {
    var completed by rememberSaveable { mutableStateOf(false) }
    var afterEntry by remember { mutableStateOf<QuickCaptureLogEntry?>(null) }
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

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        item {
            QuickCaptureEntryForm(
                names = names,
                error = formError,
                savedTick = formSavedTick,
                onSave = onSave,
                modifier = Modifier.padding(16.dp),
            )
        }
        item {
            HorizontalDivider()
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
                        IconButton(onClick = onCopyPrompt, enabled = groups.isNotEmpty()) {
                            Icon(
                                Icons.Outlined.ContentCopy,
                                contentDescription = stringResource(Res.string.action_quick_capture_copy_prompt),
                            )
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
                    TextButton(onClick = onClearCompleted) {
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
                QuickCaptureCompletedRow(entry = entry, onDelete = { onDelete(entry) })
            }
        } else {
            val pending = visibleEntries.filter { !it.isReady }
            items(pending, key = { "pending-${it.id}" }) { entry ->
                QuickCapturePendingRow(
                    entry = entry,
                    onCompleteAfter = { afterEntry = entry },
                    onDelete = { onDelete(entry) },
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
                    onDelete = onDelete,
                )
            }
        }
    }
}

@Composable
internal fun QuickCaptureEntryForm(
    names: List<QuickCaptureFoodName>,
    error: QuickCaptureFormError?,
    savedTick: Int,
    onSave: (String, QuickCaptureWeightMode, Double?, Double?, Double?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(QuickCaptureWeightMode.Direct) }
    var direct by rememberSaveable { mutableStateOf("") }
    var before by rememberSaveable { mutableStateOf("") }
    var after by rememberSaveable { mutableStateOf("") }
    val weightFocus = remember { FocusRequester() }

    fun submit() {
        onSave(
            name,
            mode,
            direct.toLocalizedDouble(),
            before.toLocalizedDouble(),
            after.toLocalizedDouble(),
        )
    }

    LaunchedEffect(savedTick) {
        if (savedTick > 0) {
            name = ""
            direct = ""
            before = ""
            after = ""
        }
    }

    Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, tonalElevation = 2.dp) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
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
}

@Composable
internal fun QuickCaptureNameField(
    value: String,
    onValueChange: (String) -> Unit,
    names: List<QuickCaptureFoodName>,
    onNameConfirmed: (String) -> Unit,
    isError: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val suggestions =
        remember(value, names) {
            names.filter { value.isBlank() || it.name.contains(value, ignoreCase = true) }.take(8)
        }
    val menuExpanded = expanded && suggestions.isNotEmpty()
    ExposedDropdownMenuBox(
        expanded = menuExpanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            modifier =
                Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                    .fillMaxWidth(),
            label = { Text(stringResource(Res.string.product_name)) },
            singleLine = true,
            isError = isError,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { onNameConfirmed(value) }),
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
    if (entries.isEmpty()) {
        EmptyQuickCaptureState(
            text = stringResource(Res.string.neutral_quick_capture_empty_photos),
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
        items(entries.sortedBy { it.createdAt }, key = { it.id }) { entry ->
            ListItem(
                modifier = Modifier.clickable { onPhoto(entry.id) },
                headlineContent = { Text(stringResource(Res.string.action_quick_capture_process_photo)) },
                supportingContent = { Text(stringResource(Res.string.description_quick_capture_photo_flow)) },
                leadingContent = {
                    PendingProductPhoto(
                        photoPath = requireNotNull(entry.photoPath),
                        photoDirectory = QUICK_CAPTURE_PHOTO_DIRECTORY,
                        modifier = Modifier.size(72.dp),
                    )
                },
                trailingContent = {
                    IconButton(onClick = { onDelete(entry) }) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(Res.string.action_delete))
                    }
                },
            )
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
                        IconButton(onClick = { onDelete(name.id) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(Res.string.action_delete))
                        }
                    }
                },
            )
        }
    }
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
    val formSavedTick = MutableStateFlow(0)

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
                        formSavedTick.value += 1
                    }
                    SaveQuickCaptureEntryResult.InvalidName ->
                        formError.value = QuickCaptureFormError.Name
                    SaveQuickCaptureEntryResult.InvalidWeight ->
                        formError.value = QuickCaptureFormError.Weight
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

internal const val QUICK_CAPTURE_PHOTO_DIRECTORY = "food-snap-photos"

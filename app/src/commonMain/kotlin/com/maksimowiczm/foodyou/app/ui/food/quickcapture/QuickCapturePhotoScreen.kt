package com.maksimowiczm.foodyou.app.ui.food.quickcapture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.RotateLeft
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.app.ui.food.pending.ZoomablePendingProductPhoto
import com.maksimowiczm.foodyou.common.compose.extension.LaunchedCollectWithLifecycle
import com.maksimowiczm.foodyou.food.domain.entity.QuickCaptureFoodName
import com.maksimowiczm.foodyou.food.domain.entity.QuickCaptureLogEntry
import com.maksimowiczm.foodyou.food.domain.usecase.DeleteQuickCaptureEntriesUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.ObserveQuickCaptureUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.ProcessQuickCapturePhotoUseCase
import foodyou.app.generated.resources.Res
import foodyou.app.generated.resources.action_delete
import foodyou.app.generated.resources.action_rotate_photo_left
import foodyou.app.generated.resources.action_rotate_photo_right
import foodyou.app.generated.resources.error_quick_capture_weight
import foodyou.app.generated.resources.headline_quick_capture_direct
import foodyou.app.generated.resources.headline_quick_capture_photos
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun QuickCapturePhotoScreen(
    entryId: Long,
    onBack: () -> Unit,
    onProcessed: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: QuickCapturePhotoViewModel = koinViewModel { parametersOf(entryId) }
    val entry by viewModel.entry.collectAsStateWithLifecycle()
    val names by viewModel.names.collectAsStateWithLifecycle()
    var rotation by rememberSaveable(entryId) { mutableIntStateOf(0) }
    var confirmDelete by rememberSaveable(entryId) { mutableStateOf(false) }

    LaunchedCollectWithLifecycle(viewModel.events) { event ->
        when (event) {
            is QuickCapturePhotoEvent.Processed -> onProcessed(event.nextEntryId)
            QuickCapturePhotoEvent.Deleted -> onBack()
        }
    }

    if (confirmDelete && entry != null) {
        QuickCaptureDeleteConfirmationDialog(
            target = QuickCaptureDeleteTarget.Photo,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                viewModel.delete()
            },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.headline_quick_capture_photos)) },
                navigationIcon = { ArrowBackIconButton(onBack) },
                actions = {
                    if (entry != null) {
                        QuickCapturePhotoActions(
                            onRotateLeft = { rotation = (rotation + 270) % 360 },
                            onRotateRight = { rotation = (rotation + 90) % 360 },
                            onDelete = { confirmDelete = true },
                        )
                    }
                },
            )
        },
    ) { padding ->
        val current = entry ?: return@Scaffold
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
        ) {
            ZoomablePendingProductPhoto(
                photoPath = requireNotNull(current.photoPath),
                rotationDegrees = rotation.toFloat(),
                photoDirectory = QUICK_CAPTURE_PHOTO_DIRECTORY,
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp),
            )
            QuickCapturePhotoEditor(
                entryId = current.id,
                names = names,
                onProcess = viewModel::process,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}

internal enum class QuickCapturePhotoStep {
    Name,
    Weight,
}

@Composable
internal fun QuickCapturePhotoEditor(
    entryId: Long,
    names: List<QuickCaptureFoodName>,
    onProcess: (String, Double) -> Unit,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = true,
) {
    var name by rememberSaveable(entryId) { mutableStateOf("") }
    var weight by rememberSaveable(entryId) { mutableStateOf("") }
    var step by rememberSaveable(entryId) { mutableStateOf(QuickCapturePhotoStep.Name) }
    var nameSubmitted by rememberSaveable(entryId) { mutableStateOf(false) }
    var submitted by rememberSaveable(entryId) { mutableStateOf(false) }
    var processing by remember(entryId) { mutableStateOf(false) }
    val parsedWeight = weight.replace(',', '.').toDoubleOrNull()
    val valid = name.isNotBlank() && parsedWeight?.let { it.isFinite() && it > 0.0 } == true

    fun submit() {
        if (processing) return
        submitted = true
        if (valid) {
            processing = true
            onProcess(name, requireNotNull(parsedWeight))
        }
    }

    fun confirmName(confirmedName: String) {
        name = confirmedName
        nameSubmitted = true
        if (confirmedName.isNotBlank()) step = QuickCapturePhotoStep.Weight
    }

    QuickCapturePhotoForm(
        step = step,
        name = name,
        onNameChange = { name = it },
        onNameConfirmed = ::confirmName,
        weight = weight,
        onWeightChange = { weight = it },
        names = names,
        nameError = nameSubmitted && name.isBlank(),
        submitted = submitted,
        processing = processing,
        autoFocusKey = entryId.takeIf { autoFocus },
        onSubmit = ::submit,
        modifier = modifier,
    )
}

@Composable
internal fun QuickCapturePhotoForm(
    step: QuickCapturePhotoStep,
    name: String,
    onNameChange: (String) -> Unit,
    onNameConfirmed: (String) -> Unit,
    weight: String,
    onWeightChange: (String) -> Unit,
    names: List<QuickCaptureFoodName>,
    nameError: Boolean,
    submitted: Boolean,
    processing: Boolean,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    autoFocusKey: Long? = null,
) {
    val nameFocus = remember { FocusRequester() }
    val weightFocus = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val parsedWeight = weight.replace(',', '.').toDoubleOrNull()
    val valid = name.isNotBlank() && parsedWeight?.let { it.isFinite() && it > 0.0 } == true

    LaunchedEffect(autoFocusKey, step) {
        if (autoFocusKey != null) {
            when (step) {
                QuickCapturePhotoStep.Name -> nameFocus.requestFocus()
                QuickCapturePhotoStep.Weight -> weightFocus.requestFocus()
            }
            keyboardController?.show()
        }
    }

    when (step) {
        QuickCapturePhotoStep.Name ->
            QuickCaptureNameField(
                value = name,
                onValueChange = onNameChange,
                names = names,
                onNameConfirmed = onNameConfirmed,
                isError = nameError,
                modifier = modifier,
                textFieldModifier = Modifier.focusRequester(nameFocus),
            )
        QuickCapturePhotoStep.Weight ->
            OutlinedTextField(
                value = weight,
                onValueChange = onWeightChange,
                modifier = modifier.fillMaxWidth().focusRequester(weightFocus),
                label = { Text(stringResource(Res.string.headline_quick_capture_direct)) },
                suffix = { Text("g") },
                singleLine = true,
                enabled = !processing,
                isError = submitted && parsedWeight?.let { !it.isFinite() || it <= 0.0 } != false,
                supportingText = {
                    if (submitted && !valid) {
                        Text(stringResource(Res.string.error_quick_capture_weight))
                    }
                },
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                keyboardActions = KeyboardActions(onDone = { if (!processing) onSubmit() }),
            )
    }
}

@Composable
internal fun QuickCapturePhotoActions(
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    onDelete: () -> Unit,
) {
    IconButton(onClick = onRotateLeft) {
        Icon(
            Icons.AutoMirrored.Outlined.RotateLeft,
            contentDescription = stringResource(Res.string.action_rotate_photo_left),
        )
    }
    IconButton(onClick = onRotateRight) {
        Icon(
            Icons.AutoMirrored.Outlined.RotateRight,
            contentDescription = stringResource(Res.string.action_rotate_photo_right),
        )
    }
    IconButton(onClick = onDelete) {
        Icon(
            Icons.Outlined.Delete,
            contentDescription = stringResource(Res.string.action_delete),
        )
    }
}

internal sealed interface QuickCapturePhotoEvent {
    data class Processed(val nextEntryId: Long?) : QuickCapturePhotoEvent
    data object Deleted : QuickCapturePhotoEvent
}

internal class QuickCapturePhotoViewModel(
    private val entryId: Long,
    observe: ObserveQuickCaptureUseCase,
    private val processPhoto: ProcessQuickCapturePhotoUseCase,
    private val deleteEntries: DeleteQuickCaptureEntriesUseCase,
) : ViewModel() {
    private val allEntries =
        observe.entries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val entry =
        allEntries.map { entries -> entries.firstOrNull { it.id == entryId } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val names =
        observe.foodNames().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val eventChannel = Channel<QuickCapturePhotoEvent>()
    val events = eventChannel.receiveAsFlow()

    fun process(name: String, weight: Double) {
        viewModelScope.launch {
            if (processPhoto.process(entryId, name, weight)) {
                val next =
                    allEntries.value
                        .asSequence()
                        .filter { it.id != entryId && it.isPendingPhoto }
                        .minByOrNull(QuickCaptureLogEntry::createdAt)
                        ?.id
                eventChannel.send(QuickCapturePhotoEvent.Processed(next))
            }
        }
    }

    fun delete() {
        viewModelScope.launch {
            entry.value?.let { deleteEntries.delete(listOf(it)) }
            eventChannel.send(QuickCapturePhotoEvent.Deleted)
        }
    }
}

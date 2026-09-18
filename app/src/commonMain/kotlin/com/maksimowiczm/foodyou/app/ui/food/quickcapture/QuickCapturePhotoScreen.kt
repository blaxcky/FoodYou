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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.maksimowiczm.foodyou.app.ui.food.pending.PendingProductPhotoPager
import com.maksimowiczm.foodyou.common.compose.extension.LaunchedCollectWithLifecycle
import com.maksimowiczm.foodyou.food.domain.entity.QuickCaptureLogEntry
import com.maksimowiczm.foodyou.food.domain.usecase.DeleteQuickCaptureEntriesUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.ObserveQuickCaptureUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.ProcessQuickCapturePhotoUseCase
import foodyou.app.generated.resources.Res
import foodyou.app.generated.resources.action_delete
import foodyou.app.generated.resources.action_quick_capture_process_photo
import foodyou.app.generated.resources.description_quick_capture_photo_flow
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
    var name by rememberSaveable { mutableStateOf("") }
    var weight by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val weightFocus = remember { FocusRequester() }
    val parsedWeight = weight.replace(',', '.').toDoubleOrNull()
    val valid = name.isNotBlank() && parsedWeight?.let { it.isFinite() && it > 0.0 } == true

    LaunchedCollectWithLifecycle(viewModel.events) { event ->
        when (event) {
            is QuickCapturePhotoEvent.Processed -> onProcessed(event.nextEntryId)
            QuickCapturePhotoEvent.Deleted -> onBack()
        }
    }

    fun submit() {
        submitted = true
        if (valid) viewModel.process(name, requireNotNull(parsedWeight))
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.headline_quick_capture_photos)) },
                navigationIcon = { ArrowBackIconButton(onBack) },
                actions = {
                    IconButton(onClick = viewModel::delete) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(Res.string.action_delete))
                    }
                },
            )
        },
    ) { padding ->
        val current = entry ?: return@Scaffold
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PendingProductPhotoPager(
                photoPaths = listOf(requireNotNull(current.photoPath)),
                photoDirectory = QUICK_CAPTURE_PHOTO_DIRECTORY,
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp),
            )
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(Res.string.description_quick_capture_photo_flow))
                QuickCaptureNameField(
                    value = name,
                    onValueChange = { name = it },
                    names = names,
                    onNameConfirmed = {
                        name = it
                        weightFocus.requestFocus()
                    },
                    isError = submitted && name.isBlank(),
                )
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(weightFocus),
                    label = { Text(stringResource(Res.string.headline_quick_capture_direct)) },
                    suffix = { Text("g") },
                    singleLine = true,
                    isError = submitted && parsedWeight?.let { !it.isFinite() || it <= 0.0 } != false,
                    supportingText = {
                        if (submitted && !valid) {
                            Text(stringResource(Res.string.error_quick_capture_weight))
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )
                Button(onClick = { submit() }, enabled = valid, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Save, contentDescription = null)
                    Text(stringResource(Res.string.action_quick_capture_process_photo))
                }
            }
        }
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

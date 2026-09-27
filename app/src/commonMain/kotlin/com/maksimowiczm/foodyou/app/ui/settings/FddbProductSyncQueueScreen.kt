package com.maksimowiczm.foodyou.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.common.compose.utility.LocalDateFormatter
import com.maksimowiczm.foodyou.food.domain.entity.FddbProductSyncQueueItem
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.usecase.FddbProductSyncBatchProgress
import com.maksimowiczm.foodyou.food.domain.usecase.FddbProductSyncManualBatchState
import com.maksimowiczm.foodyou.settings.domain.entity.FddbProductSyncManualFrequency
import com.maksimowiczm.foodyou.settings.domain.entity.FddbProductSyncMode
import foodyou.app.generated.resources.*
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun FddbProductSyncQueueScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FddbProductSyncQueueViewModel = koinViewModel(),
) {
    val model = viewModel.model.collectAsStateWithLifecycle().value
    val actionState = viewModel.actionState.collectAsStateWithLifecycle().value
    val uriHandler = LocalUriHandler.current

    FddbProductSyncQueueContent(
        model = model,
        actionState = actionState,
        onBack = onBack,
        onOpenUrl = uriHandler::openUri,
        onRetry = viewModel::retry,
        onUpdateLink = viewModel::updateLink,
        onUnlink = viewModel::unlink,
        onClearActionError = viewModel::clearActionError,
        onStartManualBatch = viewModel::startManualBatch,
        onClearManualBatchResult = viewModel::clearManualBatchResult,
        modifier = modifier,
    )
}

@Composable
internal fun FddbProductSyncQueueContent(
    model: FddbProductSyncQueueModel,
    actionState: FddbProductSyncActionState,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onRetry: (FoodId.Product) -> Unit,
    onUpdateLink: (FoodId.Product, String) -> Unit,
    onUnlink: (FoodId.Product) -> Unit,
    onClearActionError: () -> Unit,
    onStartManualBatch: (Int) -> Unit,
    onClearManualBatchResult: () -> Unit,
    modifier: Modifier = Modifier,
    initialSelectedProductId: Long? = null,
    initialConfirmUnlink: Boolean = false,
    initialRemainingExpanded: Boolean = false,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var selectedProductId by rememberSaveable {
        mutableStateOf(initialSelectedProductId)
    }
    var editedUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmUnlink by rememberSaveable { mutableStateOf(initialConfirmUnlink) }
    var showManualBatchDialog by rememberSaveable { mutableStateOf(false) }
    val selectedItem = model.queue.firstOrNull { it.productId.id == selectedProductId }
    val batchRunning = model.manualBatchState is FddbProductSyncManualBatchState.Running
    val actionsBusy = batchRunning || actionState.inProgress
    val sections = remember(model.queue) { model.queue.toSections() }
    var remainingExpanded by rememberSaveable { mutableStateOf(initialRemainingExpanded) }

    LaunchedEffect(selectedItem) {
        if (selectedItem == null) {
            editedUrl = null
            confirmUnlink = false
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            MediumFlexibleTopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.headline_fddb_product_sync_settings),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = { ArrowBackIconButton(onBack) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = paddingValues,
        ) {
            item {
                FddbProductSyncOverviewCard(
                    model = model,
                    startEnabled = model.queue.isNotEmpty() && !actionsBusy,
                    onStart = {
                        onClearManualBatchResult()
                        showManualBatchDialog = true
                    },
                    onClearResult = onClearManualBatchResult,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (model.queue.isEmpty()) {
                item {
                    ListItem(
                        headlineContent = {
                            Text(stringResource(Res.string.neutral_fddb_product_sync_empty))
                        }
                    )
                }
            } else {
                val onItemClick = { item: FddbProductSyncQueueItem ->
                    onClearActionError()
                    selectedProductId = item.productId.id
                }
                if (sections.failed.isNotEmpty()) {
                    item(key = "header_failed") {
                        FddbProductSyncSectionHeader(
                            stringResource(
                                Res.string.headline_fddb_product_sync_section_failed,
                                sections.failed.size,
                            )
                        )
                    }
                    items(sections.failed, key = { it.productId.id }) { item ->
                        FddbProductSyncQueueListItem(
                            item = item,
                            enabled = !actionsBusy,
                            onClick = { onItemClick(item) },
                        )
                    }
                }
                if (sections.next.isNotEmpty()) {
                    item(key = "header_next") {
                        FddbProductSyncSectionHeader(
                            stringResource(
                                Res.string.headline_fddb_product_sync_section_next,
                                sections.next.size,
                            )
                        )
                    }
                    items(sections.next, key = { it.productId.id }) { item ->
                        FddbProductSyncQueueListItem(
                            item = item,
                            enabled = !actionsBusy,
                            onClick = { onItemClick(item) },
                        )
                    }
                }
                if (sections.remaining.isNotEmpty()) {
                    item(key = "header_remaining") {
                        FddbProductSyncExpandableSectionHeader(
                            text =
                                stringResource(
                                    Res.string.headline_fddb_product_sync_section_remaining,
                                    sections.remaining.size,
                                ),
                            expanded = remainingExpanded,
                            onToggle = { remainingExpanded = !remainingExpanded },
                        )
                    }
                    if (remainingExpanded) {
                        items(sections.remaining, key = { it.productId.id }) { item ->
                            FddbProductSyncQueueListItem(
                                item = item,
                                enabled = !actionsBusy,
                                onClick = { onItemClick(item) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showManualBatchDialog) {
        ManualFddbProductSyncDialog(
            queueSize = model.queue.size,
            onDismiss = { showManualBatchDialog = false },
            onStart = { limit ->
                showManualBatchDialog = false
                onStartManualBatch(limit)
            },
        )
    }

    selectedItem?.let { item ->
        when {
            editedUrl != null ->
                EditFddbLinkDialog(
                    url = requireNotNull(editedUrl),
                    onUrlChange = { editedUrl = it },
                    onDismiss = { editedUrl = null },
                    onSave = {
                        val url = requireNotNull(editedUrl)
                        editedUrl = null
                        onUpdateLink(item.productId, url)
                    },
                )
            confirmUnlink ->
                ConfirmFddbUnlinkDialog(
                    onDismiss = { confirmUnlink = false },
                    onConfirm = {
                        confirmUnlink = false
                        onUnlink(item.productId)
                    },
                )
            else ->
                FddbProductSyncDetailsDialog(
                    item = item,
                    actionState = actionState.takeIf { it.productId == item.productId },
                    batchRunning = batchRunning,
                    onDismiss = { selectedProductId = null },
                    onOpenUrl = { onOpenUrl(item.sourceUrl) },
                    onEditUrl = {
                        onClearActionError()
                        editedUrl = item.sourceUrl
                    },
                    onRetry = { onRetry(item.productId) },
                    onUnlink = { confirmUnlink = true },
                )
        }
    }
}

@Composable
private fun FddbProductSyncOverviewCard(
    model: FddbProductSyncQueueModel,
    startEnabled: Boolean,
    onStart: () -> Unit,
    onClearResult: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val neverSynced = model.queue.count { it.lastSyncedAt == null }
    val failed = model.queue.count { it.lastError != null }

    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = model.scheduleStatusText(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                FddbProductSyncStat(
                    value = model.queue.size,
                    label = stringResource(Res.string.label_fddb_product_sync_stat_total),
                    modifier = Modifier.weight(1f),
                )
                FddbProductSyncStat(
                    value = neverSynced,
                    label = stringResource(Res.string.label_fddb_product_sync_stat_never),
                    modifier = Modifier.weight(1f),
                )
                FddbProductSyncStat(
                    value = failed,
                    label = stringResource(Res.string.label_fddb_product_sync_stat_failed),
                    isError = failed > 0,
                    modifier = Modifier.weight(1f),
                )
            }
            FilledTonalButton(
                onClick = onStart,
                enabled = startEnabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Outlined.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(Res.string.action_start_manual_fddb_product_sync_full))
            }
            if (model.manualBatchState !is FddbProductSyncManualBatchState.Idle) {
                HorizontalDivider()
                FddbProductSyncManualBatchStatus(
                    state = model.manualBatchState,
                    onClearResult = onClearResult,
                )
            }
        }
    }
}

@Composable
private fun FddbProductSyncStat(
    value: Int,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleLarge,
            color =
                if (isError) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FddbProductSyncSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun FddbProductSyncExpandableSectionHeader(
    text: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun FddbProductSyncQueueListItem(
    item: FddbProductSyncQueueItem,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(text = item.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = listOfNotNull(item.brand, item.lastSuccessShortText()).joinToString(" · "),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.lastError?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
            )
        },
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    )
}

@Composable
private fun FddbProductSyncDetailsDialog(
    item: FddbProductSyncQueueItem,
    actionState: FddbProductSyncActionState?,
    batchRunning: Boolean,
    onDismiss: () -> Unit,
    onOpenUrl: () -> Unit,
    onEditUrl: () -> Unit,
    onRetry: () -> Unit,
    onUnlink: () -> Unit,
) {
    val busy = actionState?.inProgress == true || batchRunning
    val actionError = actionState?.error?.message()
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(Res.string.headline_fddb_product_sync_details)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(item.headline, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(Res.string.neutral_fddb_source_url),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(item.sourceUrl, style = MaterialTheme.typography.bodySmall)
                Text(item.lastSuccessText(), style = MaterialTheme.typography.bodyMedium)
                item.lastAttemptAt?.let {
                    Text(
                        stringResource(
                            Res.string.neutral_fddb_product_sync_last_attempt,
                            it.formatDateTime(),
                        )
                    )
                }
                item.lastError?.let {
                    Text(
                        stringResource(Res.string.neutral_fddb_product_sync_error, it),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                actionError?.takeIf { it != item.lastError }?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                if (busy) {
                    CircularProgressIndicator()
                    Text(stringResource(Res.string.neutral_fddb_product_sync_running))
                }
                Button(onClick = onOpenUrl, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.OpenInBrowser, contentDescription = null)
                    Text(stringResource(Res.string.action_open_fddb_page))
                }
                OutlinedButton(
                    onClick = onEditUrl,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Edit, contentDescription = null)
                    Text(stringResource(Res.string.action_change_fddb_link))
                }
                OutlinedButton(
                    onClick = onRetry,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Sync, contentDescription = null)
                    Text(stringResource(Res.string.action_retry_fddb_product))
                }
                TextButton(
                    onClick = onUnlink,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.LinkOff, contentDescription = null)
                    Text(stringResource(Res.string.action_unlink_fddb_product))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(Res.string.action_close))
            }
        },
    )
}

@Composable
private fun ManualFddbProductSyncDialog(
    queueSize: Int,
    onDismiss: () -> Unit,
    onStart: (Int) -> Unit,
) {
    var input by rememberSaveable(queueSize) {
        mutableStateOf(minOf(DEFAULT_MANUAL_BATCH_SIZE, queueSize).toString())
    }
    val limit = input.toIntOrNull()
    val valid = limit != null && limit in 1..queueSize

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.headline_manual_fddb_product_sync)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.description_manual_fddb_product_sync))
                OutlinedTextField(
                    value = input,
                    onValueChange = { value ->
                        if (value.all(Char::isDigit)) input = value
                    },
                    label = { Text(stringResource(Res.string.label_fddb_product_sync_count)) },
                    supportingText = {
                        Text(
                            stringResource(
                                if (valid) {
                                    Res.string.description_fddb_product_sync_count_range
                                } else {
                                    Res.string.error_fddb_product_sync_count_range
                                },
                                queueSize,
                            )
                        )
                    },
                    isError = !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onStart(requireNotNull(limit)) }, enabled = valid) {
                Text(stringResource(Res.string.action_start_manual_fddb_product_sync))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}

@Composable
private fun FddbProductSyncManualBatchStatus(
    state: FddbProductSyncManualBatchState,
    onClearResult: () -> Unit,
) {
    when (state) {
        FddbProductSyncManualBatchState.Idle -> Unit
        is FddbProductSyncManualBatchState.Running ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(Res.string.neutral_fddb_product_sync_running),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text =
                            stringResource(
                                Res.string.neutral_fddb_product_sync_batch_processed,
                                state.progress.processed,
                                state.progress.total,
                            ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.progress.total > 0) {
                    LinearProgressIndicator(
                        progress = {
                            state.progress.processed.toFloat() / state.progress.total.toFloat()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                FddbProductSyncBatchCounts(state.progress)
            }
        is FddbProductSyncManualBatchState.Completed ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FddbProductSyncBatchResultHeader(
                    text = stringResource(Res.string.headline_fddb_product_sync_batch_completed),
                    onClearResult = onClearResult,
                )
                FddbProductSyncBatchCounts(state.progress)
                if (state.progress.blocked) {
                    Text(
                        text = stringResource(Res.string.error_fddb_product_sync_batch_blocked),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        is FddbProductSyncManualBatchState.Failed ->
            FddbProductSyncBatchResultHeader(
                text = stringResource(Res.string.error_fddb_product_sync_batch_failed, state.detail),
                onClearResult = onClearResult,
                color = MaterialTheme.colorScheme.error,
            )
    }
}

@Composable
private fun FddbProductSyncBatchResultHeader(
    text: String,
    onClearResult: () -> Unit,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = color,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onClearResult) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(Res.string.action_close),
            )
        }
    }
}

@Composable
private fun FddbProductSyncBatchCounts(progress: FddbProductSyncBatchProgress) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        FddbProductSyncBatchCount(
            icon = Icons.Outlined.CheckCircle,
            text = stringResource(Res.string.neutral_fddb_product_sync_count_synced, progress.synced),
            color = MaterialTheme.colorScheme.primary,
        )
        FddbProductSyncBatchCount(
            icon = Icons.Outlined.ErrorOutline,
            text = stringResource(Res.string.neutral_fddb_product_sync_count_failed, progress.failed),
            color =
                if (progress.failed > 0) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FddbProductSyncBatchCount(icon: ImageVector, text: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

@Composable
private fun EditFddbLinkDialog(
    url: String,
    onUrlChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.headline_change_fddb_link)) },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                label = { Text(stringResource(Res.string.neutral_fddb_source_url)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = onSave) { Text(stringResource(Res.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}

@Composable
private fun ConfirmFddbUnlinkDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.headline_unlink_fddb_product)) },
        text = { Text(stringResource(Res.string.description_unlink_fddb_product)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(Res.string.action_unlink_fddb_product))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}

@Composable
private fun FddbProductSyncActionError.message(): String =
    when (this) {
        FddbProductSyncActionError.InvalidUrl ->
            stringResource(Res.string.error_invalid_fddb_url)
        FddbProductSyncActionError.AlreadyLinked ->
            stringResource(Res.string.error_fddb_url_already_linked)
        FddbProductSyncActionError.ProductUnavailable ->
            stringResource(Res.string.error_fddb_product_unavailable)
        is FddbProductSyncActionError.SyncFailed -> detail
    }

private val FddbProductSyncQueueItem.headline: String
    get() = listOfNotNull(name, brand).joinToString(" · ")

@Composable
private fun FddbProductSyncQueueItem.lastSuccessShortText(): String =
    lastSyncedAt?.let {
        stringResource(
            Res.string.neutral_fddb_product_sync_last_success_short,
            LocalDateFormatter.current.formatDateShort(
                it.toLocalDateTime(TimeZone.currentSystemDefault()).date
            ),
        )
    } ?: stringResource(Res.string.neutral_fddb_product_sync_never_short)

@Composable
private fun FddbProductSyncQueueItem.lastSuccessText(): String =
    lastSyncedAt?.let {
        stringResource(Res.string.neutral_fddb_product_sync_last_success, it.formatDateTime())
    } ?: stringResource(Res.string.neutral_fddb_product_sync_never)

@Composable
private fun Instant.formatDateTime(): String =
    LocalDateFormatter.current.formatDateTime(toLocalDateTime(TimeZone.currentSystemDefault()))

@Composable
private fun FddbProductSyncQueueModel.scheduleStatusText(): String =
    when (syncMode) {
        FddbProductSyncMode.WithManualFddbSync ->
            when (manualFrequency) {
                FddbProductSyncManualFrequency.EverySync ->
                    stringResource(Res.string.neutral_fddb_product_sync_manual_every_time)
                FddbProductSyncManualFrequency.EveryThirdSync ->
                    stringResource(
                        Res.string.neutral_fddb_product_sync_manual_progress,
                        manualTriggerCount,
                    )
            }
        FddbProductSyncMode.Disabled ->
            stringResource(Res.string.neutral_fddb_product_sync_disabled)
    }

private const val DEFAULT_MANUAL_BATCH_SIZE = 15

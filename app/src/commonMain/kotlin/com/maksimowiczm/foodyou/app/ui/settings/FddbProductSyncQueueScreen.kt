package com.maksimowiczm.foodyou.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.common.compose.utility.LocalDateFormatter
import com.maksimowiczm.foodyou.food.domain.entity.FddbProductSyncQueueItem
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
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
    modifier: Modifier = Modifier,
    initialSelectedProductId: Long? = null,
    initialConfirmUnlink: Boolean = false,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var selectedProductId by rememberSaveable {
        mutableStateOf(initialSelectedProductId)
    }
    var editedUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmUnlink by rememberSaveable { mutableStateOf(initialConfirmUnlink) }
    val selectedItem = model.queue.firstOrNull { it.productId.id == selectedProductId }

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
                title = { Text(stringResource(Res.string.headline_fddb_product_sync_queue)) },
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
                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(
                                Res.string.neutral_fddb_product_sync_progress,
                                model.progress,
                            )
                        )
                    }
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
                itemsIndexed(model.queue, key = { _, item -> item.productId.id }) { index, item ->
                    FddbProductSyncQueueListItem(
                        item = item,
                        isNext = index < 2,
                        onClick = {
                            onClearActionError()
                            selectedProductId = item.productId.id
                        },
                    )
                }
            }
        }
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
private fun FddbProductSyncQueueListItem(
    item: FddbProductSyncQueueItem,
    isNext: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(item.headline) },
        supportingContent = {
            Column(
                modifier = Modifier.padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(item.lastSuccessText(), style = MaterialTheme.typography.bodyMedium)
                item.lastAttemptAt?.let {
                    Text(
                        stringResource(
                            Res.string.neutral_fddb_product_sync_last_attempt,
                            it.formatDateTime(),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                item.lastError?.let {
                    Text(
                        stringResource(Res.string.neutral_fddb_product_sync_error, it),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        trailingContent = {
            if (isNext) {
                AssistChip(
                    onClick = onClick,
                    label = { Text(stringResource(Res.string.neutral_fddb_product_sync_next)) },
                )
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun FddbProductSyncDetailsDialog(
    item: FddbProductSyncQueueItem,
    actionState: FddbProductSyncActionState?,
    onDismiss: () -> Unit,
    onOpenUrl: () -> Unit,
    onEditUrl: () -> Unit,
    onRetry: () -> Unit,
    onUnlink: () -> Unit,
) {
    val busy = actionState?.inProgress == true
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
private fun FddbProductSyncQueueItem.lastSuccessText(): String =
    lastSyncedAt?.let {
        stringResource(Res.string.neutral_fddb_product_sync_last_success, it.formatDateTime())
    } ?: stringResource(Res.string.neutral_fddb_product_sync_never)

@Composable
private fun Instant.formatDateTime(): String =
    LocalDateFormatter.current.formatDateTime(toLocalDateTime(TimeZone.currentSystemDefault()))

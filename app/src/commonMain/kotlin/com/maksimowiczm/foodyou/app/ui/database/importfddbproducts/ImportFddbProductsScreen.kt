package com.maksimowiczm.foodyou.app.ui.database.importfddbproducts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.common.compose.extension.add
import com.maksimowiczm.foodyou.common.compose.utility.LocalClipboardManager
import com.maksimowiczm.foodyou.food.domain.entity.FddbImportQueueItem
import com.maksimowiczm.foodyou.food.domain.usecase.FddbImportResult
import com.maksimowiczm.foodyou.food.domain.usecase.FddbSkipReason
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ImportFddbProductsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: ImportFddbProductsViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ImportFddbProductsScreen(
        uiState = uiState,
        linkCount = viewModel::countLinks,
        onAddLinks = viewModel::addLinks,
        onImport = viewModel::importQueue,
        onDeleteQueueItem = viewModel::deleteQueueItem,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
private fun ImportFddbProductsScreen(
    uiState: ImportFddbProductsUiState,
    linkCount: (String) -> Int,
    onAddLinks: (String) -> Unit,
    onImport: () -> Unit,
    onDeleteQueueItem: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val clipboardManager = LocalClipboardManager.current
    var text by rememberSaveable { mutableStateOf("") }
    val count = remember(text) { linkCount(text) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.action_import_fddb_products)) },
                navigationIcon = { ArrowBackIconButton(onBack, enabled = !uiState.isImporting) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier =
                Modifier.fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = 16.dp),
            contentPadding = paddingValues.add(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = stringResource(Res.string.description_import_fddb_products),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            item {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isImporting,
                    label = { Text(stringResource(Res.string.label_fddb_links)) },
                    minLines = 6,
                    maxLines = 12,
                    trailingIcon = {
                        IconButton(
                            onClick = { clipboardManager.paste()?.let { text = it } },
                            enabled = !uiState.isImporting,
                        ) {
                            Icon(Icons.Outlined.ContentPaste, null)
                        }
                    },
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.neutral_fddb_links_found, count),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = {
                            onAddLinks(text)
                            text = ""
                        },
                        enabled = count > 0 && !uiState.isImporting,
                    ) {
                        Text(stringResource(Res.string.action_add))
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text =
                            stringResource(
                                Res.string.neutral_fddb_queue_count,
                                uiState.queue.size,
                            ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Button(
                        onClick = onImport,
                        enabled = uiState.queue.isNotEmpty() && !uiState.isImporting,
                    ) {
                        Icon(Icons.Outlined.FileDownload, null)
                        Text(stringResource(Res.string.action_import))
                    }
                }
            }

            items(uiState.queue, key = { it.id }) { item ->
                FddbQueueItem(
                    item = item,
                    enabled = !uiState.isImporting,
                    onDelete = { onDeleteQueueItem(item.id) },
                )
            }

            if (uiState.isImporting || uiState.progress.results.isNotEmpty()) {
                item { ImportProgress(uiState) }
            }

            items(uiState.progress.results) { result -> ImportResultItem(result) }
        }
    }
}

@Composable
private fun FddbQueueItem(item: FddbImportQueueItem, enabled: Boolean, onDelete: () -> Unit) {
    val supportingContent: (@Composable () -> Unit)? =
        item.lastError?.let { error ->
            {
                Text(
                    text = error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

    ListItem(
        headlineContent = {
            Text(
                text = item.url.toFddbQueueLabel(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = supportingContent,
        trailingContent = {
            IconButton(onClick = onDelete, enabled = enabled) {
                Icon(Icons.Outlined.Delete, null)
            }
        },
    )
}

@Composable
private fun ImportProgress(uiState: ImportFddbProductsUiState) {
    val progress = uiState.progress
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (progress.total > 0) {
            LinearProgressIndicator(
                progress = { progress.completed.toFloat() / progress.total.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            text =
                stringResource(
                    Res.string.neutral_fddb_import_summary,
                    progress.imported,
                    progress.skipped,
                    progress.failed,
                ),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun String.toFddbQueueLabel(): String =
    substringAfter("/lebensmittel/", missingDelimiterValue = this)
        .substringAfter("/food/", missingDelimiterValue = this)
        .removeSuffix("/index.html")
        .replace('_', ' ')

@Composable
private fun ImportResultItem(result: FddbImportResult) {
    ListItem(
        leadingContent = {
            when (result) {
                is FddbImportResult.Imported -> Icon(Icons.Filled.Check, null)
                is FddbImportResult.Skipped -> Icon(Icons.Outlined.Info, null)
                is FddbImportResult.Failed -> Icon(Icons.Outlined.ErrorOutline, null)
            }
        },
        headlineContent = {
            Text(
                text =
                    when (result) {
                        is FddbImportResult.Imported -> result.name
                        is FddbImportResult.Skipped -> result.name
                        is FddbImportResult.Failed ->
                            stringResource(Res.string.headline_import_failed)
                    },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text =
                    when (result) {
                        is FddbImportResult.Imported -> stringResource(Res.string.headline_imported)
                        is FddbImportResult.Skipped ->
                            when (result.reason) {
                                FddbSkipReason.BarcodeExists ->
                                    stringResource(Res.string.description_fddb_skip_barcode)
                                FddbSkipReason.ProductExists ->
                                    stringResource(Res.string.description_fddb_skip_product)
                                FddbSkipReason.UpdatedWeights ->
                                    stringResource(Res.string.description_fddb_updated_weights)
                            }
                        is FddbImportResult.Failed ->
                            result.message ?: stringResource(Res.string.error_unknown_error)
                    },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

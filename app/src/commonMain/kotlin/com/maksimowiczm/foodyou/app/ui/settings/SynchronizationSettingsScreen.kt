package com.maksimowiczm.foodyou.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.app.ui.home.master.FddbLoginDialog
import com.maksimowiczm.foodyou.common.compose.utility.LocalClipboardManager
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun SynchronizationSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SynchronizationSettingsViewModel = koinViewModel(),
) {
    val model = viewModel.model.collectAsStateWithLifecycle().value
    var showFddbLoginDialog by remember { mutableStateOf(false) }

    SynchronizationSettingsContent(
        model = model,
        onBack = onBack,
        onHomeSyncHealthConnectEnabledChange = viewModel::setHomeSyncHealthConnectEnabled,
        onHomeSyncFddbDiaryEnabledChange = viewModel::setHomeSyncFddbDiaryEnabled,
        onFddbSync = {
            if (model?.hasFddbCredentials == true) {
                viewModel.syncFddbDiary()
            } else {
                showFddbLoginDialog = true
            }
        },
        modifier = modifier,
    )

    if (showFddbLoginDialog) {
        FddbLoginDialog(onDismissRequest = { showFddbLoginDialog = false })
    }
}

@Composable
private fun SynchronizationSettingsContent(
    model: SynchronizationSettingsModel?,
    onBack: () -> Unit,
    onHomeSyncHealthConnectEnabledChange: (Boolean) -> Unit,
    onHomeSyncFddbDiaryEnabledChange: (Boolean) -> Unit,
    onFddbSync: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val clipboardManager = LocalClipboardManager.current

    Scaffold(
        modifier = modifier,
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(Res.string.headline_synchronization)) },
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
                    headlineContent = { Text(stringResource(Res.string.headline_home_sync)) },
                    supportingContent = {
                        Text(stringResource(Res.string.neutral_home_sync_settings))
                    },
                )
            }

            item {
                val checked = model?.homeSyncHealthConnectEnabled ?: false
                ListItem(
                    headlineContent = {
                        Text(stringResource(Res.string.action_sync_health_connect))
                    },
                    trailingContent = {
                        Switch(
                            checked = checked,
                            onCheckedChange = onHomeSyncHealthConnectEnabledChange,
                        )
                    },
                    modifier =
                        Modifier.clickable {
                            onHomeSyncHealthConnectEnabledChange(!checked)
                        },
                )
            }

            item {
                val checked = model?.homeSyncFddbDiaryEnabled ?: false
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.action_sync_fddb_diary)) },
                    trailingContent = {
                        Switch(
                            checked = checked,
                            onCheckedChange = onHomeSyncFddbDiaryEnabledChange,
                        )
                    },
                    modifier =
                        Modifier.clickable {
                            onHomeSyncFddbDiaryEnabledChange(!checked)
                        },
                )
            }

            item {
                val errorMessage = model?.fddbDiarySyncStatus?.errorMessage
                ListItem(
                    headlineContent = {
                        Text(stringResource(Res.string.headline_fddb_sync_status))
                    },
                    supportingContent = {
                        Column {
                            Text(model.fddbSyncStatusText())
                            errorMessage?.let { debugText ->
                                Text(
                                    text = debugText,
                                    style =
                                        MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace
                                        ),
                                )
                                IconButton(
                                    onClick = {
                                        clipboardManager.copy(
                                            label = "FDDB sync debug",
                                            text = debugText,
                                        )
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ContentCopy,
                                        contentDescription =
                                            stringResource(Res.string.action_copy),
                                    )
                                }
                            }
                        }
                    },
                    trailingContent = {
                        FilledTonalButton(
                            onClick = onFddbSync,
                            enabled = model?.fddbSyncInProgress != true,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CloudSync,
                                contentDescription =
                                    stringResource(Res.string.action_sync_fddb_diary),
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SynchronizationSettingsModel?.fddbSyncStatusText(): String {
    if (this?.fddbSyncInProgress == true) {
        return stringResource(Res.string.neutral_fddb_sync_running)
    }
    if (this?.hasFddbCredentials == false) {
        return stringResource(Res.string.neutral_fddb_sync_not_configured)
    }

    val status = this?.fddbDiarySyncStatus
        ?: return stringResource(Res.string.neutral_fddb_sync_never_run)

    if (status.errorMessage != null) {
        return stringResource(
            Res.string.neutral_fddb_import_summary,
            status.imported,
            status.skipped,
            status.failed,
        )
    }

    return stringResource(
        Res.string.neutral_fddb_import_summary,
        status.imported,
        status.skipped,
        status.failed,
    )
}

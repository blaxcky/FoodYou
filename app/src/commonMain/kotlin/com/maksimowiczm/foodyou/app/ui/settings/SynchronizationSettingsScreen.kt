package com.maksimowiczm.foodyou.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.app.ui.home.master.FddbLoginDialog
import com.maksimowiczm.foodyou.app.ui.weight.rememberHealthConnectWeightPermissionRequester
import com.maksimowiczm.foodyou.common.compose.utility.LocalClipboardManager
import com.maksimowiczm.foodyou.common.compose.utility.LocalDateFormatter
import com.maksimowiczm.foodyou.settings.domain.entity.FddbProductSyncManualFrequency
import com.maksimowiczm.foodyou.settings.domain.entity.FddbProductSyncMode
import foodyou.app.generated.resources.*
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun SynchronizationSettingsScreen(
    onBack: () -> Unit,
    onFddbProductSyncQueue: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SynchronizationSettingsViewModel = koinViewModel(),
) {
    val model = viewModel.model.collectAsStateWithLifecycle().value
    var showFddbLoginDialog by remember { mutableStateOf(false) }
    var showFddbProductSyncPolicyDialog by remember { mutableStateOf(false) }
    val weightPermissionRequester =
        rememberHealthConnectWeightPermissionRequester { granted ->
            if (granted) viewModel.setWeightSyncEnabled(true)
        }

    SynchronizationSettingsContent(
        model = model,
        onBack = onBack,
        onHomeSyncHealthConnectEnabledChange = viewModel::setHomeSyncHealthConnectEnabled,
        onWeightSyncEnabledChange = { enabled ->
            if (enabled && model?.weightSyncAvailable == true) weightPermissionRequester.request()
            else viewModel.setWeightSyncEnabled(false)
        },
        onHomeSyncFddbDiaryEnabledChange = viewModel::setHomeSyncFddbDiaryEnabled,
        onFddbSync = {
            if (model?.hasFddbCredentials == true) {
                viewModel.syncFddbDiary()
            } else {
                showFddbLoginDialog = true
            }
        },
        onFddbProductSyncQueue = onFddbProductSyncQueue,
        onFddbProductSyncPolicy = { showFddbProductSyncPolicyDialog = true },
        modifier = modifier,
    )

    if (showFddbLoginDialog) {
        FddbLoginDialog(onDismissRequest = { showFddbLoginDialog = false })
    }
    if (showFddbProductSyncPolicyDialog && model != null) {
        FddbProductSyncPolicyDialog(
            mode = model.fddbProductSyncMode,
            frequency = model.fddbProductSyncManualFrequency,
            onDismiss = { showFddbProductSyncPolicyDialog = false },
            onSave = { mode, frequency ->
                showFddbProductSyncPolicyDialog = false
                viewModel.setFddbProductSyncPolicy(mode, frequency)
            },
        )
    }
}

@Composable
private fun SynchronizationSettingsContent(
    model: SynchronizationSettingsModel?,
    onBack: () -> Unit,
    onHomeSyncHealthConnectEnabledChange: (Boolean) -> Unit,
    onWeightSyncEnabledChange: (Boolean) -> Unit,
    onHomeSyncFddbDiaryEnabledChange: (Boolean) -> Unit,
    onFddbSync: () -> Unit,
    onFddbProductSyncQueue: () -> Unit,
    onFddbProductSyncPolicy: () -> Unit,
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
                    headlineContent = {
                        Text(stringResource(Res.string.headline_fddb_product_sync_settings))
                    },
                    supportingContent = { Text(model.fddbProductSyncPolicySummary()) },
                    modifier = Modifier.clickable(onClick = onFddbProductSyncPolicy),
                )
            }

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
                    supportingContent = { Text("Schritte und Kalorien beim Home-Sync abgleichen") },
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
                val checked = model?.weightSyncEnabled ?: false
                ListItem(
                    headlineContent = { Text("Gewicht mit Health Connect synchronisieren") },
                    supportingContent = { Text("Waagenwerte übernehmen und FoodYou-Einträge übertragen") },
                    trailingContent = {
                        Switch(
                            checked = checked,
                            enabled = checked || model?.weightSyncAvailable == true,
                            onCheckedChange = onWeightSyncEnabledChange,
                        )
                    },
                    modifier = Modifier.clickable(enabled = checked || model?.weightSyncAvailable == true) {
                        onWeightSyncEnabledChange(!checked)
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
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(model.fddbSyncStatusText())
                            errorMessage?.let { debugText ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                ) {
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
                                Surface(
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    border =
                                        BorderStroke(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant,
                                        ),
                                ) {
                                    Text(
                                        text = debugText,
                                        modifier =
                                            Modifier.verticalScroll(rememberScrollState())
                                                .padding(12.dp),
                                        style =
                                            MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace
                                            ),
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
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            item {
                ListItem(
                    headlineContent = {
                        Text(stringResource(Res.string.headline_fddb_product_sync_queue))
                    },
                    supportingContent = {
                        Text(model.fddbProductSyncQueueStatus())
                    },
                    modifier = Modifier.clickable(onClick = onFddbProductSyncQueue),
                )
            }
        }
    }
}

@Composable
internal fun FddbProductSyncPolicyDialog(
    mode: FddbProductSyncMode,
    frequency: FddbProductSyncManualFrequency,
    onDismiss: () -> Unit,
    onSave: (FddbProductSyncMode, FddbProductSyncManualFrequency) -> Unit,
) {
    var selectedMode by remember(mode) { mutableStateOf(mode) }
    var selectedFrequency by remember(frequency) { mutableStateOf(frequency) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.headline_fddb_product_sync_settings)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                FddbProductSyncPolicyOption(
                    selected = selectedMode == FddbProductSyncMode.EveryThirtyMinutes,
                    headline =
                        stringResource(
                            Res.string.option_fddb_product_sync_every_thirty_minutes
                        ),
                    supporting =
                        stringResource(
                            Res.string.description_fddb_product_sync_every_thirty_minutes
                        ),
                    onClick = { selectedMode = FddbProductSyncMode.EveryThirtyMinutes },
                )
                FddbProductSyncPolicyOption(
                    selected = selectedMode == FddbProductSyncMode.WithManualFddbSync,
                    headline =
                        stringResource(
                            Res.string.option_fddb_product_sync_with_manual_sync
                        ),
                    onClick = { selectedMode = FddbProductSyncMode.WithManualFddbSync },
                )
                if (selectedMode == FddbProductSyncMode.WithManualFddbSync) {
                    FddbProductSyncPolicyOption(
                        selected =
                            selectedFrequency == FddbProductSyncManualFrequency.EverySync,
                        headline =
                            stringResource(Res.string.option_fddb_product_sync_every_sync),
                        onClick = {
                            selectedFrequency = FddbProductSyncManualFrequency.EverySync
                        },
                        modifier = Modifier.padding(start = 24.dp),
                    )
                    FddbProductSyncPolicyOption(
                        selected =
                            selectedFrequency == FddbProductSyncManualFrequency.EveryThirdSync,
                        headline =
                            stringResource(
                                Res.string.option_fddb_product_sync_every_third_sync
                            ),
                        onClick = {
                            selectedFrequency = FddbProductSyncManualFrequency.EveryThirdSync
                        },
                        modifier = Modifier.padding(start = 24.dp),
                    )
                }
                FddbProductSyncPolicyOption(
                    selected = selectedMode == FddbProductSyncMode.Disabled,
                    headline = stringResource(Res.string.option_fddb_product_sync_disabled),
                    onClick = { selectedMode = FddbProductSyncMode.Disabled },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(selectedMode, selectedFrequency) },
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text(stringResource(Res.string.action_save))
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
private fun FddbProductSyncPolicyOption(
    selected: Boolean,
    headline: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.padding(start = 12.dp, top = 10.dp)) {
            Text(headline, style = MaterialTheme.typography.bodyLarge)
            supporting?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
private fun SynchronizationSettingsModel?.fddbProductSyncQueueStatus(): String =
    when (this?.fddbProductSyncMode) {
        FddbProductSyncMode.EveryThirtyMinutes ->
            nextAutomaticFddbProductSyncAt?.let {
                stringResource(
                    Res.string.neutral_fddb_product_sync_next_at,
                    LocalDateFormatter.current.formatDateTime(
                        it.toLocalDateTime(TimeZone.currentSystemDefault())
                    ),
                )
            } ?: stringResource(Res.string.neutral_fddb_product_sync_ready)
        FddbProductSyncMode.WithManualFddbSync,
        FddbProductSyncMode.Disabled,
        null,
        -> fddbProductSyncPolicySummary()
    }

@Composable
private fun SynchronizationSettingsModel?.fddbProductSyncPolicySummary(): String =
    when (this?.fddbProductSyncMode) {
        FddbProductSyncMode.EveryThirtyMinutes ->
            stringResource(Res.string.option_fddb_product_sync_every_thirty_minutes)
        FddbProductSyncMode.WithManualFddbSync ->
            when (fddbProductSyncManualFrequency) {
                FddbProductSyncManualFrequency.EverySync ->
                    stringResource(Res.string.neutral_fddb_product_sync_manual_every_time)
                FddbProductSyncManualFrequency.EveryThirdSync ->
                    stringResource(
                        Res.string.neutral_fddb_product_sync_manual_progress,
                        fddbProductSyncManualTriggerCount,
                    )
            }
        FddbProductSyncMode.Disabled ->
            stringResource(Res.string.neutral_fddb_product_sync_disabled)
        null -> stringResource(Res.string.description_fddb_product_sync_settings)
    }

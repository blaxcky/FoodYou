package com.maksimowiczm.foodyou.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
internal fun SynchronizationSettingsContent(
    model: SynchronizationSettingsModel?,
    onBack: () -> Unit,
    onHomeSyncHealthConnectEnabledChange: (Boolean) -> Unit,
    onWeightSyncEnabledChange: (Boolean) -> Unit,
    onHomeSyncFddbDiaryEnabledChange: (Boolean) -> Unit,
    onFddbSync: () -> Unit,
    onFddbProductSyncQueue: () -> Unit,
    onFddbProductSyncPolicy: () -> Unit,
    modifier: Modifier = Modifier,
    initialErrorDetailsExpanded: Boolean = false,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

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
                SynchronizationSectionHeader(
                    title = stringResource(Res.string.headline_home_sync),
                    description = stringResource(Res.string.neutral_home_sync_settings),
                )
            }
            item {
                val checked = model?.homeSyncHealthConnectEnabled ?: false
                SynchronizationSwitchItem(
                    icon = Icons.AutoMirrored.Outlined.DirectionsWalk,
                    headline = stringResource(Res.string.headline_home_sync_steps),
                    supporting = stringResource(Res.string.description_home_sync_steps),
                    checked = checked,
                    onCheckedChange = onHomeSyncHealthConnectEnabledChange,
                )
            }
            item {
                val checked = model?.weightSyncEnabled ?: false
                SynchronizationSwitchItem(
                    icon = Icons.Outlined.MonitorWeight,
                    headline = stringResource(Res.string.headline_home_sync_weight),
                    supporting = stringResource(Res.string.description_home_sync_weight),
                    checked = checked,
                    enabled = checked || model?.weightSyncAvailable == true,
                    onCheckedChange = onWeightSyncEnabledChange,
                )
            }
            item {
                val checked = model?.homeSyncFddbDiaryEnabled ?: false
                SynchronizationSwitchItem(
                    icon = Icons.AutoMirrored.Outlined.MenuBook,
                    headline = stringResource(Res.string.headline_home_sync_fddb_diary),
                    supporting = stringResource(Res.string.description_home_sync_fddb_diary),
                    checked = checked,
                    onCheckedChange = onHomeSyncFddbDiaryEnabledChange,
                )
            }

            item { SynchronizationSectionHeader(stringResource(Res.string.headline_fddb_diary)) }
            item {
                FddbDiarySyncCard(
                    model = model,
                    onSync = onFddbSync,
                    initialErrorDetailsExpanded = initialErrorDetailsExpanded,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            item {
                SynchronizationSectionHeader(
                    stringResource(Res.string.headline_fddb_product_sync_settings)
                )
            }
            item {
                ListItem(
                    headlineContent = {
                        Text(stringResource(Res.string.headline_fddb_product_sync_automatic))
                    },
                    supportingContent = { Text(model.fddbProductSyncPolicySummary()) },
                    leadingContent = { Icon(Icons.Outlined.Schedule, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onFddbProductSyncPolicy),
                )
            }
            item {
                ListItem(
                    headlineContent = {
                        Text(stringResource(Res.string.headline_fddb_product_sync_queue))
                    },
                    supportingContent = {
                        Text(stringResource(Res.string.description_fddb_product_sync_queue_entry))
                    },
                    leadingContent = {
                        Icon(Icons.AutoMirrored.Outlined.List, contentDescription = null)
                    },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickable(onClick = onFddbProductSyncQueue),
                )
            }
        }
    }
}

@Composable
private fun SynchronizationSectionHeader(title: String, description: String? = null) {
    Column(
        modifier =
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        description?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SynchronizationSwitchItem(
    icon: ImageVector,
    headline: String,
    supporting: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    ListItem(
        headlineContent = { Text(headline) },
        supportingContent = { Text(supporting) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = {
            Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
        },
        modifier = Modifier.clickable(enabled = enabled) { onCheckedChange(!checked) },
    )
}

@Composable
private fun FddbDiarySyncCard(
    model: SynchronizationSettingsModel?,
    onSync: () -> Unit,
    initialErrorDetailsExpanded: Boolean,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current
    val inProgress = model?.fddbSyncInProgress == true
    val status = model?.fddbDiarySyncStatus.takeIf { model?.hasFddbCredentials != false }
    var errorDetailsExpanded by rememberSaveable {
        mutableStateOf(initialErrorDetailsExpanded)
    }

    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when {
                inProgress -> {
                    Text(
                        text = stringResource(Res.string.neutral_fddb_sync_running),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                model?.hasFddbCredentials == false ->
                    Text(
                        text = stringResource(Res.string.neutral_fddb_sync_not_configured),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                status == null ->
                    Text(
                        text = stringResource(Res.string.neutral_fddb_sync_never_run),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                else -> {
                    status.attemptEpochSeconds?.let { seconds ->
                        Text(
                            text =
                                stringResource(
                                    Res.string.neutral_fddb_last_sync,
                                    LocalDateFormatter.current.formatDateTime(
                                        Instant.fromEpochSeconds(seconds)
                                            .toLocalDateTime(TimeZone.currentSystemDefault())
                                    ),
                                ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        SyncStat(
                            value = status.imported,
                            label = stringResource(Res.string.label_fddb_sync_stat_imported),
                            modifier = Modifier.weight(1f),
                        )
                        SyncStat(
                            value = status.skipped,
                            label = stringResource(Res.string.label_fddb_sync_stat_skipped),
                            modifier = Modifier.weight(1f),
                        )
                        SyncStat(
                            value = status.failed,
                            label = stringResource(Res.string.label_fddb_sync_stat_failed),
                            isError = status.failed > 0,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            FilledTonalButton(
                onClick = onSync,
                enabled = !inProgress,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Filled.CloudSync,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(Res.string.action_sync_now))
            }

            status?.errorMessage?.let { debugText ->
                HorizontalDivider()
                Row(
                    modifier =
                        Modifier.fillMaxWidth().clickable {
                            errorDetailsExpanded = !errorDetailsExpanded
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(Res.string.action_show_error_details),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                    IconButton(
                        onClick = {
                            clipboardManager.copy(label = "FDDB sync debug", text = debugText)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = stringResource(Res.string.action_copy),
                        )
                    }
                    Icon(
                        imageVector =
                            if (errorDetailsExpanded) Icons.Outlined.ExpandLess
                            else Icons.Outlined.ExpandMore,
                        contentDescription = null,
                    )
                }
                if (errorDetailsExpanded) {
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
                            modifier = Modifier.verticalScroll(rememberScrollState()).padding(12.dp),
                            style =
                                MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                        )
                    }
                }
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
private fun SynchronizationSettingsModel?.fddbProductSyncPolicySummary(): String =
    when (this?.fddbProductSyncMode) {
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

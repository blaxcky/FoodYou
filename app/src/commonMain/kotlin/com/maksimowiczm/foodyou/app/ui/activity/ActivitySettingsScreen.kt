package com.maksimowiczm.foodyou.app.ui.activity

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.app.ui.common.component.SettingsListItem
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ActivitySettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ActivitySettingsViewModel = koinViewModel()
    val model = viewModel.model.collectAsStateWithLifecycle().value
    val permissionRequester =
        rememberHealthConnectStepsPermissionRequester(viewModel::onHealthConnectPermissionResult)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    LifecycleResumeEffect(Unit) {
        viewModel.refreshHealthConnectStatus()
        onPauseOrDispose {}
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Activities") },
                navigationIcon = { ArrowBackIconButton(onBack) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(padding)
                    .padding(vertical = 8.dp)
        ) {
            SettingsListItem(
                label = { Text("Health Connect steps") },
                supportingContent = {
                    Text(model.healthConnectSupportingText())
                },
                trailingContent = {
                    Switch(
                        checked = model?.healthConnectEnabled == true,
                        onCheckedChange = {
                            viewModel.setHealthConnectEnabled(
                                enabled = it,
                                requestPermission = permissionRequester::request,
                            )
                        },
                    )
                },
                onClick = {
                    viewModel.setHealthConnectEnabled(
                        enabled = model?.healthConnectEnabled != true,
                        requestPermission = permissionRequester::request,
                    )
                },
            )
            OutlinedTextField(
                value = model?.kcalPerStep ?: "",
                onValueChange = viewModel::setKcalPerStep,
                label = { Text("kcal per step") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.padding(16.dp),
            )
            Text(
                text = model?.lastSyncedEpochSeconds?.let { "Last sync: $it" } ?: "Not synced yet",
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

private fun ActivitySettingsModel?.healthConnectSupportingText(): String =
    when (this?.healthConnectStatus) {
        null,
        ActivityHealthConnectStatus.Checking -> "Checking Health Connect"
        ActivityHealthConnectStatus.Available -> "Uses Health Connect when available and permitted"
        ActivityHealthConnectStatus.Unavailable -> "Health Connect is not available on this device"
        ActivityHealthConnectStatus.UpdateRequired -> "Health Connect needs to be installed or updated"
        ActivityHealthConnectStatus.PermissionMissing -> "Steps permission is required"
        ActivityHealthConnectStatus.PermissionDenied -> "Steps permission was denied"
        ActivityHealthConnectStatus.SyncFailed -> "Step sync failed"
        ActivityHealthConnectStatus.Synced ->
            lastSyncedEpochSeconds?.let { "Last sync: $it" } ?: "Steps synced"
    }

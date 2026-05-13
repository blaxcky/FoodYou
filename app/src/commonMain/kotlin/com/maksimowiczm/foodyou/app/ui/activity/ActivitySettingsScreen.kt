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
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.app.ui.common.component.SettingsListItem
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ActivitySettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ActivitySettingsViewModel = koinViewModel(),
) {
    val model = viewModel.model.collectAsStateWithLifecycle().value
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
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
                supportingContent = { Text("Uses Health Connect when available and permitted") },
                trailingContent = {
                    Switch(
                        checked = model?.healthConnectEnabled == true,
                        onCheckedChange = viewModel::setHealthConnectEnabled,
                    )
                },
                onClick = { viewModel.setHealthConnectEnabled(model?.healthConnectEnabled != true) },
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

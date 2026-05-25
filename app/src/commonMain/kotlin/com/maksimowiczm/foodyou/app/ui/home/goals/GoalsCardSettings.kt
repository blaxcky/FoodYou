package com.maksimowiczm.foodyou.app.ui.home.goals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import foodyou.app.generated.resources.*
import org.koin.compose.viewmodel.koinViewModel
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun GoalsCardSettings(
    onBack: () -> Unit,
    onGoalsSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GoalsCardSettingsViewModel = koinViewModel(),
) {
    val model = viewModel.model.collectAsStateWithLifecycle().value

    GoalsCardSettingsContent(
        model = model,
        onBack = onBack,
        onGoalsSettings = onGoalsSettings,
        onDietEnergyDeficitKcalChange = viewModel::setDietEnergyDeficitKcal,
        onHomeSyncHealthConnectEnabledChange = viewModel::setHomeSyncHealthConnectEnabled,
        onHomeSyncFddbDiaryEnabledChange = viewModel::setHomeSyncFddbDiaryEnabled,
        modifier = modifier,
    )
}

@Composable
private fun GoalsCardSettingsContent(
    model: GoalsCardSettingsModel,
    onBack: () -> Unit,
    onGoalsSettings: () -> Unit,
    onDietEnergyDeficitKcalChange: (String) -> Unit,
    onHomeSyncHealthConnectEnabledChange: (Boolean) -> Unit,
    onHomeSyncFddbDiaryEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier,
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(Res.string.headline_daily_goals)) },
                navigationIcon = { ArrowBackIconButton(onBack) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = paddingValues,
        ) {
            stickyHeader {
                GoalsCard(
                    energy = 1600,
                    burnedEnergy = 250,
                    netEnergy = 1350,
                    energyGoal = 2000,
                    proteins = 50,
                    proteinsGoal = 75,
                    carbohydrates = 200,
                    carbohydratesGoal = 300,
                    fats = 70,
                    fatsGoal = 90,
                    onClick = {},
                    onLongClick = {},
                    modifier = Modifier.padding(16.dp),
                )
            }

            item { HorizontalDivider() }

            item {
                ListItem(
                    headlineContent = {
                        Text(stringResource(Res.string.headline_daily_goals_settings))
                    },
                    modifier = Modifier.clickable { onGoalsSettings() },
                )
            }

            item {
                OutlinedTextField(
                    value = model.dietEnergyDeficitKcal,
                    onValueChange = onDietEnergyDeficitKcalChange,
                    label = { Text(stringResource(Res.string.label_daily_calorie_deficit)) },
                    supportingText = {
                        Text(stringResource(Res.string.neutral_daily_calorie_deficit))
                    },
                    suffix = { Text(stringResource(Res.string.unit_kcal)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }

            item { HorizontalDivider() }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.headline_home_sync)) },
                    supportingContent = {
                        Text(stringResource(Res.string.neutral_home_sync_settings))
                    },
                )
            }

            item {
                ListItem(
                    headlineContent = {
                        Text(stringResource(Res.string.action_sync_health_connect))
                    },
                    trailingContent = {
                        Switch(
                            checked = model.homeSyncHealthConnectEnabled,
                            onCheckedChange = onHomeSyncHealthConnectEnabledChange,
                        )
                    },
                    modifier =
                        Modifier.clickable {
                            onHomeSyncHealthConnectEnabledChange(
                                !model.homeSyncHealthConnectEnabled
                            )
                        },
                )
            }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.action_sync_fddb_diary)) },
                    trailingContent = {
                        Switch(
                            checked = model.homeSyncFddbDiaryEnabled,
                            onCheckedChange = onHomeSyncFddbDiaryEnabledChange,
                        )
                    },
                    modifier =
                        Modifier.clickable {
                            onHomeSyncFddbDiaryEnabledChange(!model.homeSyncFddbDiaryEnabled)
                        },
                )
            }

            item {
                ListItem(
                    headlineContent = {
                        Text(stringResource(Res.string.headline_fddb_sync_status))
                    },
                    supportingContent = { Text(model.fddbSyncStatusText()) },
                )
            }
        }
    }
}

@Composable
private fun GoalsCardSettingsModel.fddbSyncStatusText(): String {
    val status = fddbDiarySyncStatus
        ?: return stringResource(Res.string.neutral_fddb_sync_never_run)

    status.errorMessage?.let { message ->
        return stringResource(Res.string.neutral_fddb_sync_failed_with_message, message)
    }

    return stringResource(
        Res.string.neutral_fddb_import_summary,
        status.imported,
        status.skipped,
        status.failed,
    )
}

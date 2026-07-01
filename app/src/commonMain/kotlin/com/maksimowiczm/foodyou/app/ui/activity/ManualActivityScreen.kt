package com.maksimowiczm.foodyou.app.ui.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import foodyou.app.generated.resources.*
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ManualActivityScreen(
    date: LocalDate,
    id: Long?,
    onBack: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ManualActivityViewModel = koinViewModel()
    LaunchedEffect(id) {
        if (id != null) viewModel.load(id)
    }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showPresetMenu by rememberSaveable { mutableStateOf(false) }
    val name = viewModel.name.collectAsStateWithLifecycle().value
    val energyKcal = viewModel.energyKcal.collectAsStateWithLifecycle().value
    val preset = viewModel.preset.collectAsStateWithLifecycle().value
    val discountPercent = viewModel.discountPercent.collectAsStateWithLifecycle().value
    val calculatedEnergyKcal =
        viewModel.calculatedEnergyKcal.collectAsStateWithLifecycle(null).value
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    if (showDeleteDialog && id != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.delete(id, onSave)
                    }
                ) {
                    Text(stringResource(Res.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
            icon = { Icon(imageVector = Icons.Default.Delete, contentDescription = null) },
            title = { Text(stringResource(Res.string.action_delete_entry)) },
            text = { Text(stringResource(Res.string.description_delete_activity_entry)) },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (id == null) Res.string.action_add_activity else Res.string.action_edit_activity
                        )
                    )
                },
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
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (id == null) {
                ExposedDropdownMenuBox(
                    expanded = showPresetMenu,
                    onExpandedChange = { showPresetMenu = it },
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = viewModel::setName,
                        modifier =
                            Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                        label = { Text(stringResource(Res.string.product_name)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = showPresetMenu)
                        },
                    )
                    ExposedDropdownMenu(
                        expanded = showPresetMenu,
                        onDismissRequest = { showPresetMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(ManualActivityPreset.Crosstrainer.activityName) },
                            onClick = {
                                showPresetMenu = false
                                viewModel.selectPreset(ManualActivityPreset.Crosstrainer)
                            },
                        )
                    }
                }
            } else {
                OutlinedTextField(
                    value = name,
                    onValueChange = viewModel::setName,
                    label = { Text(stringResource(Res.string.product_name)) },
                )
            }
            OutlinedTextField(
                value = energyKcal,
                onValueChange = viewModel::setEnergyKcal,
                label = { Text(stringResource(Res.string.label_burned_kcal)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            if (id == null && preset == ManualActivityPreset.Crosstrainer) {
                OutlinedTextField(
                    value = discountPercent,
                    onValueChange = viewModel::setDiscountPercent,
                    label = { Text(stringResource(Res.string.label_discount_percent)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = calculatedEnergyKcal.orEmpty(),
                    onValueChange = {},
                    label = { Text(stringResource(Res.string.label_calculated_calories)) },
                    readOnly = true,
                )
            }
            Button(onClick = { viewModel.save(date, id, onSave) }) {
                Text(stringResource(Res.string.action_save))
            }
            if (id != null) {
                TextButton(onClick = { showDeleteDialog = true }) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                    Text(stringResource(Res.string.action_delete))
                }
            }
        }
    }
}

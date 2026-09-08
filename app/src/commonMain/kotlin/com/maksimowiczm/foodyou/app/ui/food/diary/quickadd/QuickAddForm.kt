package com.maksimowiczm.foodyou.app.ui.food.diary.quickadd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.maksimowiczm.foodyou.app.ui.common.form.FormField
import com.maksimowiczm.foodyou.app.ui.common.utility.LocalEnergyFormatter
import com.maksimowiczm.foodyou.app.ui.common.utility.LocalNutrientsOrder
import com.maksimowiczm.foodyou.common.compose.component.unorderedList
import com.maksimowiczm.foodyou.settings.domain.entity.NutrientsOrder
import foodyou.app.generated.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun QuickAddForm(state: QuickAddFormState, modifier: Modifier = Modifier) {
    val energyFormatter = LocalEnergyFormatter.current
    val coroutineScope = rememberCoroutineScope()
    var showCsvHelp by remember { mutableStateOf(false) }
    var showEnergyHelp by remember { mutableStateOf(false) }

    if (showCsvHelp) {
        QuickAddHelpDialog(
            title = stringResource(Res.string.headline_quick_add_csv_help),
            onDismissRequest = { showCsvHelp = false },
        ) {
            Text(stringResource(Res.string.description_quick_add_csv_help))
            Text(stringResource(Res.string.example_quick_add_csv))
        }
    }
    if (showEnergyHelp) {
        QuickAddHelpDialog(
            title = stringResource(Res.string.headline_quick_add_energy_help),
            onDismissRequest = { showEnergyHelp = false },
        ) {
            Text(stringResource(Res.string.description_quick_add_energy_help))
            Text(
                unorderedList(
                    stringResource(
                        Res.string.x_energy_unit_per_g,
                        stringResource(Res.string.nutriment_proteins),
                        energyFormatter.proteinsEnergyDensity,
                        energyFormatter.suffix(),
                    ),
                    stringResource(
                        Res.string.x_energy_unit_per_g,
                        stringResource(Res.string.nutriment_carbohydrates),
                        energyFormatter.carbohydratesEnergyDensity,
                        energyFormatter.suffix(),
                    ),
                    stringResource(
                        Res.string.x_energy_unit_per_g,
                        stringResource(Res.string.nutriment_fats),
                        energyFormatter.fatsEnergyDensity,
                        energyFormatter.suffix(),
                    ),
                )
            )
        }
    }

    Column(modifier = modifier) {
        OutlinedTextField(
            state = state.name.textFieldState,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(Res.string.product_name)) },
            supportingText = { Text(stringResource(Res.string.neutral_required)) },
            isError = state.name.error != null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )

        OutlinedTextField(
            state = state.energy.textFieldState,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(Res.string.unit_energy)) },
            supportingText = {
                val error = state.energy.error
                if (error != null) {
                    Text(error.stringResource())
                }
            },
            suffix = { Text(energyFormatter.suffix()) },
            placeholder = { Text("0") },
            trailingIcon = {
                TooltipBox(
                    positionProvider =
                        TooltipDefaults.rememberTooltipPositionProvider(
                            TooltipAnchorPosition.Above
                        ),
                    tooltip = {
                        PlainTooltip {
                            Text(
                                text =
                                    if (state.autoCalculateEnergy) {
                                        stringResource(Res.string.headline_auto_calculate_energy)
                                    } else {
                                        stringResource(Res.string.headline_manual_energy_input)
                                    },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    },
                    state = rememberTooltipState(isPersistent = true),
                ) {
                    IconButton(
                        onClick = { state.autoCalculateEnergy = !state.autoCalculateEnergy }
                    ) {
                        if (state.autoCalculateEnergy) {
                            Icon(
                                imageVector = Icons.Outlined.Calculate,
                                contentDescription = stringResource(Res.string.headline_manual_energy_input),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Keyboard,
                                contentDescription = stringResource(Res.string.headline_auto_calculate_energy),
                            )
                        }
                    }
                }
            },
            isError = state.energy.error != null,
            keyboardOptions =
                KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    if (state.autoCalculateEnergy) Res.string.status_quick_add_auto_energy
                    else Res.string.status_quick_add_manual_energy
                ),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
            )
            IconButton(onClick = { showEnergyHelp = true }) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = stringResource(Res.string.action_quick_add_energy_help),
                )
            }
        }

        OutlinedTextField(
            state = state.csvTextFieldState,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(Res.string.headline_quick_add_csv)) },
            supportingText = {
                val error = state.csvError
                if (error != null) {
                    Text(error.stringResource())
                }
            },
            trailingIcon = {
                IconButton(onClick = { showCsvHelp = true }) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = stringResource(Res.string.action_quick_add_csv_help),
                    )
                }
            },
            isError = state.csvError != null,
            lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 2, maxHeightInLines = 6),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            FilledTonalButton(onClick = { coroutineScope.launch { state.applyCsv() } }) {
                Icon(imageVector = Icons.Outlined.Check, contentDescription = null)
                Text(stringResource(Res.string.action_apply))
            }
        }

        Spacer(Modifier.height(16.dp))

        LocalNutrientsOrder.current.forEach {
            when (it) {
                NutrientsOrder.Proteins ->
                    state.proteins.TextField(
                        label = stringResource(Res.string.nutriment_proteins),
                        modifier = Modifier.fillMaxWidth(),
                    )
                NutrientsOrder.Fats ->
                    state.fats.TextField(
                        label = stringResource(Res.string.nutriment_fats),
                        modifier = Modifier.fillMaxWidth(),
                    )
                NutrientsOrder.Carbohydrates ->
                    state.carbohydrates.TextField(
                        label = stringResource(Res.string.nutriment_carbohydrates),
                        modifier = Modifier.fillMaxWidth(),
                    )
                else -> Unit
            }
        }
    }
}

@Composable
private fun QuickAddHelpDialog(
    title: String,
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                content()
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(Res.string.action_quick_add_help_understood))
            }
        },
    )
}

@Composable
private fun FormField<Double?, QuickAddFormFieldError>.TextField(
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        state = textFieldState,
        modifier = modifier,
        keyboardOptions =
            KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        suffix = { Text(stringResource(Res.string.unit_gram_short)) },
        supportingText = {
            val error = error
            if (error != null) {
                Text(error.stringResource())
            }
        },
        isError = error != null,
        label = { Text(label) },
    )
}

@Composable
private fun QuickAddCsvError.stringResource(): String =
    when (this) {
        QuickAddCsvError.Empty -> stringResource(Res.string.error_quick_add_csv_empty)
        QuickAddCsvError.InvalidHeader -> stringResource(Res.string.error_quick_add_csv_header)
        QuickAddCsvError.InvalidDataRowCount ->
            stringResource(Res.string.error_quick_add_csv_data_row_count)
        QuickAddCsvError.InvalidNumber -> stringResource(Res.string.error_invalid_number)
        QuickAddCsvError.NegativeNumber -> stringResource(Res.string.error_invalid_number)
        QuickAddCsvError.EmptyName -> stringResource(Res.string.error_quick_add_csv_empty_name)
    }

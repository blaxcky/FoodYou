package com.maksimowiczm.foodyou.app.ui.food.diary.add

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.common.utility.ServingUnit
import com.maksimowiczm.foodyou.app.ui.common.utility.stringResource
import com.maksimowiczm.foodyou.app.ui.food.component.MeasurementPickerOption
import com.maksimowiczm.foodyou.app.ui.food.component.MeasurementPickerState
import com.maksimowiczm.foodyou.app.ui.food.component.PortionEditResult
import com.maksimowiczm.foodyou.app.ui.food.component.findByPortionLabel
import com.maksimowiczm.foodyou.app.ui.food.component.portionMatching
import com.maksimowiczm.foodyou.app.ui.food.component.withPortionEdit
import com.maksimowiczm.foodyou.common.compose.utility.formatClipZeros
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.measurement.MeasurementType
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.food.domain.entity.ProductPortion
import com.maksimowiczm.foodyou.food.domain.entity.normalizedLabel
import com.maksimowiczm.foodyou.settings.domain.entity.FoodEntryAmountPickerStyle
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.core.qualifier.named

private const val COLLAPSED_ROW_LIMIT = 5

@Immutable
internal data class PortionListEdit(
    val portions: List<ProductPortion>,
    val hiddenStandardTypes: Set<MeasurementType> = emptySet(),
)

/** Amount picker layout chosen in personalization settings; the new view while loading. */
@Composable
internal fun rememberFoodEntryAmountPickerStyle(): FoodEntryAmountPickerStyle {
    val settingsRepository: UserPreferencesRepository<Settings> =
        koinInject(named(Settings::class.qualifiedName!!))
    val styleFlow =
        remember(settingsRepository) {
            settingsRepository.observe().map { it.foodEntryAmountPickerStyle }
        }
    val style by styleFlow.collectAsStateWithLifecycle(null)
    return style ?: FoodEntryAmountPickerStyle.PortionList
}

/** Amount field with a live description of the selected unit, e.g. "× medium = 150 g". */
@Composable
internal fun PortionListAmountInput(
    state: MeasurementPickerState,
    servingUnit: ServingUnit,
    modifier: Modifier = Modifier,
) {
    SyncMeasurementWithInput(state)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReferenceMeasurementInput(formField = state.inputField, modifier = Modifier.width(104.dp))
        Text(
            text = state.selectionDescription(servingUnit),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Selectable list of product portions and units. Long pressing a product portion edits it when
 * [portions] and [onSavePortions] are provided.
 */
@Composable
internal fun PortionListOptions(
    state: MeasurementPickerState,
    servingUnit: ServingUnit,
    portions: List<ProductPortion>,
    onSavePortions: ((PortionListEdit) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    PreselectMatchingPortion(state)

    var pendingRename by remember {
        mutableStateOf<Pair<MeasurementPickerOption, ProductPortion>?>(null)
    }
    KeepPortionSelection(state = state, pendingRename = pendingRename)

    var editing by remember { mutableStateOf<PortionDialogTarget?>(null) }
    editing?.let { target ->
        PortionEditDialog(
            target = target,
            portions = portions,
            unit = if (state.isLiquid) ProductPortion.Unit.Milliliter else ProductPortion.Unit.Gram,
            onDismiss = { editing = null },
            onSave = { _, edited, updated ->
                if (target.option != null && edited != null) pendingRename = target.option to edited
                onSavePortions?.invoke(PortionListEdit(updated, target.standardTypes))
                editing = null
            },
        )
    }

    val sortedOptions = remember(state.options) { state.options.sortedBy { it.listOrder() } }
    val allRows =
        sortedOptions
            .map { option ->
                val label = option.rowLabel(servingUnit)
                PortionRow(
                    option = option,
                    label = label,
                    weight = option.rowWeight(state),
                    portion = option.editablePortion(label, state),
                )
            }
    val rows = allRows.withoutRepeatedStandardRows(selected = state.selectedOption)
    var expanded by rememberSaveable { mutableStateOf(false) }
    val collapsible = rows.size > COLLAPSED_ROW_LIMIT + 1
    // Grams or milliliters come first, so they always stay visible when collapsed.
    val visibleRows =
        if (!collapsible || expanded) {
            rows
        } else {
            rows.filterIndexed { index, row ->
                index < COLLAPSED_ROW_LIMIT || row.option == state.selectedOption
            }
        }
    val canEdit = onSavePortions != null

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = stringResource(Res.string.headline_portions),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (canEdit && rows.any { it.portion != null }) {
                Text(
                    text = stringResource(Res.string.description_long_press_to_edit),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Rows are inset so the label and its weight stay visually close together.
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            visibleRows.forEachIndexed { index, row ->
                if (index > 0) PortionRowDivider()
                val option = row.option
                val portion = row.portion
                PortionOptionRow(
                    label = row.label,
                    weight = row.weight,
                    selected = option == state.selectedOption,
                    onClick = { state.selectFromList(option) },
                    onLongClick =
                        if (canEdit && portion != null) {
                            {
                                editing =
                                    PortionDialogTarget(
                                        original = portion,
                                        amount = null,
                                        option = option,
                                        standardTypes =
                                            allRows.filter {
                                                it.option is MeasurementPickerOption.Standard &&
                                                    it.portion?.sameDefinitionAs(portion) == true
                                            }.map { it.option.type }.toSet(),
                                    )
                            }
                        } else {
                            null
                        },
                )
            }

            if (collapsible && !expanded) {
                PortionRowDivider()
                ActionRow(
                    icon = { Icon(Icons.Outlined.ExpandMore, contentDescription = null) },
                    text = stringResource(Res.string.action_show_all_count, rows.size),
                    onClick = { expanded = true },
                )
            }

            if (canEdit) {
                PortionRowDivider()
                ActionRow(
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = stringResource(Res.string.action_add_portion),
                    onClick = {
                        editing =
                            PortionDialogTarget(
                                original = null,
                                amount = state.currentMetricWeight()?.takeIf { it > 0.0 },
                            )
                    },
                )
            }
        }
    }
}

@Composable
private fun PortionRowDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}

@Composable
private fun PortionOptionRow(
    label: String,
    weight: String?,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val color =
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = 48.dp)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.size(40.dp))
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = color,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (weight != null) {
            Text(
                text = weight,
                modifier = Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActionRow(icon: @Composable () -> Unit, text: String, onClick: () -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = 48.dp)
                .combinedClickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
                icon()
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Immutable
private data class PortionDialogTarget(
    val original: ProductPortion?,
    val amount: Double?,
    val option: MeasurementPickerOption? = null,
    val standardTypes: Set<MeasurementType> = emptySet(),
)

@Composable
private fun PortionEditDialog(
    target: PortionDialogTarget,
    portions: List<ProductPortion>,
    unit: ProductPortion.Unit,
    onDismiss: () -> Unit,
    onSave: (
        original: ProductPortion?,
        edited: ProductPortion?,
        updated: List<ProductPortion>,
    ) -> Unit,
) {
    val original = target.original
    // Reference rows need not be present in the user-managed portion list.
    val listOriginal = original?.let { value -> portions.firstOrNull { it.sameDefinitionAs(value) } }
    val name = rememberTextFieldState(original?.label.orEmpty())
    val amount =
        rememberTextFieldState((original?.amount ?: target.amount)?.formatClipZeros().orEmpty())
    var error by remember { mutableStateOf<PortionEditResult?>(null) }
    LaunchedEffect(name.text, amount.text) { error = null }
    val portionUnit = original?.unit ?: unit

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (original == null) Res.string.action_add_portion
                    else Res.string.headline_edit_portion
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    state = name,
                    label = { Text(stringResource(Res.string.product_name)) },
                    isError =
                        error == PortionEditResult.EmptyName ||
                            error == PortionEditResult.DuplicateName,
                    supportingText =
                        when (error) {
                            PortionEditResult.EmptyName -> {
                                { Text(stringResource(Res.string.error_portion_name_empty)) }
                            }
                            PortionEditResult.DuplicateName -> {
                                { Text(stringResource(Res.string.error_portion_name_duplicate)) }
                            }
                            else -> null
                        },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    state = amount,
                    label = { Text(stringResource(Res.string.headline_amount)) },
                    suffix = { Text(portionUnit.shortLabel()) },
                    isError = error == PortionEditResult.InvalidAmount,
                    supportingText =
                        if (error == PortionEditResult.InvalidAmount) {
                            { Text(stringResource(Res.string.error_portion_amount_invalid)) }
                        } else {
                            null
                        },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val edited =
                        ProductPortion(
                            label = name.text.toString(),
                            amount =
                                amount.text.toString().replace(',', '.').toDoubleOrNull()
                                    ?: Double.NaN,
                            unit = portionUnit,
                        )
                    when (val result = portions.withPortionEdit(listOriginal, edited)) {
                        is PortionEditResult.Success ->
                            onSave(original, edited.copy(label = edited.label.trim()), result.portions)
                        else -> error = result
                    }
                }
            ) {
                Text(stringResource(Res.string.action_save))
            }
        },
        dismissButton = {
            Row {
                if (original != null) {
                    TextButton(
                        onClick = {
                            val result = portions.withPortionEdit(listOriginal, null)
                            if (result is PortionEditResult.Success) {
                                onSave(original, null, result.portions)
                            }
                        }
                    ) {
                        Text(
                            text = stringResource(Res.string.action_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
            }
        },
    )
}

/** Selects the portion that matches the prefilled amount exactly, once per screen. */
@Composable
private fun PreselectMatchingPortion(state: MeasurementPickerState) {
    var done by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state) {
        if (done) return@LaunchedEffect
        done = true
        if (state.selectedOption !is MeasurementPickerOption.Standard) return@LaunchedEffect
        val portion = state.portionOptions.portionMatching(state.measurement) ?: return@LaunchedEffect
        if (portion in state.options) {
            state.selectOption(option = portion, inputTextOverride = "1")
        }
    }
}

/**
 * Re-selects an edited portion after the product portions changed, or falls back to the base unit
 * when the selected portion was deleted.
 */
@Composable
private fun KeepPortionSelection(
    state: MeasurementPickerState,
    pendingRename: Pair<MeasurementPickerOption, ProductPortion>?,
) {
    val latestRename by rememberUpdatedState(pendingRename)
    LaunchedEffect(state.options) {
        val selected = state.selectedOption
        if (selected in state.options) return@LaunchedEffect
        val selectedPortion = (selected as? MeasurementPickerOption.Portion)?.portion

        val rename = latestRename
        val target =
            when {
                rename != null && rename.first == selected ->
                    state.portionOptions.findByPortionLabel(rename.second)
                selectedPortion == null -> null
                else -> state.portionOptions.findByPortionLabel(selectedPortion)
            }

        if (target != null) {
            state.selectOption(
                option = target,
                inputTextOverride = state.inputField.textFieldState.text.toString(),
            )
        } else {
            state.selectOption(
                state.standardOption(
                    if (state.isLiquid) MeasurementType.Milliliter else MeasurementType.Gram
                )
            )
        }
    }
}

private fun MeasurementPickerState.selectFromList(option: MeasurementPickerOption) {
    if (option == selectedOption) return
    val selectsSingleUnit =
        option is MeasurementPickerOption.Portion ||
            option.type == MeasurementType.Package ||
            option.type == MeasurementType.Serving
    selectOption(option = option, inputTextOverride = if (selectsSingleUnit) "1" else null)
}

@Immutable
private data class PortionRow(
    val option: MeasurementPickerOption,
    val label: String,
    val weight: String?,
    val portion: ProductPortion?,
)

private fun ProductPortion.sameDefinitionAs(other: ProductPortion): Boolean =
    normalizedLabel() == other.normalizedLabel() && amount == other.amount && unit == other.unit

private fun MeasurementPickerOption.editablePortion(
    label: String,
    state: MeasurementPickerState,
): ProductPortion? =
    when (this) {
        is MeasurementPickerOption.Portion -> portion
        is MeasurementPickerOption.Standard -> {
            val amount =
                when (type) {
                    MeasurementType.Package -> state.totalWeight
                    MeasurementType.Serving -> state.servingWeight
                    else -> null
                }
            amount?.takeIf { it.isFinite() && it > 0.0 }?.let {
                ProductPortion(
                    label = label,
                    amount = it,
                    unit = if (state.isLiquid) ProductPortion.Unit.Milliliter else ProductPortion.Unit.Gram,
                )
            }
        }
    }

/**
 * Hides a package or serving row when a product portion repeats it with the same label and weight,
 * e.g. an imported "Packung" portion. A selected row stays visible.
 */
private fun List<PortionRow>.withoutRepeatedStandardRows(
    selected: MeasurementPickerOption
): List<PortionRow> {
    val portionRows = filter { it.option is MeasurementPickerOption.Portion }
    return filterNot { row ->
        val type = row.option.type
        row.option is MeasurementPickerOption.Standard &&
            (type == MeasurementType.Package || type == MeasurementType.Serving) &&
            row.option != selected &&
            row.weight != null &&
            portionRows.any { portionRow ->
                portionRow.weight == row.weight &&
                    portionRow.label.trim().equals(row.label.trim(), ignoreCase = true)
            }
    }
}

private fun MeasurementPickerOption.listOrder(): Int =
    when {
        this is MeasurementPickerOption.Standard &&
            (type == MeasurementType.Gram || type == MeasurementType.Milliliter) -> 0
        this is MeasurementPickerOption.Portion -> 1
        type == MeasurementType.Package || type == MeasurementType.Serving -> 2
        else -> 3
    }

@Composable
private fun MeasurementPickerOption.rowLabel(servingUnit: ServingUnit): String =
    when (this) {
        is MeasurementPickerOption.Portion -> portion?.label ?: displayLabel
        is MeasurementPickerOption.Standard ->
            when (type) {
                MeasurementType.Gram -> pluralStringResource(Res.plurals.unit_gram, 2)
                MeasurementType.Milliliter -> pluralStringResource(Res.plurals.unit_milliliter, 2)
                else -> type.stringResource(servingUnit)
            }
    }

@Composable
private fun MeasurementPickerOption.rowWeight(state: MeasurementPickerState): String? {
    val weight =
        when (this) {
            is MeasurementPickerOption.Portion -> unitMeasurement.metric
            is MeasurementPickerOption.Standard ->
                when (type) {
                    MeasurementType.Package -> state.totalWeight
                    MeasurementType.Serving -> state.servingWeight
                    else -> null
                }
        } ?: return null
    return "${weight.formatClipZeros()} ${state.metricUnitLabel()}"
}

@Composable
private fun MeasurementPickerState.selectionDescription(servingUnit: ServingUnit): String {
    val option = selectedOption
    val label =
        when (option) {
            is MeasurementPickerOption.Portion -> option.portion?.label ?: option.displayLabel
            is MeasurementPickerOption.Standard ->
                when (option.type) {
                    MeasurementType.Package,
                    MeasurementType.Serving -> option.type.stringResource(servingUnit)
                    else -> return option.type.stringResource(servingUnit)
                }
        }
    val weight = currentMetricWeight()
    return buildString {
        append("× ")
        append(label)
        if (weight != null) append(" = ${weight.formatClipZeros("%.1f")} ${metricUnitLabel()}")
    }
}

private fun MeasurementPickerState.currentMetricWeight(): Double? =
    when (val measurement = measurement) {
        is Measurement.ImmutableMeasurement -> measurement.metric
        is Measurement.Package -> totalWeight?.let { measurement.weight(it) }
        is Measurement.Serving -> servingWeight?.let { measurement.weight(it) }
    }

@Composable
private fun MeasurementPickerState.metricUnitLabel(): String =
    stringResource(if (isLiquid) Res.string.unit_milliliter_short else Res.string.unit_gram_short)

@Composable
private fun ProductPortion.Unit.shortLabel(): String =
    stringResource(
        when (this) {
            ProductPortion.Unit.Gram -> Res.string.unit_gram_short
            ProductPortion.Unit.Milliliter -> Res.string.unit_milliliter_short
        }
    )

package com.maksimowiczm.foodyou.app.ui.home.meals.card

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.selectAll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.maksimowiczm.foodyou.app.ui.common.theme.LocalNutrientsPalette
import com.maksimowiczm.foodyou.app.ui.common.utility.LocalEnergyFormatter
import com.maksimowiczm.foodyou.app.ui.common.utility.ServingUnit
import com.maksimowiczm.foodyou.app.ui.common.utility.stringResourceWithWeight
import com.maksimowiczm.foodyou.app.ui.food.component.MeasurementPickerOption
import com.maksimowiczm.foodyou.app.ui.food.component.measurementPickerInputForSelection
import com.maksimowiczm.foodyou.app.ui.food.component.metricWeightPerUnit
import com.maksimowiczm.foodyou.app.ui.food.component.unitName
import com.maksimowiczm.foodyou.app.ui.food.component.unitWeightLabel
import com.maksimowiczm.foodyou.app.ui.food.component.toMeasurementPickerOptions
import com.maksimowiczm.foodyou.app.ui.home.shared.FoodYouHomeCard
import com.maksimowiczm.foodyou.common.compose.utility.LocalDateFormatter
import com.maksimowiczm.foodyou.common.compose.utility.formatClipZeros
import com.maksimowiczm.foodyou.common.domain.measurement.MeasurementType
import com.maksimowiczm.foodyou.common.domain.measurement.rawValue
import com.maksimowiczm.foodyou.common.domain.measurement.type
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.fooddiary.domain.entity.MealCardMacro
import com.maksimowiczm.foodyou.fooddiary.domain.entity.MealCardMacroStyle
import foodyou.app.generated.resources.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MealCard(
    meal: MealModel,
    displayedMacros: Set<MealCardMacro> = MealCardMacro.default,
    showMacrosInFoodEntries: Boolean = true,
    macroStyle: MealCardMacroStyle = MealCardMacroStyle.default,
    showTime: Boolean = true,
    onAddFood: () -> Unit,
    onQuickAdd: () -> Unit,
    onBarcodeScan: () -> Unit,
    onEditEntry: (MealEntryModel) -> Unit,
    onEditFood: (FoodId.Product) -> Unit,
    onAddToEntry: (MealEntryModel, EntryAddition) -> Unit,
    onDeleteEntry: (MealEntryModel) -> Unit,
    selectedEntries: Set<MealEntrySelectionKey>,
    isCollapsed: Boolean,
    isSelectionMode: Boolean,
    onEnterSelection: (MealEntryModel) -> Unit,
    onToggleSelection: (MealEntryModel) -> Unit,
    onToggleCollapsed: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateFormatter = LocalDateFormatter.current
    val enDash = stringResource(Res.string.en_dash)
    val allDayString = stringResource(Res.string.headline_all_day)

    val timeString =
        remember(dateFormatter, meal, enDash, allDayString) {
            if (meal.isAllDay) {
                allDayString
            } else {
                buildString {
                    append(dateFormatter.formatTime(meal.from))
                    append(" $enDash ")
                    append(dateFormatter.formatTime(meal.to))
                }
            }
        }

    FoodYouHomeCard(
        modifier = modifier,
        color = Color.White,
        onClick = if (isSelectionMode) ({}) else onAddFood,
        onLongClick = onLongClick,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    // Keep the slot when the toggle is hidden so meal names stay aligned
                    // across cards and do not jump when selection mode starts.
                    if (meal.foods.isNotEmpty() && !isSelectionMode) {
                        IconButton(
                            onClick = onToggleCollapsed,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector =
                                    if (isCollapsed) {
                                        Icons.Default.KeyboardArrowDown
                                    } else {
                                        Icons.Default.KeyboardArrowUp
                                    },
                                contentDescription =
                                    stringResource(
                                        if (isCollapsed) {
                                            Res.string.action_expand_meal
                                        } else {
                                            Res.string.action_collapse_meal
                                        }
                                    ),
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Spacer(Modifier.size(32.dp))
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = meal.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (showTime) {
                            Text(
                                text = timeString,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.outline,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                MealNutritionSummary(
                    meal = meal,
                    displayedMacros = displayedMacros,
                    macroStyle = macroStyle,
                    modifier = Modifier.padding(top = 2.dp).widthIn(min = 130.dp),
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onQuickAdd,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Bolt,
                            contentDescription = stringResource(Res.string.headline_quick_add),
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = onBarcodeScan,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_barcode_scanner),
                            contentDescription = stringResource(Res.string.action_scan_barcode),
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = !isCollapsed,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                FoodContainer(
                    foods = meal.foods,
                    displayedMacros =
                        if (showMacrosInFoodEntries) displayedMacros else emptySet(),
                    macroStyle = macroStyle,
                    onEditEntry = onEditEntry,
                    onEditFood = onEditFood,
                    onAddToEntry = onAddToEntry,
                    onDeleteEntry = onDeleteEntry,
                    selectedEntries = selectedEntries,
                    isSelectionMode = isSelectionMode,
                    onEnterSelection = onEnterSelection,
                    onToggleSelection = onToggleSelection,
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(top = 16.dp)
                            .clip(MaterialTheme.shapes.medium),
                )
            }
        }
    }
}

@Composable
private fun MealNutritionSummary(
    meal: MealModel,
    displayedMacros: Set<MealCardMacro>,
    macroStyle: MealCardMacroStyle,
    modifier: Modifier = Modifier,
) {
    val nutrientsPalette = LocalNutrientsPalette.current
    val energyFormatter = LocalEnergyFormatter.current
    val gram = stringResource(Res.string.unit_gram_short)

    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        Text(
            text = energyFormatter.formatEnergy(meal.energy, withSuffix = true),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.End,
        )
        if (displayedMacros.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val macros =
                    listOf(
                        MacroSummary(
                            macro = MealCardMacro.Fats,
                            value = meal.fats,
                            suffix = gram,
                            color = nutrientsPalette.fatsOnSurfaceContainer,
                        ),
                        MacroSummary(
                            macro = MealCardMacro.Carbohydrates,
                            value = meal.carbohydrates,
                            suffix = gram,
                            color = nutrientsPalette.carbohydratesOnSurfaceContainer,
                        ),
                        MacroSummary(
                            macro = MealCardMacro.Proteins,
                            value = meal.proteins,
                            suffix = gram,
                            color = nutrientsPalette.proteinsOnSurfaceContainer,
                        ),
                    ).filter { it.macro in displayedMacros }

                macros.forEachIndexed { index, macro ->
                    MacroSummaryText(macro, macroStyle)
                    if (index != macros.lastIndex) {
                        Text(
                            text = ", ",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MacroSummaryText(macro: MacroSummary, style: MealCardMacroStyle) {
    MacroValueLabel(
        macro = macro.macro,
        value = "${macro.value.formatClipZeros("%.1f")}${macro.suffix}",
        style = style,
        color = macro.color,
        textStyle = MaterialTheme.typography.labelMedium,
    )
}

private data class MacroSummary(
    val macro: MealCardMacro,
    val value: Double,
    val suffix: String,
    val color: Color,
)

@Composable
private fun FoodContainer(
    foods: List<MealEntryModel>,
    displayedMacros: Set<MealCardMacro>,
    macroStyle: MealCardMacroStyle,
    onEditEntry: (MealEntryModel) -> Unit,
    onEditFood: (FoodId.Product) -> Unit,
    onAddToEntry: (MealEntryModel, EntryAddition) -> Unit,
    onDeleteEntry: (MealEntryModel) -> Unit,
    selectedEntries: Set<MealEntrySelectionKey>,
    isSelectionMode: Boolean,
    onEnterSelection: (MealEntryModel) -> Unit,
    onToggleSelection: (MealEntryModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        HorizontalDivider(Modifier.padding(horizontal = 8.dp))
        foods.forEachIndexed { i, entry ->
            val key =
                remember(entry) {
                    when (entry) {
                        is FoodMealEntryModel -> entry.id.toString()
                        is ManualMealEntryModel -> entry.id.toString()
                    }
                }

            key(key) {
                FoodContainerItem(
                    entry = entry,
                    displayedMacros = displayedMacros,
                    macroStyle = macroStyle,
                    onEditEntry = onEditEntry,
                    onEditFood = onEditFood,
                    onAddToEntry = onAddToEntry,
                    onDeleteEntry = onDeleteEntry,
                    selected = entry.selectionKey in selectedEntries,
                    isSelectionMode = isSelectionMode,
                    onEnterSelection = onEnterSelection,
                    onToggleSelection = onToggleSelection,
                    shape = foodItemShape(index = i, lastIndex = foods.lastIndex),
                )
            }
            if (i != foods.lastIndex) {
                HorizontalDivider(Modifier.padding(horizontal = 8.dp))
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = 8.dp))
    }
}

private fun foodItemShape(index: Int, lastIndex: Int): Shape {
    val topRadius = if (index == 0) 12.dp else 0.dp
    val bottomRadius = if (index == lastIndex) 12.dp else 0.dp
    return RoundedCornerShape(topRadius, topRadius, bottomRadius, bottomRadius)
}

@Composable
private fun FoodContainerItem(
    entry: MealEntryModel,
    displayedMacros: Set<MealCardMacro>,
    macroStyle: MealCardMacroStyle,
    onEditEntry: (MealEntryModel) -> Unit,
    onEditFood: (FoodId.Product) -> Unit,
    onAddToEntry: (MealEntryModel, EntryAddition) -> Unit,
    onDeleteEntry: (MealEntryModel) -> Unit,
    selected: Boolean,
    isSelectionMode: Boolean,
    onEnterSelection: (MealEntryModel) -> Unit,
    onToggleSelection: (MealEntryModel) -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    var showBottomSheet by rememberSaveable { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    if (showBottomSheet) {
        val sheetState = rememberModalBottomSheetState()

        ModalBottomSheet(onDismissRequest = { showBottomSheet = false }, sheetState = sheetState) {
            BottomSheetContent(
                entry = entry,
                displayedMacros = displayedMacros,
                macroStyle = macroStyle,
                onEdit = {
                    coroutineScope.launch {
                        onEditEntry(entry)
                        sheetState.hide()
                        showBottomSheet = false
                    }
                },
                onEditFood = { foodId ->
                    coroutineScope.launch {
                        onEditFood(foodId)
                        sheetState.hide()
                        showBottomSheet = false
                    }
                },
                onAddToEntry = { amount ->
                    coroutineScope.launch {
                        onAddToEntry(entry, amount)
                        sheetState.hide()
                        showBottomSheet = false
                    }
                },
                onDelete = {
                    coroutineScope.launch {
                        sheetState.hide()
                        onDeleteEntry(entry)
                        showBottomSheet = false
                    }
                },
            )
        }
    }

    SelectableMealFoodListItem(
        entry = entry,
        displayedMacros = displayedMacros,
        macroStyle = macroStyle,
        selected = selected,
        isSelectionMode = isSelectionMode,
        onClick = {
            if (isSelectionMode) {
                onToggleSelection(entry)
            } else {
                showBottomSheet = true
            }
        },
        onLongClick = { onEnterSelection(entry) },
        shape = shape,
        modifier = modifier,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SelectableMealFoodListItem(
    entry: MealEntryModel,
    displayedMacros: Set<MealCardMacro>,
    macroStyle: MealCardMacroStyle,
    selected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier.combinedClickable(
                onClick = onClick,
                onLongClick = if (isSelectionMode) null else onLongClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isSelectionMode) {
            Checkbox(checked = selected, onCheckedChange = { onClick() })
        }
        MealFoodListItem(
            entry = entry,
            displayedMacros = displayedMacros,
            macroStyle = macroStyle,
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = shape,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BottomSheetContent(
    entry: MealEntryModel,
    displayedMacros: Set<MealCardMacro>,
    macroStyle: MealCardMacroStyle,
    onEdit: () -> Unit,
    onEditFood: (FoodId.Product) -> Unit,
    onAddToEntry: (EntryAddition) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showAddToEntryDialog by rememberSaveable { mutableStateOf(false) }

    if (showDeleteDialog) {
        DeleteDialog(
            onDismissRequest = { showDeleteDialog = false },
            onDeleteEntry = {
                onDelete()
                showDeleteDialog = false
            },
        )
    }

    if (showAddToEntryDialog) {
        AddToEntryDialog(
            entry = entry,
            onDismissRequest = { showAddToEntryDialog = false },
            onAddToEntry = { amount ->
                onAddToEntry(amount)
                showAddToEntryDialog = false
            },
        )
    }

    Column(modifier = modifier) {
        MealFoodListItem(
            entry = entry,
            displayedMacros = displayedMacros,
            macroStyle = macroStyle,
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RectangleShape,
        )
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        ListItem(
            headlineContent = { Text(stringResource(Res.string.action_edit_entry)) },
            modifier = Modifier.clickable { onEdit() },
            leadingContent = { Icon(imageVector = Icons.Default.Edit, contentDescription = null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        val productId = (entry as? FoodMealEntryModel)?.editableProductId
        if (productId != null) {
            ListItem(
                headlineContent = { Text(stringResource(Res.string.action_edit_food)) },
                modifier = Modifier.clickable { onEditFood(productId) },
                leadingContent = {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = null)
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
        ListItem(
            headlineContent = { Text(stringResource(Res.string.action_add_to_entry)) },
            modifier = Modifier.clickable { showAddToEntryDialog = true },
            leadingContent = { Icon(imageVector = Icons.Default.Add, contentDescription = null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        ListItem(
            headlineContent = { Text(stringResource(Res.string.action_delete_entry)) },
            modifier = Modifier.clickable { showDeleteDialog = true },
            leadingContent = {
                Icon(imageVector = Icons.Default.Delete, contentDescription = null)
            },
            colors =
                ListItemDefaults.colors(
                    headlineColor = MaterialTheme.colorScheme.error,
                    leadingIconColor = MaterialTheme.colorScheme.error,
                    containerColor = Color.Transparent,
                ),
        )
    }
}

@Composable
private fun DeleteDialog(onDismissRequest: () -> Unit, onDeleteEntry: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = onDeleteEntry,
                colors =
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(Res.string.action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
        title = { Text(stringResource(Res.string.action_delete_entry)) },
        text = { Text(stringResource(Res.string.description_delete_product_entry)) },
    )
}

@Composable
internal fun AddToEntryDialog(
    entry: MealEntryModel,
    onDismissRequest: () -> Unit,
    onAddToEntry: (EntryAddition) -> Unit,
) {
    val initialAmount =
        remember(entry) {
            when (entry) {
                is FoodMealEntryModel -> entry.measurement.rawValue.formatClipZeros()
                is ManualMealEntryModel -> "1"
            }
        }
    val amountText = rememberTextFieldState(initialAmount, TextRange(0, initialAmount.length))
    val options = remember(entry) { (entry as? FoodMealEntryModel)?.additionOptions().orEmpty() }
    var selectedIndex by rememberSaveable(entry) { mutableStateOf(0) }
    val selectedOption = options.getOrNull(selectedIndex)
    val servingUnit =
        if ((entry as? FoodMealEntryModel)?.isRecipe == true) ServingUnit.Serving else ServingUnit.Piece
    val amount = remember(amountText.text) { amountText.text.toString().replace(',', '.').toDoubleOrNull() }
    val isError = amountText.text.isNotBlank() && (amount == null || !amount.isFinite() || amount <= 0.0)
    val amountFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { amountFocusRequester.requestFocus() }

    val addition =
        amount?.takeIf { it.isFinite() && it > 0.0 }?.let { value ->
            if (selectedOption != null) {
                selectedOption.measurementForInput(value)
                    .takeIf { it.rawValue.isFinite() && it.rawValue > 0.0 }
                    ?.let(EntryAddition::Food)
            } else EntryAddition.Manual(value)
        }

    val energyFormatter = LocalEnergyFormatter.current
    val currentAmount =
        (entry as? FoodMealEntryModel)?.let {
            it.measurement.stringResourceWithWeight(
                totalWeight = it.totalWeight,
                servingWeight = it.servingWeight,
                isLiquid = it.isLiquid,
                servingUnit = servingUnit,
            )
        }
    val currentEnergy = entry.energy?.let { energyFormatter.formatEnergy(it, withSuffix = true) }
    val subtitle = listOfNotNull(entry.name, currentAmount, currentEnergy).joinToString(" · ")
    val additionHint =
        amount
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?.let { value -> entry.additionHint(selectedOption, value) }
            ?.let { (weight, energy) ->
                val energyText =
                    energy?.let { "+" + energyFormatter.formatEnergy(it.roundToInt(), withSuffix = true) }
                val weightText =
                    weight?.let {
                        val unit = if ((entry as? FoodMealEntryModel)?.isLiquid == true) "ml" else "g"
                        "= ${it.formatClipZeros()} $unit"
                    }
                listOfNotNull(weightText, energyText).joinToString(" · ").ifEmpty { null }
            }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            Button(
                enabled = addition != null,
                onClick = { onAddToEntry(addition ?: return@Button) },
            ) {
                Text(stringResource(Res.string.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(Res.string.action_add_to_entry))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (selectedOption != null && options.size > 1) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.selectableGroup(),
                    ) {
                        options.forEachIndexed { index, option ->
                            AdditionUnitChip(
                                name = option.unitName(servingUnit),
                                weight = option.unitWeightLabel(),
                                selected = index == selectedIndex,
                                onClick = {
                                    if (index == selectedIndex) return@AdditionUnitChip
                                    val text =
                                        measurementPickerInputForSelection(
                                            selectedOption, option, amountText.text.toString(),
                                        )
                                    amountText.edit {
                                        replace(0, length, text)
                                        selectAll()
                                    }
                                    selectedIndex = index
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    state = amountText,
                    label = { Text(stringResource(Res.string.label_additional_amount)) },
                    suffix = selectedOption?.let { option -> { Text(option.unitName(servingUnit)) } },
                    isError = isError,
                    supportingText =
                        when {
                            isError -> {
                                { Text(stringResource(Res.string.error_invalid_number)) }
                            }
                            additionHint != null -> {
                                { Text(additionHint) }
                            }
                            else -> null
                        },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier.fillMaxWidth().focusRequester(amountFocusRequester),
                )
            }
        },
    )
}

@Composable
private fun AdditionUnitChip(
    name: String,
    weight: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            // Equal height for chips with and without a weight line, and a 48 dp touch target.
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.heightIn(min = 36.dp).padding(vertical = 6.dp),
            ) {
                Text(text = name, style = MaterialTheme.typography.labelLarge)
                if (weight != null) {
                    Text(
                        text = weight,
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = 0.8f),
                    )
                }
            }
        },
        leadingIcon =
            if (selected) {
                {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                }
            } else null,
    )
}

/**
 * The metric weight and energy that adding [value] of [option] contributes, each null when it cannot
 * be derived. The weight is null for gram and millilitre input, where it would only repeat the input.
 */
private fun MealEntryModel.additionHint(
    option: MeasurementPickerOption?,
    value: Double,
): Pair<Double?, Double?>? =
    when (this) {
        is ManualMealEntryModel -> null to energy?.let { it * value }
        is FoodMealEntryModel -> {
            val unitWeight = option?.metricWeightPerUnit?.takeIf { it.isFinite() && it > 0.0 }
            val addedWeight = unitWeight?.let { it * value }?.takeIf { it.isFinite() }
            val energyPerUnitWeight =
                if (energy != null && weight != null && weight.isFinite() && weight > 0.0) {
                    energy / weight
                } else null
            val isWeightInput =
                option is MeasurementPickerOption.Standard &&
                    (option.type == MeasurementType.Gram || option.type == MeasurementType.Milliliter)
            val shownWeight = addedWeight.takeUnless { isWeightInput }
            val addedEnergy = addedWeight?.let { w -> energyPerUnitWeight?.let { it * w } }
            if (shownWeight == null && addedEnergy == null) null else shownWeight to addedEnergy
        }
    }

internal fun FoodMealEntryModel.additionOptions(): List<MeasurementPickerOption> {
    fun valid(weight: Double?) = weight != null && weight.isFinite() && weight > 0.0
    fun standard(type: MeasurementType) =
        MeasurementPickerOption.Standard(type, totalWeight, servingWeight, isLiquid)
    return buildList {
        // Preserve the diary unit, including older entries whose product no longer exists.
        add(standard(measurement.type))
        add(standard(if (isLiquid) MeasurementType.Milliliter else MeasurementType.Gram))
        if (valid(servingWeight)) add(standard(MeasurementType.Serving))
        if (valid(totalWeight)) add(standard(MeasurementType.Package))
        addAll(portions.toMeasurementPickerOptions(isLiquid).filter { valid(it.unitMeasurement.metric) })
    }.distinct().withoutSameWeightUnits()
}

/**
 * Drops a piece or package option when a named portion has the same unit weight, since both
 * describe the same unit (e.g. "Packung (185 g)" and "1 Packung (185 g)"). The named portion wins,
 * except over the diary unit in first place, which always stays.
 */
private fun List<MeasurementPickerOption>.withoutSameWeightUnits(): List<MeasurementPickerOption> {
    fun MeasurementPickerOption.isCountUnit() =
        this is MeasurementPickerOption.Standard &&
            (type == MeasurementType.Serving || type == MeasurementType.Package)

    fun sameWeight(a: MeasurementPickerOption, b: MeasurementPickerOption): Boolean {
        val first = a.metricWeightPerUnit ?: return false
        val second = b.metricWeightPerUnit ?: return false
        return abs(first - second) <= 1e-9 * max(abs(first), abs(second))
    }

    val diaryOption = firstOrNull() ?: return this
    val portions = filterIsInstance<MeasurementPickerOption.Portion>()
    return filterIndexed { index, option ->
        when {
            index == 0 -> true
            option.isCountUnit() -> portions.none { sameWeight(it, option) }
            option is MeasurementPickerOption.Portion ->
                !(diaryOption.isCountUnit() && sameWeight(diaryOption, option))
            else -> true
        }
    }
}

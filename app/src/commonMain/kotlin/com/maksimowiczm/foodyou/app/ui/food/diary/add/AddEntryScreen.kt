package com.maksimowiczm.foodyou.app.ui.food.diary.add

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.selectAll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.app.ui.common.form.FormField
import com.maksimowiczm.foodyou.app.ui.common.utility.LocalEnergyFormatter
import com.maksimowiczm.foodyou.app.ui.common.utility.ServingUnit
import com.maksimowiczm.foodyou.app.ui.common.utility.stringResource
import com.maksimowiczm.foodyou.app.ui.common.utility.stringResourceWithWeight
import com.maksimowiczm.foodyou.app.ui.food.component.MeasurementPickerOption
import com.maksimowiczm.foodyou.app.ui.food.component.MeasurementPickerState
import com.maksimowiczm.foodyou.app.ui.food.component.toMeasurementPickerOptions
import com.maksimowiczm.foodyou.common.compose.utility.formatClipZeros
import com.maksimowiczm.foodyou.app.ui.food.diary.component.ChipsDatePicker
import com.maksimowiczm.foodyou.app.ui.food.diary.component.ChipsDatePickerState
import com.maksimowiczm.foodyou.app.ui.food.diary.component.ChipsMealPicker
import com.maksimowiczm.foodyou.app.ui.food.diary.component.ChipsMealPickerState
import com.maksimowiczm.foodyou.app.ui.food.diary.component.FoodMeasurementFormState
import com.maksimowiczm.foodyou.app.ui.food.diary.component.Source
import com.maksimowiczm.foodyou.app.ui.food.diary.component.rememberFoodMeasurementFormState
import com.maksimowiczm.foodyou.common.compose.extension.LaunchedCollectWithLifecycle
import com.maksimowiczm.foodyou.common.compose.extension.add
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.measurement.MeasurementType
import com.maksimowiczm.foodyou.common.domain.measurement.isUserSelectable
import com.maksimowiczm.foodyou.common.domain.measurement.rawValue
import com.maksimowiczm.foodyou.common.domain.measurement.type
import com.maksimowiczm.foodyou.common.extension.minus
import com.maksimowiczm.foodyou.common.extension.plus
import com.maksimowiczm.foodyou.food.domain.entity.FoodHistory
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.defaultEntryMeasurement
import foodyou.app.generated.resources.*
import kotlin.time.Duration.Companion.days
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun AddEntryScreen(
    onBack: () -> Unit,
    onEditFood: (FoodId) -> Unit,
    onEntryAdded: () -> Unit,
    onFoodDeleted: () -> Unit,
    onIngredient: (FoodId, Measurement) -> Unit,
    foodId: FoodId,
    mealId: Long,
    date: LocalDate,
    measurement: Measurement?,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
) {
    val viewModel: AddEntryViewModel = koinViewModel { parametersOf(foodId) }

    LaunchedCollectWithLifecycle(viewModel.uiEvents) {
        when (it) {
            is AddEntryEvent.FoodDeleted -> onFoodDeleted()
            is AddEntryEvent.EntryAdded -> onEntryAdded()
        }
    }

    val food = viewModel.food.collectAsStateWithLifecycle().value
    val events by viewModel.foodHistory.collectAsStateWithLifecycle()
    val meals = viewModel.meals.collectAsStateWithLifecycle().value
    val today by viewModel.today.collectAsStateWithLifecycle()
    val suggestions = viewModel.suggestions.collectAsStateWithLifecycle().value
    val possibleTypes = viewModel.possibleMeasurementTypes.collectAsStateWithLifecycle().value
    val measurementSuggestion by viewModel.suggestedMeasurement.collectAsStateWithLifecycle()

    // This is stupid that it is here but it's going to be deleted in 4.0.0
    val selectedMeasurement =
        remember(food, measurement, measurementSuggestion, possibleTypes) {
            val realMeasurement = measurement ?: measurementSuggestion
            if (realMeasurement == null) return@remember null
            if (food == null) return@remember realMeasurement
            if (possibleTypes != null && realMeasurement.type !in possibleTypes) {
                return@remember measurementSuggestion?.takeIf { it.type in possibleTypes }
            }

            if (food.weight(realMeasurement) != null) {
                realMeasurement
            } else {
                defaultEntryMeasurement(food.isLiquid)
            }
        }

    if (
        food == null ||
            meals == null ||
            suggestions == null ||
            possibleTypes == null ||
            selectedMeasurement == null
    ) {
        // TODO loading state
    } else {
        val state =
            rememberFoodMeasurementFormState(
                today = today,
                possibleDates =
                    listOf(today.minus(1.days), today, today.plus(1.days), date)
                        .distinct()
                        .sorted(),
                selectedDate = date,
                meals = meals.map { it.name },
                selectedMeal =
                    remember(meals, mealId) {
                            meals.firstOrNull { it.id == mealId } ?: meals.firstOrNull()
                        }
                        ?.name,
                suggestions = suggestions,
                portionOptions =
                    remember(food) {
                        (food as? ProductModel)
                            ?.portions
                            ?.toMeasurementPickerOptions(food.isLiquid)
                            .orEmpty()
                    },
                totalWeight = food.totalWeight,
                servingWeight = food.servingWeight,
                isLiquid = food.isLiquid,
                possibleTypes = possibleTypes,
                selectedMeasurement = selectedMeasurement,
            )

        FoodEntryForm(
            onBack = onBack,
            onSave = {
                val selectedMealId =
                    state.mealsState.selectedMeal?.let { mealName ->
                        meals.firstOrNull { it.name == mealName }?.id
                    }

                if (selectedMealId != null) {
                    viewModel.addEntry(
                        measurement = state.measurementState.measurement,
                        mealId = selectedMealId,
                        date = state.dateState.selectedDate,
                    )
                }
            },
            onUnpack = {
                val selectedMealId =
                    state.mealsState.selectedMeal?.let { mealName ->
                        meals.firstOrNull { it.name == mealName }?.id
                    }

                if (selectedMealId != null) {
                    viewModel.unpack(
                        measurement = state.measurementState.measurement,
                        mealId = selectedMealId,
                        date = state.dateState.selectedDate,
                    )
                }
            },
            onEditFood = onEditFood,
            onDelete = viewModel::deleteFood,
            onIngredient = onIngredient,
            food = food,
            history = events,
            state = state,
            animatedVisibilityScope = animatedVisibilityScope,
            modifier = modifier,
        )
    }
}

@Composable
internal fun FoodEntryForm(
    onBack: () -> Unit,
    onSave: () -> Unit,
    onUnpack: () -> Unit,
    onEditFood: (FoodId) -> Unit,
    onDelete: (() -> Unit)?,
    onIngredient: ((FoodId, Measurement) -> Unit)?,
    food: FoodModel,
    history: List<FoodHistory>,
    state: FoodMeasurementFormState,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    val cardShape = RoundedCornerShape(24.dp)

    val topBar =
        @Composable {
            TopAppBar(
                title = {},
                navigationIcon = { ArrowBackIconButton(onBack) },
                actions = {
                    Menu(onEdit = { onEditFood(food.foodId) }, onDelete = onDelete)
                },
                scrollBehavior = scrollBehavior,
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0f),
                        scrolledContainerColor =
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                    ),
            )
        }

    Scaffold(modifier = modifier, topBar = topBar) { paddingValues ->
        LazyColumn(
            modifier =
                Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f))
                    .imePadding()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding =
                paddingValues
                    .add(horizontal = 16.dp)
                    .add(top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AddEntryCard(shape = cardShape) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(contentPadding),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        Text(
                            text = food.name,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        val note = food.note
                        if (!note.isNullOrBlank()) {
                            Text(
                                text = note,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        MacroSummary(
                            food = food,
                            measurement = state.measurementState.measurement,
                        )

                        ReferenceMeasurementPicker(
                            state = state.measurementState,
                            servingUnit =
                                if (food is RecipeModel) ServingUnit.Serving else ServingUnit.Piece,
                        )

                        if (food.canUnpack) {
                            OutlinedButton(
                                onClick = {
                                    if (state.isValid) {
                                        onUnpack()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                enabled = state.isValid,
                                shape = RoundedCornerShape(28.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.CallSplit,
                                    contentDescription = null,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(Res.string.action_unpack))
                            }
                        }

                        Button(
                            onClick = {
                                if (state.isValid) {
                                    onSave()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            enabled =
                                !animatedVisibilityScope.transition.isRunning &&
                                    state.isValid &&
                                    (food !is RecipeModel || food.isValid),
                            shape = RoundedCornerShape(28.dp),
                            contentPadding = ButtonDefaults.ContentPadding,
                        ) {
                            Text(stringResource(Res.string.action_save))
                        }
                    }
                }
            }

            item {
                AddEntryCard(shape = cardShape) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(contentPadding),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ReferenceDatePicker(state = state.dateState)
                        HorizontalDivider()
                        ReferenceMealPicker(state = state.mealsState)
                    }
                }
            }

            if (food is RecipeModel) {
                item {
                    val measurement = state.measurementState.measurement
                    val ingredients = food.unpack(food.weight(measurement))

                    AddEntryCard(shape = cardShape) {
                        Ingredients(
                            ingredients = ingredients,
                            onIngredient = onIngredient,
                            contentPadding = contentPadding,
                        )
                    }
                }
            }

            item {
                AddEntryCard(shape = cardShape) {
                    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                        Text(
                            text = stringResource(Res.string.headline_macronutrients),
                            modifier = Modifier.padding(horizontal = 16.dp),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(8.dp))
                        NutrientList(
                            food = food,
                            measurement = state.measurementState.measurement,
                            onEditFood = onEditFood,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                }
            }

            val note = food.note
            if (note != null) {
                item {
                    AddEntryCard(shape = cardShape) {
                        Column(Modifier.padding(contentPadding)) {
                            Text(
                                text = stringResource(Res.string.headline_note),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(text = note, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            if (food is ProductModel) {
                item {
                    AddEntryCard(shape = cardShape) {
                        Column(Modifier.padding(contentPadding)) {
                            Text(
                                text = stringResource(Res.string.headline_source),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(8.dp))
                            Source(food.source)
                        }
                    }
                }
            }

            if (history.isNotEmpty()) {
                item {
                    AddEntryCard(shape = cardShape) {
                        FoodHistory(events = history, modifier = Modifier.padding(contentPadding))
                    }
                }
            }
        }
    }
}

@Composable
internal fun AddEntryCard(
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = { content() },
    )
}

@Composable
internal fun MacroSummary(food: FoodModel, measurement: Measurement, modifier: Modifier = Modifier) {
    val energyFormatter = LocalEnergyFormatter.current
    val weight = remember(food, measurement) { food.weight(measurement) }
    val facts = remember(food, weight) { weight?.let { food.nutritionFacts * (it / 100) } }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MacroSummaryItem(
            value =
                facts?.energy?.value?.let { energyFormatter.formatEnergy(it, "%.0f", false) }
                    ?: "-",
            label = energyFormatter.suffix(),
            modifier = Modifier.weight(1f),
        )
        MacroSummaryItem(
            value = facts?.fats?.value?.formatMacroValue() ?: "-",
            label = "Fett",
            modifier = Modifier.weight(1f),
        )
        MacroSummaryItem(
            value = facts?.carbohydrates?.value?.formatMacroValue() ?: "-",
            label = "KH",
            modifier = Modifier.weight(1f),
        )
        MacroSummaryItem(
            value = facts?.proteins?.value?.formatMacroValue() ?: "-",
            label = "Protein",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MacroSummaryItem(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.heightIn(min = 68.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

internal fun Double.formatMacroValue(): String = "${formatClipZeros("%.1f")}g"

@Composable
internal fun ReferenceMeasurementPicker(
    state: MeasurementPickerState,
    servingUnit: ServingUnit,
    modifier: Modifier = Modifier,
) {
    val latestState by rememberUpdatedState(state)
    LaunchedEffect(state.inputField.value, state.selectedOption) {
        val value = state.inputField.value ?: return@LaunchedEffect
        latestState.measurement = state.selectedOption.measurementForInput(value.toDouble())
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ReferenceMeasurementInput(
                formField = state.inputField,
                modifier = Modifier.width(104.dp),
            )
            ReferenceMeasurementTypePicker(
                selectedOption = state.selectedOption,
                options = state.options,
                servingUnit = servingUnit,
                onSelect = {
                    state.selectOption(it)
                },
                modifier = Modifier.weight(1f),
            )
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.labelSuggestions.forEach { suggestion ->
                SuggestionChip(
                    onClick = {
                        state.selectOption(
                            option = suggestion.option,
                            inputTextOverride = suggestion.inputValue.formatClipZeros(),
                        )
                    },
                    label = { Text(suggestion.label) },
                )
            }
            state.suggestions.filter { it.type.isUserSelectable }.forEach { measurement ->
                SuggestionChip(
                    onClick = {
                        state.selectOption(
                            option = state.standardOption(measurement.type),
                            inputTextOverride = measurement.rawValue.formatClipZeros(),
                        )
                    },
                    label = {
                        Text(
                            measurement.stringResourceWithWeight(
                                totalWeight = state.totalWeight,
                                servingWeight = state.servingWeight,
                                isLiquid = state.isLiquid,
                                servingUnit = servingUnit,
                            ) ?: measurement.stringResource(servingUnit)
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ReferenceMeasurementInput(
    formField: FormField<Float?, String>,
    modifier: Modifier = Modifier,
) {
    var inputFocused by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(inputFocused) {
        if (inputFocused) {
            withFrameNanos {}
            formField.textFieldState.edit { selectAll() }
        }
    }

    val borderColor by
        animateColorAsState(
            targetValue =
                if (formField.error == null) {
                    MaterialTheme.colorScheme.outline
                } else {
                    MaterialTheme.colorScheme.error
                }
        )

    Surface(
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, borderColor),
    ) {
        BasicTextField(
            state = formField.textFieldState,
            modifier =
                Modifier.fillMaxSize()
                    .onFocusChanged { focusState -> inputFocused = focusState.isFocused }
                    .padding(horizontal = 16.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = LocalTextStyle.current.merge(LocalContentColor.current),
            lineLimits = TextFieldLineLimits.SingleLine,
            cursorBrush = SolidColor(LocalContentColor.current),
            decorator = { Box(contentAlignment = Alignment.CenterStart) { it() } },
        )
    }
}

@Composable
private fun ReferenceMeasurementTypePicker(
    selectedOption: MeasurementPickerOption,
    options: List<MeasurementPickerOption>,
    servingUnit: ServingUnit,
    onSelect: (MeasurementPickerOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val contentColor = MaterialTheme.colorScheme.onSurface

    Surface(
        onClick = { expanded = true },
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = selectedOption.label(servingUnit),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                autoSize =
                    TextAutoSize.StepBased(
                        minFontSize = MaterialTheme.typography.bodySmall.fontSize,
                        maxFontSize = MaterialTheme.typography.bodyLarge.fontSize,
                    ),
                style = MaterialTheme.typography.bodyLarge,
                color = { contentColor },
            )
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(imageVector = Icons.Outlined.KeyboardArrowDown, contentDescription = null)

                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    options.forEach {
                        DropdownMenuItem(
                            text = { Text(it.label(servingUnit)) },
                            onClick = {
                                onSelect(it)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ReferenceDatePicker(state: ChipsDatePickerState, modifier: Modifier = Modifier) {
    ChipsDatePicker(state = state, modifier = modifier)
}

@Composable
internal fun ReferenceMealPicker(state: ChipsMealPickerState, modifier: Modifier = Modifier) {
    ChipsMealPicker(state = state, modifier = modifier)
}

@Composable
private fun Menu(onEdit: () -> Unit, onDelete: (() -> Unit)?, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    if (showDeleteDialog && onDelete != null) {
        DeleteDialog(onDismissRequest = { showDeleteDialog = false }, onDelete = onDelete)
    }

    Box(modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(Res.string.action_show_more),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.action_edit)) },
                onClick = {
                    expanded = false
                    onEdit()
                },
            )
            if (onDelete != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.action_delete)) },
                    onClick = {
                        expanded = false
                        showDeleteDialog = true
                    },
                )
            }
        }
    }
}

@Composable
private fun DeleteDialog(
    onDismissRequest: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onDelete) { Text(stringResource(Res.string.action_delete)) }
        },
        modifier = modifier,
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
        icon = { Icon(imageVector = Icons.Default.Delete, contentDescription = null) },
        title = { Text(stringResource(Res.string.headline_delete_food)) },
        text = { Text(stringResource(Res.string.description_delete_food)) },
    )
}

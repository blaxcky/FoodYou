package com.maksimowiczm.foodyou.app.ui.food.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.selectAll
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.maksimowiczm.foodyou.app.ui.common.form.FormField
import com.maksimowiczm.foodyou.app.ui.common.form.nullableFloatParser
import com.maksimowiczm.foodyou.app.ui.common.form.positiveFloatValidator
import com.maksimowiczm.foodyou.app.ui.common.form.rememberFormField
import com.maksimowiczm.foodyou.app.ui.common.utility.Saver
import com.maksimowiczm.foodyou.app.ui.common.utility.ServingUnit
import com.maksimowiczm.foodyou.app.ui.common.utility.stringResource
import com.maksimowiczm.foodyou.app.ui.common.utility.stringResourceWithWeight
import com.maksimowiczm.foodyou.common.compose.utility.formatClipZeros
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.measurement.MeasurementType
import com.maksimowiczm.foodyou.common.domain.measurement.from
import com.maksimowiczm.foodyou.common.domain.measurement.isUserSelectable
import com.maksimowiczm.foodyou.common.domain.measurement.rawValue
import com.maksimowiczm.foodyou.common.domain.measurement.type
import com.maksimowiczm.foodyou.food.domain.entity.FddbPortion
import com.maksimowiczm.foodyou.food.domain.entity.ProductPortion
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.painterResource

@Composable
fun MeasurementPicker(
    state: MeasurementPickerState,
    modifier: Modifier = Modifier,
    servingUnit: ServingUnit = ServingUnit.Serving,
) {
    val latestState by rememberUpdatedState(state)
    LaunchedEffect(state.inputField.value, state.selectedOption) {
        val value = state.inputField.value ?: return@LaunchedEffect
        latestState.measurement = state.selectedOption.measurementForInput(value.toDouble())
    }

    Column(modifier) {
        Row {
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(painter = painterResource(Res.drawable.ic_weight), contentDescription = null)
            }

            Spacer(Modifier.width(8.dp))

            Input(
                formField = state.inputField,
                selectedOption = state.selectedOption,
                options = state.options,
                servingUnit = servingUnit,
                onSelect = { state.selectOption(it) },
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
        }

        Spacer(Modifier.height(8.dp))

        FlowRow(
            modifier = Modifier.padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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
private fun Input(
    formField: FormField<Float?, String>,
    selectedOption: MeasurementPickerOption,
    options: List<MeasurementPickerOption>,
    servingUnit: ServingUnit,
    onSelect: (MeasurementPickerOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var inputFocused by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(inputFocused) {
        if (inputFocused) {
            withFrameNanos {}
            formField.textFieldState.edit { selectAll() }
        }
    }

    val inputColor by
        animateColorAsState(
            targetValue =
                if (formField.error == null) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                }
        )
    val contentColor by
        animateColorAsState(
            targetValue =
                if (formField.error == null) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                }
        )

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Surface(
            modifier = Modifier.height(48.dp).width(120.dp),
            color = inputColor,
            contentColor = contentColor,
            shape =
                RoundedCornerShape(
                    topStart = 16.dp,
                    bottomStart = 16.dp,
                    topEnd = 4.dp,
                    bottomEnd = 4.dp,
                ),
        ) {
            BasicTextField(
                state = formField.textFieldState,
                modifier =
                    Modifier.height(48.dp)
                        .onFocusChanged { focusState ->
                            inputFocused = focusState.isFocused
                        }
                        .padding(horizontal = 16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = LocalTextStyle.current.merge(LocalContentColor.current),
                lineLimits = TextFieldLineLimits.SingleLine,
                cursorBrush = SolidColor(LocalContentColor.current),
                decorator = { Box(contentAlignment = Alignment.CenterStart) { it() } },
            )
        }

        Surface(
            onClick = { expanded = true },
            modifier = Modifier.heightIn(min = 48.dp).weight(1f),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape =
                RoundedCornerShape(
                    topStart = 4.dp,
                    bottomStart = 4.dp,
                    topEnd = 16.dp,
                    bottomEnd = 16.dp,
                ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = selectedOption.label(servingUnit),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
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
}

@Composable
fun rememberMeasurementPickerState(
    suggestions: List<Measurement>,
    portionOptions: List<MeasurementPickerOption.Portion> = emptyList(),
    totalWeight: Double? = null,
    servingWeight: Double? = null,
    isLiquid: Boolean = false,
    possibleTypes: List<MeasurementType>,
    selectedMeasurement: Measurement,
): MeasurementPickerState {
    val inputField =
        rememberFormField(
            initialValue = selectedMeasurement.rawValue.toFloat(),
            parser = nullableFloatParser(onNotANumber = { "Invalid number format" }),
            validator =
                positiveFloatValidator(
                    onNotPositive = { "Value must be positive" },
                    onNull = { "Value cannot be empty" },
                ),
            textFieldState = rememberTextFieldState(selectedMeasurement.rawValue.formatClipZeros()),
            validateFirst = true,
        )
    val typeState = rememberSaveable { mutableStateOf(selectedMeasurement.type) }
    val selectedOptionState =
        remember {
            mutableStateOf<MeasurementPickerOption>(
                MeasurementPickerOption.Standard(
                    type = selectedMeasurement.type,
                    totalWeight = totalWeight,
                    servingWeight = servingWeight,
                    isLiquid = isLiquid,
                )
            )
        }
    val measurementState =
        rememberSaveable(selectedMeasurement, stateSaver = Measurement.Saver) {
            mutableStateOf(selectedMeasurement)
        }
    val inputMemory = remember { MeasurementPickerInputMemory() }

    LaunchedEffect(selectedMeasurement, totalWeight, servingWeight, isLiquid) {
        inputMemory.reset()
        inputField.textFieldState.setTextAndPlaceCursorAtEnd(
            selectedMeasurement.rawValue.formatClipZeros()
        )
        typeState.value = selectedMeasurement.type
        selectedOptionState.value =
            MeasurementPickerOption.Standard(
                type = selectedMeasurement.type,
                totalWeight = totalWeight,
                servingWeight = servingWeight,
                isLiquid = isLiquid,
            )
        measurementState.value = selectedMeasurement
    }

    return remember(
        suggestions,
        portionOptions,
        totalWeight,
        servingWeight,
        isLiquid,
        possibleTypes,
        inputField,
        typeState,
        selectedOptionState,
        measurementState,
        inputMemory,
    ) {
        MeasurementPickerState(
            suggestions = suggestions,
            portionOptions = portionOptions,
            totalWeight = totalWeight,
            servingWeight = servingWeight,
            isLiquid = isLiquid,
            possibleTypes = possibleTypes,
            inputField = inputField,
            measurementState = measurementState,
            typeState = typeState,
            selectedOptionState = selectedOptionState,
            inputMemory = inputMemory,
        )
    }
}

class MeasurementPickerState(
    val suggestions: List<Measurement>,
    val portionOptions: List<MeasurementPickerOption.Portion>,
    val totalWeight: Double?,
    val servingWeight: Double?,
    val isLiquid: Boolean,
    val possibleTypes: List<MeasurementType>,
    val inputField: FormField<Float?, String>,
    measurementState: MutableState<Measurement>,
    typeState: MutableState<MeasurementType>,
    selectedOptionState: MutableState<MeasurementPickerOption>,
    private val inputMemory: MeasurementPickerInputMemory,
) {
    var measurement by measurementState
    var type by typeState
        private set
    var selectedOption by selectedOptionState
        private set

    val options: List<MeasurementPickerOption> =
        possibleTypes.filter { it.isUserSelectable }.map(::standardOption) +
            portionOptions.filter { it.type in possibleTypes }

    val labelSuggestions: List<LabeledMeasurementSuggestion> =
        portionOptions.map { option ->
            LabeledMeasurementSuggestion(
                label = option.displayLabel,
                inputValue = 1.0,
                option = option,
            )
        }

    fun selectOption(option: MeasurementPickerOption, inputTextOverride: String? = null) {
        val inputText =
            inputMemory.select(
                previousOption = selectedOption,
                selectedOption = option,
                currentInput = inputField.textFieldState.text.toString(),
                inputTextOverride = inputTextOverride,
            )
        selectedOption = option
        type = option.type

        if (inputField.textFieldState.text.toString() != inputText) {
            inputField.textFieldState.setTextAndPlaceCursorAtEnd(inputText)
        }
    }

    fun standardOption(type: MeasurementType): MeasurementPickerOption.Standard =
        MeasurementPickerOption.Standard(
            type = type,
            totalWeight = totalWeight,
            servingWeight = servingWeight,
            isLiquid = isLiquid,
        )
}

class MeasurementPickerInputMemory {
    private val inputs = mutableMapOf<MeasurementPickerOptionKey, String>()

    fun select(
        previousOption: MeasurementPickerOption,
        selectedOption: MeasurementPickerOption,
        currentInput: String,
        inputTextOverride: String? = null,
    ): String {
        inputs[previousOption.memoryKey] = currentInput

        return inputTextOverride
            ?: inputs[selectedOption.memoryKey]
            ?: selectedOption.defaultInput(currentInput)
    }

    fun reset() {
        inputs.clear()
    }
}

private fun MeasurementPickerOption.defaultInput(currentInput: String): String =
    if (selectsUnitQuantity) "1" else currentInput

private val MeasurementPickerOption.memoryKey: MeasurementPickerOptionKey
    get() =
        when (this) {
            is MeasurementPickerOption.Standard -> MeasurementPickerOptionKey.Standard(type)
            is MeasurementPickerOption.Portion ->
                MeasurementPickerOptionKey.Portion(displayLabel, unitMeasurement)
        }

private sealed interface MeasurementPickerOptionKey {
    data class Standard(val type: MeasurementType) : MeasurementPickerOptionKey

    data class Portion(
        val displayLabel: String,
        val unitMeasurement: Measurement.ImmutableMeasurement,
    ) : MeasurementPickerOptionKey
}

@Immutable
data class LabeledMeasurementSuggestion(
    val label: String,
    val inputValue: Double,
    val option: MeasurementPickerOption,
)

@Immutable
sealed interface MeasurementPickerOption {
    val type: MeasurementType
    val selectsUnitQuantity: Boolean
        get() = false

    @Composable
    fun label(servingUnit: ServingUnit): String

    fun measurementForInput(value: Double): Measurement

    @Immutable
    data class Standard(
        override val type: MeasurementType,
        val totalWeight: Double? = null,
        val servingWeight: Double? = null,
        val isLiquid: Boolean = false,
    ) : MeasurementPickerOption {
        @Composable
        override fun label(servingUnit: ServingUnit): String =
            formatMeasurementPickerLabel(
                label = type.stringResource(servingUnit),
                type = type,
                totalWeight = totalWeight,
                servingWeight = servingWeight,
                isLiquid = isLiquid,
            )

        override fun measurementForInput(value: Double): Measurement = Measurement.from(type, value)
    }

    @Immutable
    data class Portion(
        val displayLabel: String,
        val unitMeasurement: Measurement.ImmutableMeasurement,
    ) :
        MeasurementPickerOption {
        override val type: MeasurementType = unitMeasurement.type

        override val selectsUnitQuantity: Boolean = true

        @Composable
        override fun label(servingUnit: ServingUnit): String = displayLabel

        override fun measurementForInput(value: Double): Measurement = unitMeasurement * value
    }
}

fun FddbPortion.toMeasurementPickerOption(isLiquid: Boolean): MeasurementPickerOption.Portion? {
    val measurement =
        when (unit) {
            ProductPortion.Unit.Gram -> {
                if (isLiquid) return null
                Measurement.Gram(amount)
            }
            ProductPortion.Unit.Milliliter -> {
                if (!isLiquid) return null
                Measurement.Milliliter(amount)
            }
        }
    return MeasurementPickerOption.Portion(
        displayLabel = "1 $label (${amount.formatClipZeros()} ${unit.label})",
        unitMeasurement = measurement,
    )
}

fun List<FddbPortion>.toMeasurementPickerOptions(
    isLiquid: Boolean
): List<MeasurementPickerOption.Portion> = mapNotNull { it.toMeasurementPickerOption(isLiquid) }

private val ProductPortion.Unit.label: String
    get() =
        when (this) {
            ProductPortion.Unit.Gram -> "g"
            ProductPortion.Unit.Milliliter -> "ml"
        }

internal fun formatMeasurementPickerLabel(
    label: String,
    type: MeasurementType,
    totalWeight: Double?,
    servingWeight: Double?,
    isLiquid: Boolean,
): String {
    val referenceWeight =
        when (type) {
            MeasurementType.Package -> totalWeight
            MeasurementType.Serving -> servingWeight
            else -> null
        } ?: return label

    val unit =
        if (isLiquid) {
            "ml"
        } else {
            "g"
        }

    return "$label (${referenceWeight.formatClipZeros()} $unit)"
}

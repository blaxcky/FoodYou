package com.maksimowiczm.foodyou.app.ui.goals.setup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationEventHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.app.ui.common.component.DiscardDialog
import com.maksimowiczm.foodyou.app.ui.common.form.FormField
import com.maksimowiczm.foodyou.app.ui.common.theme.LocalNutrientsPalette
import com.maksimowiczm.foodyou.app.ui.common.utility.LocalNutrientsOrder
import com.maksimowiczm.foodyou.common.compose.extension.LaunchedCollectWithLifecycle
import com.maksimowiczm.foodyou.common.compose.extension.add
import com.maksimowiczm.foodyou.common.compose.utility.LocalDateFormatter
import com.maksimowiczm.foodyou.common.compose.utility.formatClipZeros
import com.maksimowiczm.foodyou.common.extension.now
import com.maksimowiczm.foodyou.goals.domain.entity.BiologicalSex
import com.maksimowiczm.foodyou.goals.domain.usecase.calculateBasalMetabolicRateSuggestion
import com.maksimowiczm.foodyou.settings.domain.entity.DietEnergyDeficitOverride
import com.maksimowiczm.foodyou.settings.domain.entity.NutrientsOrder
import foodyou.app.generated.resources.*
import kotlin.math.roundToInt
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DailyGoalsScreen(onBack: () -> Unit, onSave: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: DailyGoalsViewModel = koinViewModel()
    val setupState = viewModel.state.collectAsStateWithLifecycle().value

    LaunchedCollectWithLifecycle(viewModel.events) {
        when (it) {
            DailyGoalsViewModelEvent.Updated -> onSave()
        }
    }

    if (setupState == null) {
        // TODO loading state
        return
    }

    val weeklyState = rememberWeeklyGoalsState(setupState.weeklyGoals)
    val basalMetabolicRateProfileState =
        rememberBasalMetabolicRateProfileFormState(setupState.basalMetabolicRateProfile)
    val dietEnergyDeficitState =
        rememberDietEnergyDeficitFormState(
            initialValue = setupState.dietEnergyDeficitKcal,
            initialOverride = setupState.dietEnergyDeficitOverride,
        )

    DailyGoalsContent(
        weeklyState = weeklyState,
        basalMetabolicRateProfileState = basalMetabolicRateProfileState,
        dietEnergyDeficitState = dietEnergyDeficitState,
        onBack = onBack,
        onSave = {
            viewModel.update(
                weeklyGoals = weeklyState.intoWeeklyGoals(),
                basalMetabolicRateProfile =
                    basalMetabolicRateProfileState.intoProfile(),
                dietEnergyDeficitKcal = dietEnergyDeficitState.value,
                dietEnergyDeficitOverride = dietEnergyDeficitState.overrideValue,
            )
        },
        modifier = modifier,
    )
}

@Composable
internal fun DailyGoalsContent(
    weeklyState: WeeklyGoalsState,
    basalMetabolicRateProfileState: BasalMetabolicRateProfileFormState,
    dietEnergyDeficitState: DietEnergyDeficitFormState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier,
) {
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    val isModified =
        weeklyState.isModified ||
            basalMetabolicRateProfileState.isModified ||
            dietEnergyDeficitState.isModified
    val isValid =
        weeklyState.isValid &&
            basalMetabolicRateProfileState.isValid &&
            dietEnergyDeficitState.isValid
    val handleOnBack = { if (isModified) showDiscardDialog = true else onBack() }
    NavigationEventHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = isModified,
        onBackCompleted = { showDiscardDialog = true },
    )
    if (showDiscardDialog) {
        DiscardDialog(onDismissRequest = { showDiscardDialog = false }, onDiscard = onBack) {
            Text(stringResource(Res.string.question_discard_changes))
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.headline_daily_goals)) },
                navigationIcon = { ArrowBackIconButton(handleOnBack) },
                actions = {
                    FilledIconButton(
                        onClick = onSave,
                        shapes = IconButtonDefaults.shapes(),
                        enabled = isValid,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Save,
                            contentDescription = stringResource(Res.string.action_save),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier =
                Modifier.fillMaxSize()
                    .imePadding()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = paddingValues.add(vertical = 8.dp),
        ) {
            item {
                val state = weeklyState.selectedDayGoals

                Column(modifier) {
                    BasalMetabolicRateProfileForm(
                        state = basalMetabolicRateProfileState,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                    HorizontalDivider(Modifier.padding(vertical = 16.dp))
                    DietEnergyDeficitForm(
                        state = dietEnergyDeficitState,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                    HorizontalDivider(Modifier.padding(vertical = 16.dp))
                    Text(
                        text = stringResource(Res.string.action_set_goals),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    WeightOrPercentageToggle(
                        useDistribution = state.inputType == InputType.Percentage,
                        onUseDistributionChange = {
                            state.inputType = if (it) InputType.Percentage else InputType.Weight
                        },
                        modifier =
                            Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 16.dp),
                    )
                    if (state.inputType == InputType.Percentage) {
                        MacroInputSliderForm(
                            state = state,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        )
                    } else {
                        MacroInput(
                            state = state,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        )
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    DayPicker(
                        useSeparateGoals = weeklyState.useSeparateGoals,
                        onUseSeparateGoalsChange = { weeklyState.useSeparateGoals = it },
                        selectedDay = weeklyState.selectedDay,
                        onSelectedDayChange = { weeklyState.selectedDay = it },
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    AdditionalGoalsForm(
                        state = state.additionalState,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}

@Stable
internal class DietEnergyDeficitFormState(
    val textFieldState: TextFieldState,
    private val initialInput: String,
    overrideState: TemporaryDietEnergyDeficitFormState,
) {
    private val input: String
        get() = textFieldState.text.toString()

    val override: TemporaryDietEnergyDeficitFormState = overrideState

    val parsedValue: Double? by derivedStateOf { input.parseNonNegativeDeficit() }

    val value: Double? by derivedStateOf { parsedValue?.takeIf { it > 0.0 } }

    val overrideValue: DietEnergyDeficitOverride? by derivedStateOf { override.value }

    val isValid: Boolean by derivedStateOf {
        (input.isBlank() || parsedValue != null) && override.isValid
    }

    val isModified: Boolean by derivedStateOf { input != initialInput || override.isModified }
}

@Composable
internal fun rememberDietEnergyDeficitFormState(
    initialValue: Double?,
    initialOverride: DietEnergyDeficitOverride? = null,
): DietEnergyDeficitFormState {
    val sanitizedInitialValue = initialValue?.takeIf { it > 0.0 }
    val textFieldState = rememberTextFieldState(sanitizedInitialValue?.formatClipZeros().orEmpty())
    val overrideState = rememberTemporaryDietEnergyDeficitFormState(initialOverride)

    return remember(textFieldState, sanitizedInitialValue, overrideState) {
        DietEnergyDeficitFormState(
            textFieldState = textFieldState,
            initialInput = sanitizedInitialValue?.formatClipZeros().orEmpty(),
            overrideState = overrideState,
        )
    }
}

@Stable
internal class TemporaryDietEnergyDeficitFormState(
    val textFieldState: TextFieldState,
    private val initialEnabled: Boolean,
    private val initialInput: String,
    private val initialStartEpochDay: Long,
    private val initialEndEpochDay: Long,
    enabledState: MutableState<Boolean>,
    startEpochDayState: MutableState<Long>,
    endEpochDayState: MutableState<Long>,
) {
    private val input: String
        get() = textFieldState.text.toString()

    var enabled by enabledState
    var startEpochDay by startEpochDayState
    var endEpochDay by endEpochDayState

    var startDate: LocalDate
        get() = LocalDate.fromEpochDays(startEpochDay.toInt())
        set(value) {
            startEpochDay = value.toEpochDays()
        }

    var endDate: LocalDate
        get() = LocalDate.fromEpochDays(endEpochDay.toInt())
        set(value) {
            endEpochDay = value.toEpochDays()
        }

    val parsedValue: Double? by derivedStateOf { input.parsePositiveDeficit() }

    val value: DietEnergyDeficitOverride? by derivedStateOf {
        if (!enabled) {
            null
        } else {
            parsedValue?.let {
                DietEnergyDeficitOverride(
                    energyDeficitKcal = it,
                    startDate = startDate,
                    endDate = endDate,
                )
            }
        }
    }

    val isValid: Boolean by derivedStateOf {
        !enabled || (parsedValue != null && endDate >= startDate)
    }

    val isModified: Boolean by derivedStateOf {
        enabled != initialEnabled ||
            input != initialInput ||
            startEpochDay != initialStartEpochDay ||
            endEpochDay != initialEndEpochDay
    }
}

@Composable
internal fun rememberTemporaryDietEnergyDeficitFormState(
    initialOverride: DietEnergyDeficitOverride?
): TemporaryDietEnergyDeficitFormState {
    val today = LocalDate.now()
    val sanitizedInitialOverride =
        initialOverride?.takeIf { it.energyDeficitKcal > 0.0 && it.endDate >= it.startDate }
    val initialInput = sanitizedInitialOverride?.energyDeficitKcal?.formatClipZeros().orEmpty()
    val initialStartEpochDay = sanitizedInitialOverride?.startDate ?: today
    val initialEndEpochDay = sanitizedInitialOverride?.endDate ?: today
    val textFieldState = rememberTextFieldState(initialInput)
    val enabledState = rememberSaveable { mutableStateOf(sanitizedInitialOverride != null) }
    val startEpochDayState = rememberSaveable {
        mutableStateOf(initialStartEpochDay.toEpochDays())
    }
    val endEpochDayState = rememberSaveable { mutableStateOf(initialEndEpochDay.toEpochDays()) }

    return remember(
        textFieldState,
        sanitizedInitialOverride,
        enabledState,
        startEpochDayState,
        endEpochDayState,
    ) {
        TemporaryDietEnergyDeficitFormState(
            textFieldState = textFieldState,
            initialEnabled = sanitizedInitialOverride != null,
            initialInput = initialInput,
            initialStartEpochDay = initialStartEpochDay.toEpochDays(),
            initialEndEpochDay = initialEndEpochDay.toEpochDays(),
            enabledState = enabledState,
            startEpochDayState = startEpochDayState,
            endEpochDayState = endEpochDayState,
        )
    }
}

@Composable
private fun DietEnergyDeficitForm(
    state: DietEnergyDeficitFormState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            state = state.textFieldState,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(Res.string.label_daily_calorie_deficit)) },
            supportingText = { Text(stringResource(Res.string.neutral_daily_calorie_deficit)) },
            suffix = { Text(stringResource(Res.string.unit_kcal)) },
            isError = state.textFieldState.text.isNotBlank() && state.parsedValue == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        TemporaryDietEnergyDeficitForm(state = state.override, modifier = Modifier.fillMaxWidth())
    }
}

private fun String.parseNonNegativeDeficit(): Double? =
    trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0.0 }

private fun String.parsePositiveDeficit(): Double? =
    trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }

@Composable
private fun TemporaryDietEnergyDeficitForm(
    state: TemporaryDietEnergyDeficitFormState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .clickable { state.enabled = !state.enabled }
                    .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Checkbox(checked = state.enabled, onCheckedChange = null)
            Text(
                text = stringResource(Res.string.label_temporary_diet_deficit),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        AnimatedVisibility(state.enabled) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    state = state.textFieldState,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.label_temporary_calorie_deficit)) },
                    supportingText = {
                        Text(stringResource(Res.string.neutral_temporary_daily_calorie_deficit))
                    },
                    suffix = { Text(stringResource(Res.string.unit_kcal)) },
                    isError = state.parsedValue == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DatePickerField(
                        date = state.startDate,
                        onDateChange = { selectedDate ->
                            selectedDate?.let { state.startDate = it }
                        },
                        label = stringResource(Res.string.label_start_date),
                        modifier = Modifier.weight(1f),
                    )
                    DatePickerField(
                        date = state.endDate,
                        onDateChange = { selectedDate ->
                            selectedDate?.let { state.endDate = it }
                        },
                        label = stringResource(Res.string.label_end_date),
                        isError = state.endDate < state.startDate,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (state.endDate < state.startDate) {
                    Text(
                        text = stringResource(Res.string.error_end_date_before_start_date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun BasalMetabolicRateProfileForm(
    state: BasalMetabolicRateProfileFormState,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()
    val suggestion =
        calculateBasalMetabolicRateSuggestion(profile = state.intoProfile(), today = today)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(Res.string.headline_body_data),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BodyMetricTextField(
                field = state.weightKg,
                label = stringResource(Res.string.weight),
                suffix = stringResource(Res.string.unit_kilogram_short),
                modifier = Modifier.weight(1f),
            )
            BodyMetricTextField(
                field = state.heightCm,
                label = stringResource(Res.string.height),
                suffix = stringResource(Res.string.unit_centimeter_short),
                modifier = Modifier.weight(1f),
            )
        }
        BirthDatePickerField(
            birthDate = state.birthDate,
            onBirthDateChange = { state.birthDate = it },
            modifier = Modifier.fillMaxWidth(),
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(Res.string.biological_sex),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BiologicalSexToggle(
                sex = state.sex,
                onSexChange = { state.sex = it },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (suggestion == null) {
            Text(
                text = stringResource(Res.string.hint_body_data_required_for_bmr),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            BmrSuggestionBlock(
                basalMetabolicRateKcal = suggestion.basalMetabolicRateKcal,
                minimalActivityKcal = suggestion.minimalActivityKcal,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun BmrSuggestionBlock(
    basalMetabolicRateKcal: Int,
    minimalActivityKcal: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(Res.string.headline_basal_metabolic_rate_suggestions),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text =
                    stringResource(
                        Res.string.neutral_basal_metabolic_rate_value,
                        basalMetabolicRateKcal,
                    ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = minimalActivitySuggestionText(minimalActivityKcal),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun minimalActivitySuggestionText(minimalActivityKcal: Int): AnnotatedString {
    val text = stringResource(Res.string.neutral_minimal_activity_value, minimalActivityKcal)
    val valueText = "$minimalActivityKcal ${stringResource(Res.string.unit_kcal)}"
    val valueIndex = text.lastIndexOf(valueText)

    return buildAnnotatedString {
        if (valueIndex == -1) {
            append(text)
            return@buildAnnotatedString
        }

        append(text.substring(0, valueIndex))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(valueText)
        }
        append(text.substring(valueIndex + valueText.length))
    }
}

@Composable
private fun BodyMetricTextField(
    field: FormField<Double?, DailyGoalsFormError>,
    label: String,
    suffix: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        state = field.textFieldState,
        modifier = modifier,
        label = { Text(label) },
        suffix = { Text(suffix) },
        isError = field.error != null,
        keyboardOptions =
            KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
    )
}

@Composable
private fun BirthDatePickerField(
    birthDate: LocalDate?,
    onBirthDateChange: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
) {
    DatePickerField(
        date = birthDate,
        onDateChange = onBirthDateChange,
        label = stringResource(Res.string.birth_date),
        modifier = modifier,
    )
}

@Composable
private fun DatePickerField(
    date: LocalDate?,
    onDateChange: (LocalDate?) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    val dateFormatter = LocalDateFormatter.current

    if (showDatePicker) {
        val datePickerState =
            rememberDatePickerState(initialSelectedDateMillis = date?.toUtcEpochMillis())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDateChange(
                            datePickerState.selectedDateMillis
                                ?.let { Instant.fromEpochMilliseconds(it) }
                                ?.toLocalDateTime(TimeZone.UTC)
                                ?.date
                        )
                        showDatePicker = false
                    }
                ) {
                    Text(stringResource(Res.string.positive_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = date?.let(dateFormatter::formatDateShort).orEmpty(),
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            isError = isError,
        )
        Box(Modifier.matchParentSize().clickable { showDatePicker = true })
    }
}

@Composable
private fun BiologicalSexToggle(
    sex: BiologicalSex?,
    onSexChange: (BiologicalSex) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement =
            Arrangement.spacedBy(
                ButtonGroupDefaults.ConnectedSpaceBetween,
                Alignment.CenterHorizontally,
            ),
    ) {
        ToggleButton(
            checked = sex == BiologicalSex.Male,
            onCheckedChange = { onSexChange(BiologicalSex.Male) },
            modifier = Modifier.height(44.dp).semantics { role = Role.RadioButton },
            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
        ) {
            Text(stringResource(Res.string.biological_sex_male))
        }
        ToggleButton(
            checked = sex == BiologicalSex.Female,
            onCheckedChange = { onSexChange(BiologicalSex.Female) },
            modifier = Modifier.height(44.dp).semantics { role = Role.RadioButton },
            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
        ) {
            Text(stringResource(Res.string.biological_sex_female))
        }
    }
}

private fun LocalDate.toUtcEpochMillis(): Long = toEpochDays() * 86_400_000L

@Composable
private fun DayPicker(
    useSeparateGoals: Boolean,
    onUseSeparateGoalsChange: (Boolean) -> Unit,
    selectedDay: Int,
    onSelectedDayChange: (Int) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current

    Column(modifier) {
        Text(
            text = stringResource(Res.string.headline_pick_the_days),
            modifier = Modifier.padding(contentPadding),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .clickable { onUseSeparateGoalsChange(!useSeparateGoals) }
                    .padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Checkbox(
                modifier = Modifier.padding(vertical = 16.dp),
                checked = useSeparateGoals,
                onCheckedChange = null,
            )
            Text(
                text = stringResource(Res.string.action_set_separate_goals),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        AnimatedVisibility(useSeparateGoals) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                contentPadding = contentPadding,
            ) {
                item {
                    val dateFormatter = LocalDateFormatter.current
                    val weekDayNamesShort = dateFormatter.weekDayNamesShort

                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                ButtonGroupDefaults.ConnectedSpaceBetween,
                                Alignment.CenterHorizontally,
                            )
                    ) {
                        weekDayNamesShort.forEachIndexed { i, name ->
                            ToggleButton(
                                checked = selectedDay == i,
                                onCheckedChange = {
                                    onSelectedDayChange(i)
                                    hapticFeedback.performHapticFeedback(
                                        HapticFeedbackType.SegmentTick
                                    )
                                },
                                modifier = Modifier.semantics { role = Role.RadioButton },
                                shapes =
                                    when (i) {
                                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                        weekDayNamesShort.lastIndex ->
                                            ButtonGroupDefaults.connectedTrailingButtonShapes()

                                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                    },
                            ) {
                                Text(name)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeightOrPercentageToggle(
    useDistribution: Boolean,
    onUseDistributionChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement =
            Arrangement.spacedBy(
                ButtonGroupDefaults.ConnectedSpaceBetween,
                Alignment.CenterHorizontally,
            ),
    ) {
        ToggleButton(
            checked = !useDistribution,
            onCheckedChange = { onUseDistributionChange(false) },
            modifier = Modifier.height(56.dp).semantics { role = Role.RadioButton },
            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
        ) {
            Icon(painter = painterResource(Res.drawable.ic_weight), contentDescription = null)
            Spacer(Modifier.width(ToggleButtonDefaults.IconSpacing))
            Text(stringResource(Res.string.weight))
        }
        ToggleButton(
            checked = useDistribution,
            onCheckedChange = { onUseDistributionChange(true) },
            modifier = Modifier.height(56.dp).semantics { role = Role.RadioButton },
            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
        ) {
            Icon(imageVector = Icons.Outlined.Percent, contentDescription = null)
            Spacer(Modifier.width(ToggleButtonDefaults.IconSpacing))
            Text(stringResource(Res.string.headline_percentages))
        }
    }
}

@Composable
private fun MacroInputSliderForm(state: DailyGoalsFormState, modifier: Modifier = Modifier) {
    val nutrientsPalette = LocalNutrientsPalette.current
    val nutrientsOrder = LocalNutrientsOrder.current

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            state = state.energy.textFieldState,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(Res.string.unit_energy)) },
            suffix = { Text(stringResource(Res.string.unit_kcal)) },
            isError = state.energy.error != null,
            keyboardOptions =
                KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        )

        nutrientsOrder.forEach {
            when (it) {
                NutrientsOrder.Proteins ->
                    MacroSlider(
                        value = state.proteinsSlider,
                        onValueChange = { state.proteinsSlider = it },
                        color = nutrientsPalette.proteinsOnSurfaceContainer,
                        label = stringResource(Res.string.nutriment_proteins),
                    )

                NutrientsOrder.Fats ->
                    MacroSlider(
                        value = state.fatsSlider,
                        onValueChange = { state.fatsSlider = it },
                        color = nutrientsPalette.fatsOnSurfaceContainer,
                        label = stringResource(Res.string.nutriment_fats),
                    )

                NutrientsOrder.Carbohydrates ->
                    MacroSlider(
                        value = state.carbsSlider,
                        onValueChange = { state.carbsSlider = it },
                        color = nutrientsPalette.carbohydratesOnSurfaceContainer,
                        label = stringResource(Res.string.nutriment_carbohydrates),
                    )

                NutrientsOrder.Other,
                NutrientsOrder.Vitamins,
                NutrientsOrder.Minerals -> Unit
            }
        }
    }
}

@Composable
private fun MacroSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    color: Color,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = label, color = color, style = MaterialTheme.typography.bodyMedium)
            Text(
                text =
                    buildString {
                        append(value.roundToInt())
                        append("%")
                    },
                color = color,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Slider(
            value = value,
            onValueChange = { onValueChange(it.roundToInt().toFloat()) },
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(activeTrackColor = color, thumbColor = color),
        )
    }
}

@Composable
private fun MacroInput(state: DailyGoalsFormState, modifier: Modifier = Modifier) {
    val nutrientsPalette = LocalNutrientsPalette.current
    val nutrientsOrder = LocalNutrientsOrder.current

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.energy.TextField(
            label = stringResource(Res.string.unit_energy),
            color = MaterialTheme.colorScheme.outline,
            suffix = stringResource(Res.string.unit_kcal),
            modifier = Modifier.fillMaxWidth(),
        )

        nutrientsOrder.forEach {
            when (it) {
                NutrientsOrder.Proteins ->
                    state.proteins.TextField(
                        label = stringResource(Res.string.nutriment_proteins),
                        color = nutrientsPalette.proteinsOnSurfaceContainer,
                        modifier = Modifier.fillMaxWidth(),
                    )

                NutrientsOrder.Fats ->
                    state.fats.TextField(
                        label = stringResource(Res.string.nutriment_fats),
                        color = nutrientsPalette.fatsOnSurfaceContainer,
                        modifier = Modifier.fillMaxWidth(),
                    )

                NutrientsOrder.Carbohydrates ->
                    state.carbs.TextField(
                        label = stringResource(Res.string.nutriment_carbohydrates),
                        color = nutrientsPalette.carbohydratesOnSurfaceContainer,
                        modifier = Modifier.fillMaxWidth(),
                    )

                NutrientsOrder.Other,
                NutrientsOrder.Vitamins,
                NutrientsOrder.Minerals -> Unit
            }
        }
    }
}

@Composable
private fun FormField<Double, DailyGoalsFormError>.TextField(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    suffix: String = stringResource(Res.string.unit_gram_short),
    imeAction: ImeAction = ImeAction.Next,
) {
    OutlinedTextField(
        state = textFieldState,
        modifier = modifier,
        label = { Text(label) },
        suffix = { Text(suffix) },
        keyboardOptions =
            KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
        isError = error != null,
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedBorderColor = color,
                unfocusedBorderColor = color,
                focusedLabelColor = color,
                unfocusedLabelColor = color,
            ),
    )
}

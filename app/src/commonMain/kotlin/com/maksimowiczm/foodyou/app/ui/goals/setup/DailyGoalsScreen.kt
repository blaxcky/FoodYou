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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
import com.maksimowiczm.foodyou.common.extension.now
import com.maksimowiczm.foodyou.goals.domain.entity.BiologicalSex
import com.maksimowiczm.foodyou.goals.domain.usecase.calculateBasalMetabolicRateSuggestion
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

    DailyGoalsContent(
        weeklyState = weeklyState,
        basalMetabolicRateProfileState = basalMetabolicRateProfileState,
        onBack = onBack,
        onSave = {
            viewModel.update(
                weeklyGoals = weeklyState.intoWeeklyGoals(),
                basalMetabolicRateProfile =
                    basalMetabolicRateProfileState.intoProfile(),
            )
        },
        modifier = modifier,
    )
}

@Composable
internal fun DailyGoalsContent(
    weeklyState: WeeklyGoalsState,
    basalMetabolicRateProfileState: BasalMetabolicRateProfileFormState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier,
) {
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    val isModified = weeklyState.isModified || basalMetabolicRateProfileState.isModified
    val isValid = weeklyState.isValid && basalMetabolicRateProfileState.isValid
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
                DayPicker(
                    useSeparateGoals = weeklyState.useSeparateGoals,
                    onUseSeparateGoalsChange = { weeklyState.useSeparateGoals = it },
                    selectedDay = weeklyState.selectedDay,
                    onSelectedDayChange = { weeklyState.selectedDay = it },
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item { Spacer(Modifier.height(16.dp)) }

            item {
                val state = weeklyState.selectedDayGoals

                Column(modifier) {
                    BasalMetabolicRateProfileForm(
                        state = basalMetabolicRateProfileState,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
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
                    AdditionalGoalsForm(
                        state = state.additionalState,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
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
        BodyMetricTextField(
            field = state.weightKg,
            label = stringResource(Res.string.weight),
            suffix = stringResource(Res.string.unit_kilogram_short),
            modifier = Modifier.fillMaxWidth(),
        )
        BodyMetricTextField(
            field = state.heightCm,
            label = stringResource(Res.string.height),
            suffix = stringResource(Res.string.unit_centimeter_short),
            modifier = Modifier.fillMaxWidth(),
        )
        BirthDatePickerField(
            birthDate = state.birthDate,
            onBirthDateChange = { state.birthDate = it },
            modifier = Modifier.fillMaxWidth(),
        )
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

        if (suggestion == null) {
            Text(
                text = stringResource(Res.string.hint_body_data_required_for_bmr),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text =
                    stringResource(
                        Res.string.neutral_basal_metabolic_rate_value,
                        suggestion.basalMetabolicRateKcal,
                    ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text =
                    stringResource(
                        Res.string.neutral_minimal_activity_value,
                        suggestion.minimalActivityKcal,
                    ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
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
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    val dateFormatter = LocalDateFormatter.current

    if (showDatePicker) {
        val datePickerState =
            rememberDatePickerState(initialSelectedDateMillis = birthDate?.toUtcEpochMillis())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onBirthDateChange(
                            datePickerState.selectedDateMillis
                                ?.let(Instant::fromEpochMilliseconds)
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
            value = birthDate?.let(dateFormatter::formatDate).orEmpty(),
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(Res.string.birth_date)) },
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
            modifier = Modifier.height(56.dp).semantics { role = Role.RadioButton },
            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
        ) {
            Text(stringResource(Res.string.biological_sex_male))
        }
        ToggleButton(
            checked = sex == BiologicalSex.Female,
            onCheckedChange = { onSexChange(BiologicalSex.Female) },
            modifier = Modifier.height(56.dp).semantics { role = Role.RadioButton },
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

package com.maksimowiczm.foodyou.app.ui.goals.setup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationEventHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.app.ui.common.component.DiscardDialog
import com.maksimowiczm.foodyou.app.ui.common.form.FormField
import com.maksimowiczm.foodyou.app.ui.common.theme.LocalNutrientsPalette
import com.maksimowiczm.foodyou.app.ui.common.utility.LocalEnergyFormatter
import com.maksimowiczm.foodyou.app.ui.common.utility.LocalNutrientsOrder
import com.maksimowiczm.foodyou.app.ui.common.utility.validatedNonNegativeEnergyKcal
import com.maksimowiczm.foodyou.common.compose.extension.LaunchedCollectWithLifecycle
import com.maksimowiczm.foodyou.common.compose.utility.LocalDateFormatter
import com.maksimowiczm.foodyou.common.compose.utility.formatClipZeros
import com.maksimowiczm.foodyou.common.extension.now
import com.maksimowiczm.foodyou.goals.domain.entity.BasalMetabolicRateProfile
import com.maksimowiczm.foodyou.goals.domain.entity.BiologicalSex
import com.maksimowiczm.foodyou.goals.domain.usecase.calculateBasalMetabolicRateSuggestion
import com.maksimowiczm.foodyou.goals.domain.usecase.dailyEnergyDeficitKcal
import com.maksimowiczm.foodyou.goals.domain.usecase.weeklyWeightLossKg
import com.maksimowiczm.foodyou.settings.domain.entity.DEFAULT_MAX_ENERGY_DEFICIT_KCAL
import com.maksimowiczm.foodyou.settings.domain.entity.DietEnergyDeficitOverride
import com.maksimowiczm.foodyou.settings.domain.entity.NutrientsOrder
import foodyou.app.generated.resources.*
import kotlin.math.roundToInt
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** The overview and its sub-pages. All pages share one form, saved from the overview. */
internal enum class DailyGoalsPage {
    Overview,
    BasalMetabolicRate,
    ActivityLevel,
    EnergyAndMacros,
    Nutrients,
    Diet,
}

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
            initialMaxEnergyDeficit = setupState.maxEnergyDeficitKcal,
        )
    val energyFormatter = LocalEnergyFormatter.current
    val initialLockedDaySurplusInput =
        remember(setupState.defaultLockedDaySurplusKcal, energyFormatter) {
            energyFormatter
                .fromKcal(setupState.defaultLockedDaySurplusKcal)
                .formatClipZeros("%.2f")
        }
    var lockedDaySurplusInput by rememberSaveable(initialLockedDaySurplusInput) {
        mutableStateOf(initialLockedDaySurplusInput)
    }
    val defaultLockedDaySurplusKcal =
        validatedNonNegativeEnergyKcal(lockedDaySurplusInput, energyFormatter::toKcal)

    CalculatedEnergyGoalSync(
        weeklyState = weeklyState,
        basalMetabolicRateProfileState = basalMetabolicRateProfileState,
    )

    DailyGoalsContent(
        weeklyState = weeklyState,
        basalMetabolicRateProfileState = basalMetabolicRateProfileState,
        dietEnergyDeficitState = dietEnergyDeficitState,
        lockedDaySurplusInput = lockedDaySurplusInput,
        onLockedDaySurplusInputChange = { lockedDaySurplusInput = it },
        lockedDaySurplusValid = defaultLockedDaySurplusKcal != null,
        lockedDaySurplusModified = lockedDaySurplusInput != initialLockedDaySurplusInput,
        onBack = onBack,
        onSave = {
            viewModel.update(
                weeklyGoals = weeklyState.intoWeeklyGoals(),
                basalMetabolicRateProfile = basalMetabolicRateProfileState.intoProfile(),
                dietEnergyDeficitKcal = dietEnergyDeficitState.value,
                dietEnergyDeficitOverride = dietEnergyDeficitState.overrideValue,
                maxEnergyDeficitKcal = dietEnergyDeficitState.catchUpCap.value,
                defaultLockedDaySurplusKcal = defaultLockedDaySurplusKcal ?: return@DailyGoalsContent,
            )
        },
        modifier = modifier,
    )
}

/**
 * Keeps every day's energy goal equal to the fixed basal metabolic rate × activity level while the
 * calculated mode is on. It lives above the pages so a changed activity level is applied even when
 * the user saves straight from the overview. Fields are only written when the value differs, so
 * opening the screen does not mark it as modified.
 */
@Composable
private fun CalculatedEnergyGoalSync(
    weeklyState: WeeklyGoalsState,
    basalMetabolicRateProfileState: BasalMetabolicRateProfileFormState,
) {
    LaunchedEffect(weeklyState, basalMetabolicRateProfileState) {
        snapshotFlow {
                if (basalMetabolicRateProfileState.calculateEnergyGoal) {
                    basalMetabolicRateProfileState.calculatedEnergyGoalKcal?.roundToInt()
                } else {
                    null
                }
            }
            .collect { energyKcal ->
                if (energyKcal == null) return@collect
                val text = energyKcal.toString()
                weeklyState.days.forEach { day ->
                    if (day.energy.textFieldState.text.toString() != text) {
                        day.energy.textFieldState.setTextAndPlaceCursorAtEnd(text)
                    }
                }
            }
    }
}

@Composable
internal fun DailyGoalsContent(
    weeklyState: WeeklyGoalsState,
    basalMetabolicRateProfileState: BasalMetabolicRateProfileFormState,
    dietEnergyDeficitState: DietEnergyDeficitFormState,
    lockedDaySurplusInput: String,
    onLockedDaySurplusInputChange: (String) -> Unit,
    lockedDaySurplusValid: Boolean,
    lockedDaySurplusModified: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier,
) {
    var page by rememberSaveable { mutableStateOf(DailyGoalsPage.Overview) }
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    val isModified =
        weeklyState.isModified ||
            basalMetabolicRateProfileState.isModified ||
            dietEnergyDeficitState.isModified ||
            lockedDaySurplusModified
    val isValid =
        weeklyState.isValid &&
            basalMetabolicRateProfileState.isValid &&
            dietEnergyDeficitState.isValid &&
            lockedDaySurplusValid
    val handleOnBack: () -> Unit = {
        when {
            page != DailyGoalsPage.Overview -> page = DailyGoalsPage.Overview
            isModified -> showDiscardDialog = true
            else -> onBack()
        }
    }
    NavigationEventHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = page != DailyGoalsPage.Overview || isModified,
        onBackCompleted = {
            if (page != DailyGoalsPage.Overview) {
                page = DailyGoalsPage.Overview
            } else {
                showDiscardDialog = true
            }
        },
    )
    if (showDiscardDialog) {
        DiscardDialog(onDismissRequest = { showDiscardDialog = false }, onDiscard = onBack) {
            Text(stringResource(Res.string.question_discard_changes))
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollState = key(page) { rememberScrollState() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(page.title())) },
                navigationIcon = { ArrowBackIconButton(handleOnBack) },
                actions = {
                    if (page == DailyGoalsPage.Overview) {
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
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .imePadding()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .verticalScroll(scrollState)
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            when (page) {
                DailyGoalsPage.Overview ->
                    DailyGoalsOverview(
                        weeklyState = weeklyState,
                        basalMetabolicRateProfileState = basalMetabolicRateProfileState,
                        dietEnergyDeficitState = dietEnergyDeficitState,
                        lockedDaySurplusInput = lockedDaySurplusInput,
                        lockedDaySurplusValid = lockedDaySurplusValid,
                        onOpen = { page = it },
                    )

                DailyGoalsPage.BasalMetabolicRate ->
                    BasalMetabolicRatePage(state = basalMetabolicRateProfileState)

                DailyGoalsPage.ActivityLevel ->
                    ActivityLevelPage(state = basalMetabolicRateProfileState)

                DailyGoalsPage.EnergyAndMacros ->
                    EnergyAndMacrosPage(
                        weeklyState = weeklyState,
                        basalMetabolicRateProfileState = basalMetabolicRateProfileState,
                    )

                DailyGoalsPage.Nutrients -> NutrientsPage(weeklyState = weeklyState)

                DailyGoalsPage.Diet ->
                    DietPage(
                        dietEnergyDeficitState = dietEnergyDeficitState,
                        lockedDaySurplusInput = lockedDaySurplusInput,
                        onLockedDaySurplusInputChange = onLockedDaySurplusInputChange,
                        lockedDaySurplusValid = lockedDaySurplusValid,
                    )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun DailyGoalsPage.title(): StringResource =
    when (this) {
        DailyGoalsPage.Overview -> Res.string.headline_daily_goals
        DailyGoalsPage.BasalMetabolicRate -> Res.string.daily_goals_basal_metabolic_rate
        DailyGoalsPage.ActivityLevel -> Res.string.daily_goals_activity_level
        DailyGoalsPage.EnergyAndMacros -> Res.string.daily_goals_energy_and_macros
        DailyGoalsPage.Nutrients -> Res.string.daily_goals_additional_nutrients
        DailyGoalsPage.Diet -> Res.string.daily_goals_headline_diet
    }

// ---------------------------------------------------------------------------------------------
// Overview
// ---------------------------------------------------------------------------------------------

@Composable
private fun DailyGoalsOverview(
    weeklyState: WeeklyGoalsState,
    basalMetabolicRateProfileState: BasalMetabolicRateProfileFormState,
    dietEnergyDeficitState: DietEnergyDeficitFormState,
    lockedDaySurplusInput: String,
    lockedDaySurplusValid: Boolean,
    onOpen: (DailyGoalsPage) -> Unit,
) {
    val dayGoals = weeklyState.selectedDayGoals
    val activeDays = weeklyState.activeDays
    val energyKcal = dayGoals.energy.value.roundToInt()
    val kcal = stringResource(Res.string.unit_kcal)
    val off = stringResource(Res.string.daily_goals_off)
    val dateFormatter = LocalDateFormatter.current
    val energyFormatter = LocalEnergyFormatter.current
    val bmrState = basalMetabolicRateProfileState

    Column(
        modifier = Modifier.padding(horizontal = 4.dp).padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = stringResource(Res.string.daily_goals_your_goal),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = energyKcal.toString(), style = MaterialTheme.typography.displaySmall)
            Text(
                text = kcal,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        dietEnergyDeficitState.value?.let { deficit ->
            Text(
                text =
                    stringResource(
                        Res.string.daily_goals_diet_goal,
                        (dayGoals.energy.value - deficit).roundToInt(),
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    OverviewGroup(title = stringResource(Res.string.daily_goals_headline_needs)) {
        OverviewRow(
            label = stringResource(Res.string.daily_goals_basal_metabolic_rate),
            value =
                bmrState.basalMetabolicRate.value?.let { "${it.roundToInt()} $kcal" }
                    ?: stringResource(Res.string.daily_goals_not_set),
            isError = !bmrState.isBasalMetabolicRateValid || !bmrState.isBodyDataValid,
            onClick = { onOpen(DailyGoalsPage.BasalMetabolicRate) },
        )
        OverviewDivider()
        OverviewRow(
            label = stringResource(Res.string.daily_goals_activity_level),
            value = stringResource(Res.string.daily_goals_percent_value, bmrState.activityLevelPercent),
            isError = !bmrState.isActivityLevelValid,
            onClick = { onOpen(DailyGoalsPage.ActivityLevel) },
        )
    }

    OverviewGroup(title = stringResource(Res.string.daily_goals_headline_goals)) {
        OverviewRow(
            label = stringResource(Res.string.daily_goals_energy),
            value = "$energyKcal $kcal",
            supporting =
                if (bmrState.calculateEnergyGoal) {
                    stringResource(
                        Res.string.daily_goals_bmr_times_pal,
                        bmrState.activityLevelPercent,
                    )
                } else {
                    null
                },
            isError = activeDays.any { it.energy.error != null },
            onClick = { onOpen(DailyGoalsPage.EnergyAndMacros) },
        )
        OverviewDivider()
        OverviewRow(
            label = stringResource(Res.string.headline_macronutrients),
            value = macroSummary(dayGoals),
            isError =
                activeDays.any {
                    it.proteins.error != null || it.fats.error != null || it.carbs.error != null
                },
            onClick = { onOpen(DailyGoalsPage.EnergyAndMacros) },
        )
        OverviewDivider()
        OverviewRow(
            label = stringResource(Res.string.daily_goals_additional_nutrients),
            value = null,
            isError = activeDays.any { !it.additionalState.isValid },
            onClick = { onOpen(DailyGoalsPage.Nutrients) },
        )
        OverviewDivider()
        OverviewRow(
            label = stringResource(Res.string.daily_goals_applies_to),
            value =
                stringResource(
                    if (weeklyState.useSeparateGoals) Res.string.daily_goals_per_weekday
                    else Res.string.daily_goals_all_days
                ),
            onClick = { onOpen(DailyGoalsPage.EnergyAndMacros) },
        )
    }

    OverviewGroup(title = stringResource(Res.string.daily_goals_headline_diet)) {
        OverviewRow(
            label = stringResource(Res.string.label_daily_calorie_deficit),
            value = dietEnergyDeficitState.value?.let { "${it.formatClipZeros()} $kcal" } ?: off,
            isError =
                dietEnergyDeficitState.textFieldState.text.isNotBlank() &&
                    dietEnergyDeficitState.parsedValue == null,
            onClick = { onOpen(DailyGoalsPage.Diet) },
        )
        OverviewDivider()
        OverviewRow(
            label = stringResource(Res.string.label_max_daily_energy_deficit),
            value =
                dietEnergyDeficitState.catchUpCap.value?.let { "${it.formatClipZeros()} $kcal" }
                    ?: off,
            isError = !dietEnergyDeficitState.catchUpCap.isValid,
            onClick = { onOpen(DailyGoalsPage.Diet) },
        )
        OverviewDivider()
        OverviewRow(
            label = stringResource(Res.string.label_temporary_diet_deficit),
            value =
                dietEnergyDeficitState.overrideValue?.let {
                    "${dateFormatter.formatDateShort(it.startDate)} – " +
                        dateFormatter.formatDateShort(it.endDate)
                } ?: off,
            isError = !dietEnergyDeficitState.override.isValid,
            onClick = { onOpen(DailyGoalsPage.Diet) },
        )
        OverviewDivider()
        OverviewRow(
            label = stringResource(Res.string.daily_goals_locked_days),
            value = "+$lockedDaySurplusInput ${energyFormatter.suffix()}",
            isError = !lockedDaySurplusValid,
            onClick = { onOpen(DailyGoalsPage.Diet) },
        )
    }
}

@Composable
private fun macroSummary(state: DailyGoalsFormState): String =
    if (state.inputType == InputType.Percentage) {
        stringResource(
            Res.string.daily_goals_macro_split_percent,
            state.proteinsSlider.roundToInt(),
            state.fatsSlider.roundToInt(),
            state.carbsSlider.roundToInt(),
        )
    } else {
        stringResource(
            Res.string.daily_goals_macro_split_grams,
            state.proteins.value.formatClipZeros("%.0f"),
            state.fats.value.formatClipZeros("%.0f"),
            state.carbs.value.formatClipZeros("%.0f"),
        )
    }

@Composable
private fun OverviewGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(title)
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun OverviewRow(
    label: String,
    value: String?,
    onClick: () -> Unit,
    isError: Boolean = false,
    supporting: String? = null,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(onClick = onClick)
                .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            if (isError) {
                Text(
                    text = stringResource(Res.string.daily_goals_check_input),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (supporting != null) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OverviewDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(horizontal = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun PageCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun PageHint(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ---------------------------------------------------------------------------------------------
// Basal metabolic rate
// ---------------------------------------------------------------------------------------------

@Composable
private fun BasalMetabolicRatePage(state: BasalMetabolicRateProfileFormState) {
    val suggestion =
        calculateBasalMetabolicRateSuggestion(profile = state.intoProfile(), today = LocalDate.now())
    val kcal = stringResource(Res.string.unit_kcal)

    PageCard {
        OutlinedTextField(
            state = state.basalMetabolicRate.textFieldState,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(Res.string.daily_goals_your_bmr)) },
            suffix = { Text(kcal) },
            supportingText = { Text(stringResource(Res.string.daily_goals_bmr_stays_fixed)) },
            isError = !state.isBasalMetabolicRateValid,
            textStyle = MaterialTheme.typography.headlineSmall,
            lineLimits = androidx.compose.foundation.text.input.TextFieldLineLimits.SingleLine,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(Res.string.daily_goals_estimate_from_body_data))
        PageCard {
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
            DatePickerField(
                date = state.birthDate,
                onDateChange = { state.birthDate = it },
                label = stringResource(Res.string.birth_date),
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
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (suggestion == null) {
                Text(
                    text = stringResource(Res.string.hint_body_data_required_for_bmr),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val estimated = suggestion.basalMetabolicRateKcal
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(Res.string.daily_goals_estimated),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "$estimated $kcal",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                    if (state.basalMetabolicRate.value?.roundToInt() != estimated) {
                        FilledTonalButton(onClick = { state.adoptBasalMetabolicRate(estimated) }) {
                            Text(stringResource(Res.string.daily_goals_adopt))
                        }
                    } else {
                        Text(
                            text = stringResource(Res.string.daily_goals_matches_your_value),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        PageHint(stringResource(Res.string.daily_goals_estimate_explanation))
    }
}

// ---------------------------------------------------------------------------------------------
// Activity level
// ---------------------------------------------------------------------------------------------

private val activityLevelPresets =
    listOf(
        105 to Res.string.daily_goals_pal_untracked,
        120 to Res.string.daily_goals_pal_lying,
        145 to Res.string.daily_goals_pal_seated,
        165 to Res.string.daily_goals_pal_seated_some,
        185 to Res.string.daily_goals_pal_standing,
        210 to Res.string.daily_goals_pal_heavy,
    )

@Composable
private fun ActivityLevelPage(state: BasalMetabolicRateProfileFormState) {
    val percent = state.activityLevelPercent
    val isValid = state.isActivityLevelValid
    val min = BasalMetabolicRateProfile.MIN_PHYSICAL_ACTIVITY_LEVEL_PERCENT
    val max = BasalMetabolicRateProfile.MAX_PHYSICAL_ACTIVITY_LEVEL_PERCENT
    // 105 % is shown as "PAL 1,05" (German) or "PAL 1.05" (English).
    val palText =
        stringResource(
            Res.string.daily_goals_pal_value,
            percent / 100,
            (percent % 100).toString().padStart(2, '0'),
        )

    PageCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalIconButton(
                onClick = { state.setActivityLevel(percent - 1) },
                enabled = isValid && percent > min,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Remove,
                    contentDescription = stringResource(Res.string.daily_goals_decrease),
                )
            }
            OutlinedTextField(
                state = state.activityLevel.textFieldState,
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(Res.string.daily_goals_activity_level_percent)) },
                suffix = { Text("%") },
                isError = !isValid,
                textStyle =
                    MaterialTheme.typography.headlineMedium.copy(textAlign = TextAlign.Center),
                lineLimits = androidx.compose.foundation.text.input.TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            FilledTonalIconButton(
                onClick = { state.setActivityLevel(percent + 1) },
                enabled = isValid && percent < max,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(Res.string.daily_goals_increase),
                )
            }
        }
        val calculated = state.calculatedEnergyGoalKcal?.roundToInt()
        Text(
            text =
                when {
                    !isValid -> stringResource(Res.string.daily_goals_pal_range_error)
                    calculated != null ->
                        stringResource(Res.string.daily_goals_pal_summary, palText, calculated)
                    else -> stringResource(Res.string.daily_goals_pal_summary_no_bmr, palText)
                },
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color =
                if (isValid) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.error,
        )
        Slider(
            value = percent.coerceIn(min, max).toFloat(),
            onValueChange = { state.setActivityLevel(it.roundToInt()) },
            valueRange = min.toFloat()..max.toFloat(),
        )
    }

    Text(
        text = stringResource(Res.string.daily_goals_pal_hint),
        modifier = Modifier.padding(horizontal = 4.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(Res.string.daily_goals_reference_values))
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.selectableGroup()) {
                activityLevelPresets.forEachIndexed { index, (presetPercent, title) ->
                    if (index > 0) OverviewDivider()
                    val selected = isValid && percent == presetPercent
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .selectable(
                                    selected = selected,
                                    onClick = { state.setActivityLevel(presetPercent) },
                                    role = Role.RadioButton,
                                )
                                .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Text(
                            text =
                                stringResource(Res.string.daily_goals_percent_value, presetPercent),
                            modifier = Modifier.width(56.dp),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = stringResource(title),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
        PageHint(stringResource(Res.string.daily_goals_pal_source))
    }
}

// ---------------------------------------------------------------------------------------------
// Energy and macros
// ---------------------------------------------------------------------------------------------

@Composable
private fun EnergyAndMacrosPage(
    weeklyState: WeeklyGoalsState,
    basalMetabolicRateProfileState: BasalMetabolicRateProfileFormState,
) {
    val dayGoals = weeklyState.selectedDayGoals
    val bmrState = basalMetabolicRateProfileState
    val kcal = stringResource(Res.string.unit_kcal)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(Res.string.daily_goals_applies_to))
        TwoOptionToggle(
            firstLabel = stringResource(Res.string.daily_goals_all_days),
            secondLabel = stringResource(Res.string.daily_goals_per_weekday),
            secondSelected = weeklyState.useSeparateGoals,
            onSecondSelectedChange = { weeklyState.useSeparateGoals = it },
        )
        AnimatedVisibility(weeklyState.useSeparateGoals) {
            WeekdayPicker(
                selectedDay = weeklyState.selectedDay,
                onSelectedDayChange = { weeklyState.selectedDay = it },
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(Res.string.daily_goals_energy))
        PageCard {
            // With a basal metabolic rate the goal always follows it; it is typed in only without.
            val calculated = bmrState.calculatedEnergyGoalKcal?.roundToInt()
            if (bmrState.calculateEnergyGoal && calculated != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text =
                            stringResource(
                                Res.string.daily_goals_bmr_times_pal,
                                bmrState.activityLevelPercent,
                            ),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(text = "$calculated $kcal", style = MaterialTheme.typography.titleLarge)
                }
                Text(
                    text = stringResource(Res.string.daily_goals_energy_follows),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                dayGoals.energy.TextField(
                    label = stringResource(Res.string.unit_energy),
                    color = MaterialTheme.colorScheme.outline,
                    suffix = kcal,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(Res.string.daily_goals_set_bmr_first),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(Res.string.headline_macronutrients))
        PageCard {
            WeightOrPercentageToggle(
                useDistribution = dayGoals.inputType == InputType.Percentage,
                onUseDistributionChange = {
                    dayGoals.inputType = if (it) InputType.Percentage else InputType.Weight
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (dayGoals.inputType == InputType.Percentage) {
                MacroInputSliderForm(state = dayGoals, modifier = Modifier.fillMaxWidth())
            } else {
                MacroInput(state = dayGoals, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun TwoOptionToggle(
    firstLabel: String,
    secondLabel: String,
    secondSelected: Boolean,
    onSecondSelectedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    firstEnabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        ToggleButton(
            checked = !secondSelected,
            onCheckedChange = { onSecondSelectedChange(false) },
            enabled = firstEnabled,
            modifier = Modifier.weight(1f).height(48.dp).semantics { role = Role.RadioButton },
            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
        ) {
            Text(firstLabel)
        }
        ToggleButton(
            checked = secondSelected,
            onCheckedChange = { onSecondSelectedChange(true) },
            modifier = Modifier.weight(1f).height(48.dp).semantics { role = Role.RadioButton },
            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
        ) {
            Text(secondLabel)
        }
    }
}

@Composable
private fun WeekdayPicker(
    selectedDay: Int,
    onSelectedDayChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val weekDayNamesShort = LocalDateFormatter.current.weekDayNamesShort

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        item {
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
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
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

// ---------------------------------------------------------------------------------------------
// Additional nutrients
// ---------------------------------------------------------------------------------------------

@Composable
private fun NutrientsPage(weeklyState: WeeklyGoalsState) {
    if (weeklyState.useSeparateGoals) {
        val weekDayNamesShort = LocalDateFormatter.current.weekDayNamesShort
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(
                stringResource(
                    Res.string.daily_goals_goals_for_day,
                    weekDayNamesShort.getOrElse(weeklyState.selectedDay) { "" },
                )
            )
            WeekdayPicker(
                selectedDay = weeklyState.selectedDay,
                onSelectedDayChange = { weeklyState.selectedDay = it },
            )
        }
    }
    AdditionalGoalsForm(
        state = weeklyState.selectedDayGoals.additionalState,
        modifier = Modifier.fillMaxWidth(),
    )
}

// ---------------------------------------------------------------------------------------------
// Diet
// ---------------------------------------------------------------------------------------------

@Composable
private fun DietPage(
    dietEnergyDeficitState: DietEnergyDeficitFormState,
    lockedDaySurplusInput: String,
    onLockedDaySurplusInputChange: (String) -> Unit,
    lockedDaySurplusValid: Boolean,
) {
    PageCard { DietEnergyDeficitForm(state = dietEnergyDeficitState) }
    PageCard {
        MaxEnergyDeficitForm(
            state = dietEnergyDeficitState.catchUpCap,
            dietEnergyDeficitKcal = dietEnergyDeficitState.value,
        )
    }
    PageCard {
        Text(
            text = stringResource(Res.string.daily_goals_locked_days),
            style = MaterialTheme.typography.titleMedium,
        )
        LockedDaySurplusDefaultField(
            value = lockedDaySurplusInput,
            onValueChange = onLockedDaySurplusInputChange,
            isValid = lockedDaySurplusValid,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LockedDaySurplusDefaultField(
    value: String,
    onValueChange: (String) -> Unit,
    isValid: Boolean,
    modifier: Modifier = Modifier,
) {
    val energyFormatter = LocalEnergyFormatter.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(stringResource(Res.string.locked_day_default_surplus_label)) },
        suffix = { Text(energyFormatter.suffix()) },
        supportingText = {
            Text(
                stringResource(
                    if (isValid) Res.string.locked_day_default_surplus_supporting_text
                    else Res.string.locked_day_surplus_error
                )
            )
        },
        isError = !isValid,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

@Stable
internal class DietEnergyDeficitFormState(
    val textFieldState: TextFieldState,
    private val initialInput: String,
    overrideState: TemporaryDietEnergyDeficitFormState,
    catchUpCapState: MaxEnergyDeficitFormState,
) {
    private val input: String
        get() = textFieldState.text.toString()

    val override: TemporaryDietEnergyDeficitFormState = overrideState

    val catchUpCap: MaxEnergyDeficitFormState = catchUpCapState

    val parsedValue: Double? by derivedStateOf { input.parseNonNegativeDeficit() }

    val value: Double? by derivedStateOf { parsedValue?.takeIf { it > 0.0 } }

    val overrideValue: DietEnergyDeficitOverride? by derivedStateOf { override.value }

    val isValid: Boolean by derivedStateOf {
        (input.isBlank() || parsedValue != null) && override.isValid && catchUpCap.isValid
    }

    val isModified: Boolean by derivedStateOf {
        input != initialInput || override.isModified || catchUpCap.isModified
    }

    /**
     * Turns the temporary deficit on or off. An empty temporary deficit starts with the daily one,
     * so switching it on does not leave an empty, invalid field that blocks saving.
     */
    fun setTemporaryDeficitEnabled(enabled: Boolean) {
        if (enabled && override.textFieldState.text.isBlank() && value != null) {
            override.textFieldState.setTextAndPlaceCursorAtEnd(input.trim())
        }
        override.enabled = enabled
    }
}

@Composable
internal fun rememberDietEnergyDeficitFormState(
    initialValue: Double?,
    initialOverride: DietEnergyDeficitOverride? = null,
    initialMaxEnergyDeficit: Double? = DEFAULT_MAX_ENERGY_DEFICIT_KCAL,
): DietEnergyDeficitFormState {
    val sanitizedInitialValue = initialValue?.takeIf { it > 0.0 }
    val textFieldState = rememberTextFieldState(sanitizedInitialValue?.formatClipZeros().orEmpty())
    val overrideState = rememberTemporaryDietEnergyDeficitFormState(initialOverride)
    val catchUpCapState = rememberMaxEnergyDeficitFormState(initialMaxEnergyDeficit)

    return remember(textFieldState, sanitizedInitialValue, overrideState, catchUpCapState) {
        DietEnergyDeficitFormState(
            textFieldState = textFieldState,
            initialInput = sanitizedInitialValue?.formatClipZeros().orEmpty(),
            overrideState = overrideState,
            catchUpCapState = catchUpCapState,
        )
    }
}

/**
 * The cap on how far the weekly balancing lowers the diet goal below the normal goal. Turned off,
 * the field keeps its text, so switching it back on restores the last cap.
 */
@Stable
internal class MaxEnergyDeficitFormState(
    val textFieldState: TextFieldState,
    private val initialEnabled: Boolean,
    private val initialInput: String,
    enabledState: MutableState<Boolean>,
) {
    private val input: String
        get() = textFieldState.text.toString()

    var enabled by enabledState

    val parsedValue: Double? by derivedStateOf { input.parsePositiveDeficit() }

    val value: Double? by derivedStateOf { if (enabled) parsedValue else null }

    val isValid: Boolean by derivedStateOf { !enabled || parsedValue != null }

    val isModified: Boolean by derivedStateOf {
        enabled != initialEnabled || (enabled && input != initialInput)
    }
}

@Composable
internal fun rememberMaxEnergyDeficitFormState(initialValue: Double?): MaxEnergyDeficitFormState {
    val sanitizedInitialValue = initialValue?.takeIf { it.isFinite() && it > 0.0 }
    val initialInput = (sanitizedInitialValue ?: DEFAULT_MAX_ENERGY_DEFICIT_KCAL).formatClipZeros()
    val textFieldState = rememberTextFieldState(initialInput)
    val enabledState = rememberSaveable { mutableStateOf(sanitizedInitialValue != null) }

    return remember(textFieldState, sanitizedInitialValue, enabledState) {
        MaxEnergyDeficitFormState(
            textFieldState = textFieldState,
            initialEnabled = sanitizedInitialValue != null,
            initialInput = initialInput,
            enabledState = enabledState,
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
        WeeklyWeightLossCalculator(state = state)
        OutlinedTextField(
            state = state.textFieldState,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(Res.string.label_daily_calorie_deficit)) },
            supportingText = { Text(stringResource(Res.string.neutral_daily_calorie_deficit)) },
            suffix = { Text(stringResource(Res.string.unit_kcal)) },
            isError = state.textFieldState.text.isNotBlank() && state.parsedValue == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        TemporaryDietEnergyDeficitForm(
            state = state.override,
            onEnabledChange = state::setTemporaryDeficitEnabled,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Suggests a daily deficit from the weight the user wants to lose per week. The weekly amount is
 * only a helper and is not stored; it starts from the current deficit.
 */
@Composable
private fun WeeklyWeightLossCalculator(state: DietEnergyDeficitFormState) {
    val decimalSeparator = stringResource(Res.string.daily_goals_decimal_separator)
    val prefill =
        remember(decimalSeparator) {
            state.value
                ?.let { weeklyWeightLossKg(it).formatClipZeros("%.2f") }
                ?.replace(",", ".")
                ?.replace(".", decimalSeparator)
                .orEmpty()
        }
    val weeklyLossState = rememberTextFieldState(prefill)
    val weeklyLossText = weeklyLossState.text.toString()
    val weeklyLossKg = weeklyLossText.trim().replace(',', '.').toDoubleOrNull()
    val isInvalid = weeklyLossText.isNotBlank() && (weeklyLossKg == null || weeklyLossKg < 0)
    val suggestedKcal =
        if (weeklyLossText.isNotEmpty() && weeklyLossText == prefill) {
            // The prefill is rounded; unchanged, it stands for the current deficit itself.
            state.value?.roundToInt()
        } else {
            weeklyLossKg?.takeIf { it > 0.0 }?.let { dailyEnergyDeficitKcal(it).roundToInt() }
        }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            state = weeklyLossState,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(Res.string.daily_goals_weight_loss_per_week)) },
            suffix = { Text(stringResource(Res.string.unit_kilogram_short)) },
            supportingText = { Text(stringResource(Res.string.daily_goals_deficit_formula_hint)) },
            isError = isInvalid,
            lineLimits = androidx.compose.foundation.text.input.TextFieldLineLimits.SingleLine,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        if (suggestedKcal != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(Res.string.daily_goals_suggested_deficit, suggestedKcal),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (state.value?.roundToInt() != suggestedKcal) {
                    FilledTonalButton(
                        onClick = {
                            state.textFieldState.setTextAndPlaceCursorAtEnd(
                                suggestedKcal.toString()
                            )
                        }
                    ) {
                        Text(stringResource(Res.string.daily_goals_adopt))
                    }
                } else {
                    Text(
                        text = stringResource(Res.string.daily_goals_matches_your_deficit),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MaxEnergyDeficitForm(state: MaxEnergyDeficitFormState, dietEnergyDeficitKcal: Double?) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(Res.string.daily_goals_catch_up_cap_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(Res.string.daily_goals_catch_up_cap_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
                text = stringResource(Res.string.daily_goals_catch_up_cap_enabled),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        AnimatedVisibility(state.enabled) {
            val catchUpKcal =
                state.parsedValue?.let { cap ->
                    (cap - (dietEnergyDeficitKcal ?: 0.0)).coerceAtLeast(0.0).roundToInt()
                }
            OutlinedTextField(
                state = state.textFieldState,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(Res.string.label_max_daily_energy_deficit)) },
                supportingText =
                    catchUpKcal?.let {
                        { Text(stringResource(Res.string.daily_goals_catch_up_cap_supporting, it)) }
                    },
                suffix = { Text(stringResource(Res.string.unit_kcal)) },
                isError = state.parsedValue == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }
    }
}

private fun String.parseNonNegativeDeficit(): Double? =
    trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0.0 }

private fun String.parsePositiveDeficit(): Double? =
    trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }

@Composable
private fun TemporaryDietEnergyDeficitForm(
    state: TemporaryDietEnergyDeficitFormState,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .clickable { onEnabledChange(!state.enabled) }
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

// ---------------------------------------------------------------------------------------------
// Shared fields
// ---------------------------------------------------------------------------------------------

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
            modifier = Modifier.height(48.dp).semantics { role = Role.RadioButton },
            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
        ) {
            Icon(painter = painterResource(Res.drawable.ic_weight), contentDescription = null)
            Spacer(Modifier.width(ToggleButtonDefaults.IconSpacing))
            Text(stringResource(Res.string.weight))
        }
        ToggleButton(
            checked = useDistribution,
            onCheckedChange = { onUseDistributionChange(true) },
            modifier = Modifier.height(48.dp).semantics { role = Role.RadioButton },
            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
        ) {
            Icon(imageVector = Icons.Outlined.Percent, contentDescription = null)
            Spacer(Modifier.width(ToggleButtonDefaults.IconSpacing))
            Text(stringResource(Res.string.headline_percentages))
        }
    }
}

/** Macro sliders; the energy they refer to is edited in the energy section above. */
@Composable
private fun MacroInputSliderForm(state: DailyGoalsFormState, modifier: Modifier = Modifier) {
    val nutrientsPalette = LocalNutrientsPalette.current
    val nutrientsOrder = LocalNutrientsOrder.current

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
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

/** Macro gram fields; the energy goal is edited in the energy section above. */
@Composable
private fun MacroInput(state: DailyGoalsFormState, modifier: Modifier = Modifier) {
    val nutrientsPalette = LocalNutrientsPalette.current
    val nutrientsOrder = LocalNutrientsOrder.current

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

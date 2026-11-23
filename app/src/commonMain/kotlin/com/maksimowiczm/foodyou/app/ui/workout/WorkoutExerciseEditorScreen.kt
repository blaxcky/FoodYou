package com.maksimowiczm.foodyou.app.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.workout.domain.CardioConversion
import com.maksimowiczm.foodyou.workout.domain.StrengthKcalMode
import com.maksimowiczm.foodyou.workout.domain.StrengthMeasure
import com.maksimowiczm.foodyou.workout.domain.WorkoutKind
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutExerciseEditorScreen(
    exerciseId: Long?,
    cardio: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: WorkoutExerciseEditorViewModel = koinViewModel()
    LaunchedEffect(exerciseId, cardio) {
        viewModel.load(exerciseId, if (cardio) WorkoutKind.Cardio else WorkoutKind.Strength)
    }
    val form by viewModel.form.collectAsStateWithLifecycle()
    val weight by viewModel.latestWeightKg.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (form.id == null) Res.string.workout_new_exercise
                            else Res.string.workout_edit_exercise
                        )
                    )
                },
                navigationIcon = { ArrowBackIconButton(onBack) },
                actions = {
                    Button(
                        onClick = { viewModel.save(onBack) },
                        enabled = form.canSave && !form.isLoading,
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Text(stringResource(Res.string.action_save))
                    }
                },
            )
        },
    ) { padding ->
        WorkoutExerciseEditorContent(
            form = form,
            latestWeightKg = weight,
            onUpdate = viewModel::update,
            onArchive = { viewModel.setArchived(true, onBack) },
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorkoutExerciseEditorContent(
    form: WorkoutExerciseForm,
    latestWeightKg: Double?,
    onUpdate: (WorkoutExerciseForm.() -> WorkoutExerciseForm) -> Unit,
    onArchive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val separator = decimalSeparator()
    var showAddWeight by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Segments(
            options =
                listOf(
                    WorkoutKind.Strength to Res.string.workout_kind_strength,
                    WorkoutKind.Cardio to Res.string.workout_kind_cardio,
                ),
            selected = form.kind,
            onSelect = { kind -> onUpdate { copy(kind = kind) } },
        )

        EditorCard {
            OutlinedTextField(
                value = form.name,
                onValueChange = { value -> onUpdate { copy(name = value) } },
                label = { Text(stringResource(Res.string.workout_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (form.kind == WorkoutKind.Strength) {
                FieldLabel(stringResource(Res.string.workout_measured_in))
                Segments(
                    options =
                        listOf(
                            StrengthMeasure.Reps to Res.string.workout_measure_reps,
                            StrengthMeasure.Seconds to Res.string.workout_measure_seconds,
                            StrengthMeasure.Level to Res.string.workout_measure_level,
                        ),
                    selected = form.measure,
                    onSelect = { measure -> onUpdate { copy(measure = measure) } },
                )
                CheckRow(
                    title = stringResource(Res.string.workout_uses_weight),
                    subtitle = stringResource(Res.string.workout_uses_weight_hint),
                    checked = form.usesWeight,
                    onChange = { checked -> onUpdate { copy(usesWeight = checked) } },
                )
                if (form.usesWeight) {
                    FieldLabel(stringResource(Res.string.workout_your_dumbbells))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        form.weightOptionsKg.forEach { kg ->
                            val label = kg.formatAmount(separator)
                            InputChip(
                                selected = false,
                                onClick = { onUpdate { copy(weightOptionsKg = weightOptionsKg - kg) } },
                                label = { Text("$label kg ×") },
                                modifier = Modifier.heightIn(min = 48.dp),
                            )
                        }
                        FilterChip(
                            selected = true,
                            onClick = { showAddWeight = true },
                            label = { Text(stringResource(Res.string.workout_add_weight)) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
                OutlinedTextField(
                    value = form.note,
                    onValueChange = { value -> onUpdate { copy(note = value) } },
                    label = { Text(stringResource(Res.string.workout_note)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    stringResource(Res.string.workout_cardio_input_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CheckRow(
                    title = stringResource(Res.string.workout_ask_distance),
                    checked = form.askDistance,
                    onChange = { checked -> onUpdate { copy(askDistance = checked) } },
                )
            }
        }

        if (form.kind == WorkoutKind.Strength) {
            EditorCard {
                FieldLabel(stringResource(Res.string.workout_calories))
                RadioRow(
                    title = stringResource(Res.string.workout_kcal_per_set),
                    selected = form.kcalMode == StrengthKcalMode.PerSet || form.measure != StrengthMeasure.Reps,
                    onSelect = { onUpdate { copy(kcalMode = StrengthKcalMode.PerSet) } },
                )
                if (form.measure == StrengthMeasure.Reps) {
                    RadioRow(
                        title = stringResource(Res.string.workout_kcal_per_rep),
                        selected = form.kcalMode == StrengthKcalMode.PerRep,
                        onSelect = { onUpdate { copy(kcalMode = StrengthKcalMode.PerRep) } },
                    )
                }
                OutlinedTextField(
                    value = form.kcalValue,
                    onValueChange = { value -> onUpdate { copy(kcalValue = value) } },
                    label = { Text(stringResource(Res.string.workout_kcal)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            EditorCard {
                FieldLabel(stringResource(Res.string.workout_conversion_title))
                RadioRow(
                    title = stringResource(Res.string.workout_conversion_concept2),
                    subtitle =
                        stringResource(
                            Res.string.workout_conversion_concept2_hint,
                            latestWeightKg?.let { it.formatAmount(separator) + " kg" }
                                ?: stringResource(Res.string.workout_no_weight),
                        ),
                    selected = form.cardioConversion == CardioConversion.Concept2,
                    onSelect = { onUpdate { copy(cardioConversion = CardioConversion.Concept2) } },
                )
                RadioRow(
                    title = stringResource(Res.string.workout_conversion_device),
                    selected = form.cardioConversion == CardioConversion.Device,
                    onSelect = { onUpdate { copy(cardioConversion = CardioConversion.Device) } },
                )
                RadioRow(
                    title = stringResource(Res.string.workout_conversion_factor),
                    selected = form.cardioConversion == CardioConversion.Factor,
                    onSelect = { onUpdate { copy(cardioConversion = CardioConversion.Factor) } },
                )
                if (form.cardioConversion == CardioConversion.Factor) {
                    OutlinedTextField(
                        value = form.cardioFactor,
                        onValueChange = { value -> onUpdate { copy(cardioFactor = value) } },
                        label = { Text(stringResource(Res.string.workout_factor)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                HorizontalDivider()
                OutlinedTextField(
                    value = form.cardioDiscountPercent,
                    onValueChange = { value -> onUpdate { copy(cardioDiscountPercent = value) } },
                    label = { Text(stringResource(Res.string.workout_discount)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                CheckRow(
                    title = stringResource(Res.string.workout_exclude_steps),
                    subtitle = stringResource(Res.string.workout_exclude_steps_hint),
                    checked = form.excludeSteps,
                    onChange = { checked -> onUpdate { copy(excludeSteps = checked) } },
                )
            }
        }

        EditorCard {
            if (form.kind == WorkoutKind.Strength) {
                OutlinedTextField(
                    value = form.helpsWith,
                    onValueChange = { value -> onUpdate { copy(helpsWith = value) } },
                    label = { Text(stringResource(Res.string.workout_helps_with)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = form.reminderDays,
                onValueChange = { value -> onUpdate { copy(reminderDays = value.filter(Char::isDigit).take(3)) } },
                label = { Text(stringResource(Res.string.workout_reminder_days)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (form.id != null && !form.archived) {
            TextButton(
                onClick = onArchive,
                modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp),
            ) {
                Text(stringResource(Res.string.workout_archive), color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showAddWeight) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddWeight = false },
            title = { Text(stringResource(Res.string.workout_add_weight)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(6) },
                    label = { Text(stringResource(Res.string.workout_weight_kg)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        text.parseDecimal()?.takeIf { it > 0.0 }?.let { kg ->
                            onUpdate { copy(weightOptionsKg = (weightOptionsKg + kg).distinct().sorted()) }
                        }
                        showAddWeight = false
                    }
                ) {
                    Text(stringResource(Res.string.positive_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddWeight = false }) { Text(stringResource(Res.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun EditorCard(content: @Composable () -> Unit) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> Segments(options: List<Pair<T, StringResource>>, selected: T, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = selected == value,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(label))
            }
        }
    }
}

@Composable
internal fun CheckRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, subtitle: String? = null) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Checkbox) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Checkbox(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun RadioRow(title: String, selected: Boolean, onSelect: () -> Unit, subtitle: String? = null) {
    Row(
        modifier =
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

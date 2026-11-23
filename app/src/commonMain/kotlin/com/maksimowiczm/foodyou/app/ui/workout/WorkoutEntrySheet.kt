package com.maksimowiczm.foodyou.app.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.maksimowiczm.foodyou.workout.domain.CardioConversion
import com.maksimowiczm.foodyou.workout.domain.StrengthMeasure
import com.maksimowiczm.foodyou.workout.domain.WorkoutEntry
import com.maksimowiczm.foodyou.workout.domain.WorkoutExercise
import com.maksimowiczm.foodyou.workout.domain.WorkoutFeel
import com.maksimowiczm.foodyou.workout.domain.WorkoutKind
import com.maksimowiczm.foodyou.workout.domain.cardioEnergy
import com.maksimowiczm.foodyou.workout.domain.startMinuteForNow
import com.maksimowiczm.foodyou.workout.domain.strengthSetKcal
import foodyou.app.generated.resources.*
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

/**
 * Content of the bottom sheet that logs one strength set or one cardio session. Values are only
 * entered by hand.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorkoutEntrySheetContent(
    exercise: WorkoutExercise,
    initialDraft: WorkoutEntryDraft,
    today: LocalDate,
    nowMinuteOfDay: Int,
    lastEntry: WorkoutEntry?,
    entriesTodayCount: Int,
    bodyWeightKg: Double?,
    onSave: (draft: WorkoutEntryDraft, another: Boolean) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember(initialDraft) { mutableStateOf(initialDraft) }
    val isNew = initialDraft.entryId == null
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(exercise.name, style = MaterialTheme.typography.headlineSmall)
            val subtitle = sheetSubtitle(exercise, isNew, lastEntry, entriesTodayCount)
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        WhenButton(
            date = draft.date,
            minuteOfDay = draft.minuteOfDay,
            today = today,
            onChange = { date, minute -> draft = draft.copy(date = date, minuteOfDay = minute) },
        )

        when (exercise.kind) {
            WorkoutKind.Strength -> StrengthFields(exercise, draft) { draft = it }
            WorkoutKind.Cardio -> CardioFields(exercise, draft, bodyWeightKg, nowMinuteOfDay) { draft = it }
        }

        val kcal = draft.energyKcal(exercise, bodyWeightKg)
        if (exercise.kind == WorkoutKind.Strength) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (isNew) {
                    OutlinedButton(
                        onClick = { onSave(draft, true) },
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    ) {
                        Text(stringResource(Res.string.workout_save_and_next))
                    }
                } else {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                        Text(stringResource(Res.string.action_cancel))
                    }
                }
                Button(
                    onClick = { onSave(draft, false) },
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                ) {
                    Text(stringResource(Res.string.action_save))
                }
            }
            if (exercise.measure == StrengthMeasure.Seconds) {
                Text(
                    text = stringResource(Res.string.workout_no_timer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(Res.string.action_cancel))
                }
                Button(
                    onClick = { onSave(draft, false) },
                    enabled = draft.durationMinutes > 0,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                ) {
                    Text(stringResource(Res.string.workout_save_kcal, kcalLabel(kcal)))
                }
            }
        }
    }
}

@Composable
private fun sheetSubtitle(
    exercise: WorkoutExercise,
    isNew: Boolean,
    lastEntry: WorkoutEntry?,
    entriesTodayCount: Int,
): String {
    if (exercise.kind == WorkoutKind.Cardio) {
        return when (exercise.cardioConversion) {
            CardioConversion.Concept2 -> stringResource(Res.string.workout_conversion_short_concept2)
            CardioConversion.Device -> stringResource(Res.string.workout_conversion_short_device)
            CardioConversion.Factor ->
                stringResource(
                    Res.string.workout_conversion_short_factor,
                    exercise.cardioFactor.formatAmount(decimalSeparator()),
                )
        }
    }
    val parts = mutableListOf<String>()
    if (isNew && lastEntry != null) {
        parts += stringResource(Res.string.workout_last_time, entryValues(lastEntry, exercise))
        parts += stringResource(Res.string.workout_prefilled)
    }
    if (exercise.note.isNotBlank()) parts += exercise.note
    return parts.joinToString(" · ")
}

@Composable
private fun WhenButton(
    date: LocalDate,
    minuteOfDay: Int?,
    today: LocalDate,
    onChange: (LocalDate, Int?) -> Unit,
) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var pickedDate by remember { mutableStateOf(date) }
    val label =
        if (date == today && minuteOfDay == null) stringResource(Res.string.workout_when_now)
        else
            stringResource(
                Res.string.workout_when_at,
                dayLabel(date, today),
                minuteOfDay?.let(::minuteLabel) ?: stringResource(Res.string.workout_time_now),
            )
    OutlinedButton(onClick = { showDate = true }, modifier = Modifier.heightIn(min = 48.dp)) {
        Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }

    if (showDate) {
        val state =
            rememberDatePickerState(
                initialSelectedDateMillis = date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
            )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickedDate =
                            state.selectedDateMillis
                                ?.let(Instant::fromEpochMilliseconds)
                                ?.toLocalDateTime(TimeZone.UTC)
                                ?.date ?: date
                        showDate = false
                        showTime = true
                    }
                ) {
                    Text(stringResource(Res.string.positive_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDate = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = state)
        }
    }

    if (showTime) {
        val initial = minuteOfDay ?: (12 * 60)
        val state = rememberTimePickerState(initialHour = initial / 60, initialMinute = initial % 60)
        TimePickerDialog(
            onDismissRequest = { showTime = false },
            title = {
                Text(
                    stringResource(Res.string.workout_pick_time),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showTime = false
                        onChange(pickedDate, state.hour * 60 + state.minute)
                    }
                ) {
                    Text(stringResource(Res.string.positive_ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showTime = false
                        onChange(pickedDate, if (pickedDate == today) null else minuteOfDay ?: initial)
                    }
                ) {
                    Text(stringResource(Res.string.workout_time_now))
                }
            },
        ) {
            TimePicker(state)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StrengthFields(
    exercise: WorkoutExercise,
    draft: WorkoutEntryDraft,
    onChange: (WorkoutEntryDraft) -> Unit,
) {
    val separator = decimalSeparator()
    if (exercise.usesWeight && exercise.weightOptionsKg.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(stringResource(Res.string.workout_weight_per_hand))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                exercise.weightOptionsKg.forEach { kg ->
                    FilterChip(
                        selected = draft.weightKg == kg,
                        onClick = { onChange(draft.copy(weightKg = kg)) },
                        label = {
                            Text(stringResource(Res.string.workout_value_kg, kg.formatAmount(separator)))
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
        }
    }

    when (exercise.measure) {
        StrengthMeasure.Reps ->
            BigStepper(
                label = stringResource(Res.string.workout_reps_label),
                value = draft.reps,
                suffix = "",
                step = 1,
                onValue = { onChange(draft.withReps(exercise, it)) },
            )
        StrengthMeasure.Seconds -> {
            BigStepper(
                label = stringResource(Res.string.workout_seconds_label),
                value = draft.seconds,
                suffix = " s",
                step = 5,
                onValue = { onChange(draft.copy(seconds = it)) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(15, 20, 25, 30).forEach { seconds ->
                    FilterChip(
                        selected = draft.seconds == seconds,
                        onClick = { onChange(draft.copy(seconds = seconds)) },
                        label = { CenteredLabel("$seconds s") },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    )
                }
            }
        }
        StrengthMeasure.Level ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallStepper(
                    label = stringResource(Res.string.workout_level_label),
                    value = draft.level,
                    onValue = { onChange(draft.copy(level = it)) },
                    modifier = Modifier.weight(1f),
                )
                SmallStepper(
                    label = stringResource(Res.string.workout_reps_label),
                    value = draft.reps,
                    onValue = { onChange(draft.withReps(exercise, it)) },
                    modifier = Modifier.weight(1f),
                )
            }
    }

    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(Res.string.workout_set_kcal), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(Res.string.workout_set_kcal_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val kcal = draft.kcalOverride ?: strengthSetKcal(exercise, draft.reps)
            DecimalField(
                value = kcal,
                onValue = { onChange(draft.copy(kcalOverride = it)) },
                modifier = Modifier.width(112.dp),
                suffix = stringResource(Res.string.workout_kcal),
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel(stringResource(Res.string.workout_feel))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(
                    WorkoutFeel.Easy to Res.string.workout_feel_easy,
                    WorkoutFeel.Fine to Res.string.workout_feel_fine,
                    WorkoutFeel.Hard to Res.string.workout_feel_hard,
                )
                .forEach { (feel, label) ->
                    FilterChip(
                        selected = draft.feel == feel,
                        onClick = { onChange(draft.copy(feel = if (draft.feel == feel) null else feel)) },
                        label = { CenteredLabel(stringResource(label)) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    )
                }
        }
    }
}

/** Per-repetition kcal follow the repetitions unless the user typed a value for this set. */
private fun WorkoutEntryDraft.withReps(exercise: WorkoutExercise, reps: Int): WorkoutEntryDraft =
    copy(
        reps = reps,
        kcalOverride =
            if (exercise.kcalMode == com.maksimowiczm.foodyou.workout.domain.StrengthKcalMode.PerRep)
                null
            else kcalOverride,
    )

@Composable
private fun CardioFields(
    exercise: WorkoutExercise,
    draft: WorkoutEntryDraft,
    bodyWeightKg: Double?,
    nowMinuteOfDay: Int,
    onChange: (WorkoutEntryDraft) -> Unit,
) {
    val separator = decimalSeparator()
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IntField(
            label = stringResource(Res.string.workout_duration_minutes),
            value = draft.durationMinutes,
            onValue = { onChange(draft.copy(durationMinutes = it)) },
            modifier = Modifier.weight(1f),
        )
        DecimalField(
            label = stringResource(Res.string.workout_device_kcal),
            value = draft.deviceKcal,
            onValue = { onChange(draft.copy(deviceKcal = it ?: 0.0)) },
            modifier = Modifier.weight(1f),
        )
    }
    if (exercise.askDistance) {
        IntField(
            label = stringResource(Res.string.workout_distance_optional),
            value = draft.distanceMeters ?: 0,
            onValue = { onChange(draft.copy(distanceMeters = it.takeIf { m -> m > 0 })) },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    val energy = cardioEnergy(exercise, draft.deviceKcal, draft.durationMinutes, bodyWeightKg)
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (exercise.cardioConversion == CardioConversion.Concept2) {
                Text(
                    text =
                        if (bodyWeightKg != null)
                            stringResource(
                                Res.string.workout_concept2_formula,
                                bodyWeightKg.formatAmount(separator),
                            )
                        else stringResource(Res.string.workout_concept2_no_weight),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            BreakdownLine(stringResource(Res.string.workout_device_line), kcalLabel(energy.deviceKcal))
            if (exercise.cardioConversion != CardioConversion.Device) {
                BreakdownLine(stringResource(Res.string.workout_converted), kcalLabel(energy.convertedKcal))
            }
            if (exercise.cardioDiscountPercent > 0.0) {
                BreakdownLine(
                    stringResource(
                        Res.string.workout_discount_line,
                        exercise.cardioDiscountPercent.formatAmount(separator) + " %",
                    ),
                    "− " + kcalLabel(energy.discountKcal),
                )
            }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.workout_to_diary),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    kcalLabel(energy.finalKcal),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    if (exercise.excludeSteps && draft.durationMinutes > 0) {
        val start = draft.minuteOfDay ?: startMinuteForNow(nowMinuteOfDay, draft.durationMinutes)
        val end = (start + draft.durationMinutes).coerceAtMost(24 * 60)
        Text(
            text = stringResource(Res.string.workout_steps_excluded, minuteLabel(start), minuteLabel(end)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BreakdownLine(label: String, value: String) {
    Row {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun BigStepper(label: String, value: Int, suffix: String, step: Int, onValue: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel(label)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedIconButton(
                onClick = { onValue((value - step).coerceAtLeast(0)) },
                modifier = Modifier.size(64.dp),
            ) {
                Icon(Icons.Filled.Remove, contentDescription = stringResource(Res.string.workout_less))
            }
            Text(
                text = "$value$suffix",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            OutlinedIconButton(onClick = { onValue(value + step) }, modifier = Modifier.size(64.dp)) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(Res.string.workout_more))
            }
        }
    }
}

@Composable
private fun SmallStepper(label: String, value: Int, onValue: (Int) -> Unit, modifier: Modifier = Modifier) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FieldLabel(label)
            Text("$value", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedIconButton(onClick = { onValue((value - 1).coerceAtLeast(0)) }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Remove, contentDescription = stringResource(Res.string.workout_less) + " " + label)
                }
                OutlinedIconButton(onClick = { onValue(value + 1) }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(Res.string.workout_more) + " " + label)
                }
            }
        }
    }
}

@Composable
internal fun IntField(label: String, value: Int, onValue: (Int) -> Unit, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf(if (value == 0) "" else value.toString()) }
    LaunchedEffect(value) {
        if ((text.toIntOrNull() ?: 0) != value) text = if (value == 0) "" else value.toString()
    }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            val digits = input.filter(Char::isDigit).take(6)
            text = digits
            onValue(digits.toIntOrNull() ?: 0)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
internal fun DecimalField(
    value: Double?,
    onValue: (Double?) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    suffix: String? = null,
) {
    val separator = decimalSeparator()
    var text by remember { mutableStateOf(value?.takeIf { it != 0.0 }?.formatAmount(separator).orEmpty()) }
    LaunchedEffect(value) {
        if ((text.parseDecimal() ?: 0.0) != (value ?: 0.0)) {
            text = value?.takeIf { it != 0.0 }?.formatAmount(separator).orEmpty()
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            val cleaned = input.filter { it.isDigit() || it == ',' || it == '.' }.take(8)
            text = cleaned
            onValue(cleaned.parseDecimal())
        },
        label = label?.let { { Text(it) } },
        suffix = suffix?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@Composable
private fun CenteredLabel(text: String) {
    Text(text, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
}

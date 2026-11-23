package com.maksimowiczm.foodyou.app.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.workout.domain.WorkoutEntry
import com.maksimowiczm.foodyou.workout.domain.WorkoutExercise
import com.maksimowiczm.foodyou.workout.domain.WorkoutKind
import com.maksimowiczm.foodyou.workout.domain.weekStart
import foodyou.app.generated.resources.*
import kotlin.time.Instant
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private data class EntrySheetTarget(
    val exercise: WorkoutExercise,
    val draft: WorkoutEntryDraft,
    val lastEntry: WorkoutEntry?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingScreen(
    initialDate: LocalDate?,
    initialTab: TrainingTab,
    onBack: () -> Unit,
    onEditExercise: (id: Long) -> Unit,
    onNewExercise: (cardio: Boolean) -> Unit,
    onGoals: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: TrainingViewModel = koinViewModel()
    val state = viewModel.state.collectAsStateWithLifecycle().value
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    LaunchedEffect(viewModel) { viewModel.applyInitialDate(initialDate) }
    var sheet by remember { mutableStateOf<EntrySheetTarget?>(null) }
    var pickExerciseForDate by remember { mutableStateOf<LocalDate?>(null) }
    var showHistoryDatePicker by remember { mutableStateOf(false) }
    var showArchived by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun openNew(exercise: WorkoutExercise, date: LocalDate) {
        val current = state ?: return
        val own = current.recentEntries.filter { it.exerciseId == exercise.id }
        val last = own.maxWithOrNull(compareBy({ it.date }, { it.minuteOfDay }, { it.id.value }))
        sheet =
            EntrySheetTarget(
                exercise = exercise,
                draft =
                    newWorkoutEntryDraft(
                        exercise = exercise,
                        date = date,
                        lastEntry = last,
                        minuteOfDay = null,
                    ),
                lastEntry = last,
            )
    }

    val deletedMessage = stringResource(Res.string.workout_entry_deleted, "%s")
    val undoLabel = stringResource(Res.string.workout_undo)
    val unknownName = stringResource(Res.string.workout_unknown_exercise)

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(Res.string.workout_title))
                        if (state != null) {
                            Text(
                                text = todaySummary(state),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = { ArrowBackIconButton(onBack) },
                actions = {
                    IconButton(onClick = { onNewExercise(false) }) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(Res.string.workout_new_exercise))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 4.dp)
            ) {
                val tabs =
                    listOf(
                        TrainingTab.Today to Res.string.workout_tab_today,
                        TrainingTab.Week to Res.string.workout_tab_week,
                        TrainingTab.History to Res.string.workout_tab_history,
                    )
                tabs.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = tab == value,
                        onClick = { tab = value },
                        shape = SegmentedButtonDefaults.itemShape(index, tabs.size),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(label))
                    }
                }
            }
            if (state == null) return@Column
            val contentPadding = PaddingValues(12.dp)
            when (tab) {
                TrainingTab.Today ->
                    TrainingTodayContent(
                        plan = state.plan,
                        query = state.query,
                        onQuery = viewModel::setQuery,
                        onLog = { openNew(it.exercise, state.today) },
                        onEditExercise = { onEditExercise(it.exercise.id.value) },
                        onAddStrength = { onNewExercise(false) },
                        onAddCardio = { onNewExercise(true) },
                        contentPadding = contentPadding,
                        archivedCount = state.exercises.count { it.archived },
                        onShowArchived = { showArchived = true },
                    )
                TrainingTab.Week ->
                    TrainingWeekContent(
                        week = state.week,
                        goals = state.goals,
                        isCurrentWeek = state.week.start == weekStart(state.today),
                        onPreviousWeek = { viewModel.shiftWeek(-1) },
                        onNextWeek = { viewModel.shiftWeek(1) },
                        onChangeGoal = onGoals,
                        contentPadding = contentPadding,
                    )
                TrainingTab.History ->
                    TrainingHistoryContent(
                        date = state.historyDate,
                        today = state.today,
                        rows = state.history,
                        onPreviousDay = { viewModel.shiftHistoryDay(-1) },
                        onNextDay = { viewModel.shiftHistoryDay(1) },
                        onPickDay = { showHistoryDatePicker = true },
                        onEdit = { row ->
                            val exercise = row.exercise ?: return@TrainingHistoryContent
                            sheet = EntrySheetTarget(exercise, row.entry.toDraft(), null)
                        },
                        onDelete = { row ->
                            viewModel.deleteEntry(row.entry)
                            scope.launch {
                                val name = row.exercise?.name ?: unknownName
                                val result =
                                    snackbar.showSnackbar(
                                        message = deletedMessage.replace("%s", name),
                                        actionLabel = undoLabel,
                                        withDismissAction = true,
                                    )
                                if (result == SnackbarResult.ActionPerformed) viewModel.restoreEntry(row.entry)
                            }
                        },
                        onAdd = {
                            if (state.exercises.any { !it.archived }) pickExerciseForDate = state.historyDate
                            else onNewExercise(false)
                        },
                        contentPadding = contentPadding,
                    )
            }
        }
    }

    val current = sheet
    if (current != null && state != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { sheet = null }, sheetState = sheetState) {
            WorkoutEntrySheetContent(
                exercise = current.exercise,
                initialDraft = current.draft,
                today = state.today,
                nowMinuteOfDay = viewModel.nowMinuteOfDay(),
                lastEntry = current.lastEntry,
                bodyWeightKg = state.latestWeightKg,
                onSave = { draft, another ->
                    viewModel.saveEntry(current.exercise, draft)
                    sheet =
                        if (another) current.copy(draft = draft.copy(entryId = null, kcalOverride = draft.kcalOverride))
                        else null
                },
                onCancel = { sheet = null },
            )
        }
    }

    val pickDate = pickExerciseForDate
    if (pickDate != null && state != null) {
        val active = state.exercises.filterNot { it.archived }
        AlertDialog(
            onDismissRequest = { pickExerciseForDate = null },
            title = { Text(stringResource(Res.string.workout_pick_exercise)) },
            text = {
                LazyColumn {
                    items(active, key = { it.id.value }) { exercise ->
                        Text(
                            text = exercise.name +
                                if (exercise.kind == WorkoutKind.Cardio) " · " + stringResource(Res.string.workout_cardio) else "",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier =
                                Modifier.fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clickable {
                                        pickExerciseForDate = null
                                        openNew(exercise, pickDate)
                                    }
                                    .padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { pickExerciseForDate = null }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }

    if (showArchived && state != null) {
        AlertDialog(
            onDismissRequest = { showArchived = false },
            title = { Text(stringResource(Res.string.workout_archived_count, state.exercises.count { it.archived })) },
            text = {
                LazyColumn {
                    items(state.exercises.filter { it.archived }, key = { it.id.value }) { exercise ->
                        Text(
                            text = exercise.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier =
                                Modifier.fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clickable {
                                        showArchived = false
                                        onEditExercise(exercise.id.value)
                                    }
                                    .padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showArchived = false }) { Text(stringResource(Res.string.action_cancel)) }
            },
        )
    }

    if (showHistoryDatePicker && state != null) {
        val pickerState =
            rememberDatePickerState(
                initialSelectedDateMillis = state.historyDate.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
                selectableDates = PastAndTodayDates(state.today),
            )
        DatePickerDialog(
            onDismissRequest = { showHistoryDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis
                            ?.let(Instant::fromEpochMilliseconds)
                            ?.toLocalDateTime(TimeZone.UTC)
                            ?.date
                            ?.let { if (it <= state.today) viewModel.setHistoryDate(it) }
                        showHistoryDatePicker = false
                    }
                ) {
                    Text(stringResource(Res.string.positive_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showHistoryDatePicker = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun todaySummary(state: TrainingUiState): String {
    if (state.todayStrengthSets == 0 && state.todayCardioMinutes == 0) {
        return stringResource(Res.string.workout_today_nothing)
    }
    val parts = mutableListOf<String>()
    if (state.todayStrengthSets > 0) {
        parts += pluralStringResource(Res.plurals.workout_sets, state.todayStrengthSets, state.todayStrengthSets)
    }
    if (state.todayCardioMinutes > 0) {
        parts += stringResource(Res.string.workout_value_minutes, state.todayCardioMinutes) + " " +
            stringResource(Res.string.workout_cardio)
    }
    parts += kcalLabel(state.todayKcal)
    return stringResource(Res.string.workout_today_summary, parts.joinToString(" · "))
}

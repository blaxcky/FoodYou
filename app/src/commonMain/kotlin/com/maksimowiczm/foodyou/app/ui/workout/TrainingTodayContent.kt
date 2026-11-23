package com.maksimowiczm.foodyou.app.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.maksimowiczm.foodyou.workout.domain.WORKOUT_SUGGESTIONS_MIN_EXERCISES
import com.maksimowiczm.foodyou.workout.domain.WorkoutKind
import com.maksimowiczm.foodyou.workout.domain.WorkoutToday
import com.maksimowiczm.foodyou.workout.domain.WorkoutTodayItem
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Cardio is told apart from strength by the theme's tertiary colors, readable in light and dark. */
internal object WorkoutColors {
    val cardioContainer: Color
        @Composable get() = MaterialTheme.colorScheme.tertiaryContainer

    val onCardioContainer: Color
        @Composable get() = MaterialTheme.colorScheme.onTertiaryContainer

    val cardioAccent: Color
        @Composable get() = MaterialTheme.colorScheme.tertiary
}

@Composable
internal fun TrainingTodayContent(
    plan: WorkoutToday,
    query: String,
    onQuery: (String) -> Unit,
    onLog: (WorkoutTodayItem) -> Unit,
    onEditExercise: (WorkoutTodayItem) -> Unit,
    onAddStrength: () -> Unit,
    onAddCardio: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    archivedCount: Int = 0,
    onShowArchived: () -> Unit = {},
) {
    if (plan.exerciseCount == 0) {
        Column(modifier.padding(contentPadding)) {
            EmptyTraining(onAddStrength, onAddCardio)
            if (archivedCount > 0) ArchivedButton(archivedCount, onShowArchived)
        }
        return
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (plan.showSearch) {
            item(key = "search") {
                TextField(
                    value = query,
                    onValueChange = onQuery,
                    placeholder = { Text(stringResource(Res.string.workout_search)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    colors =
                        TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (plan.suggestions.isNotEmpty()) {
            item(key = "next-label") { SectionLabel(stringResource(Res.string.workout_next_up)) }
            item(key = "next") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    plan.suggestions.forEachIndexed { index, item ->
                        SuggestionCard(
                            item = item,
                            highlighted = index == 0,
                            onLog = { onLog(item) },
                            onEdit = { onEditExercise(item) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (plan.suggestions.size == 1) Column(Modifier.weight(1f)) {}
                }
            }
        }
        val few = plan.exerciseCount < WORKOUT_SUGGESTIONS_MIN_EXERCISES
        item(key = "all-label") {
            SectionLabel(
                stringResource(
                    if (few) Res.string.workout_your_exercises else Res.string.workout_all_exercises,
                    plan.exerciseCount,
                )
            )
        }
        if (plan.items.isEmpty()) {
            item(key = "no-match") {
                Text(
                    text = stringResource(Res.string.workout_no_match),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        } else {
            item(key = "list") {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
                    Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)) {
                        plan.items.forEachIndexed { index, item ->
                            ExerciseRow(
                                item = item,
                                large = few,
                                onLog = { onLog(item) },
                                onEdit = { onEditExercise(item) },
                            )
                            if (index < plan.items.lastIndex) HorizontalDivider()
                        }
                    }
                }
            }
        }
        item(key = "add") {
            OutlinedButton(
                onClick = onAddStrength,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(Res.string.workout_add_exercise), modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (archivedCount > 0) {
            item(key = "archived") { ArchivedButton(archivedCount, onShowArchived) }
        }
        item(key = "hint") {
            Text(
                text = stringResource(if (few) Res.string.workout_few_hint else Res.string.workout_list_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 8.dp),
    )
}

@Composable
private fun SuggestionCard(
    item: WorkoutTodayItem,
    highlighted: Boolean,
    onLog: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardio = item.exercise.kind == WorkoutKind.Cardio
    val container =
        if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val content =
        if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Surface(shape = MaterialTheme.shapes.extraLarge, color = container, contentColor = content, modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onEdit),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(item.exercise.name, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Text(
                    text = suggestionReason(item),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (highlighted) content else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            Button(
                onClick = onLog,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp),
            ) {
                Text(
                    stringResource(if (cardio) Res.string.workout_log else Res.string.workout_add_set)
                )
            }
        }
    }
}

@Composable
private fun suggestionReason(item: WorkoutTodayItem): String {
    val parts = mutableListOf<String>()
    parts +=
        when (val days = item.daysSinceLast) {
            null -> stringResource(Res.string.workout_never_done)
            1 -> stringResource(Res.string.workout_yesterday)
            else -> stringResource(Res.string.workout_since_days, days)
        }
    item.lastEntry?.let { last ->
        entryValues(last, item.exercise).split(" · ").firstOrNull()?.takeIf { it.isNotBlank() }?.let { parts += it }
    }
    return parts.joinToString(" · ")
}

@Composable
private fun ExerciseRow(item: WorkoutTodayItem, large: Boolean, onLog: () -> Unit, onEdit: () -> Unit) {
    val cardio = item.exercise.kind == WorkoutKind.Cardio
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = if (large) 72.dp else 60.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier =
                Modifier.weight(1f).heightIn(min = 48.dp).clickable(onClick = onEdit).padding(vertical = 6.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = item.exercise.name,
                style = MaterialTheme.typography.bodyLarge,
                color =
                    if (item.doneToday) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = todayItemSubtitle(item),
                style = MaterialTheme.typography.bodySmall,
                color = if (cardio) WorkoutColors.cardioAccent else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val label =
            if (cardio) item.exercise.name + " · " + stringResource(Res.string.workout_log)
            else item.exercise.name + " · " + stringResource(Res.string.workout_add_set)
        if (item.doneToday) {
            Icon(
                Icons.Filled.Check,
                contentDescription = stringResource(Res.string.workout_done_today),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            OutlinedIconButton(onClick = onLog, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Add, contentDescription = label)
            }
        } else {
            FilledTonalIconButton(
                onClick = onLog,
                modifier = Modifier.size(48.dp),
                colors =
                    if (cardio)
                        IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = WorkoutColors.cardioContainer,
                            contentColor = WorkoutColors.onCardioContainer,
                        )
                    else IconButtonDefaults.filledTonalIconButtonColors(),
            ) {
                Icon(Icons.Filled.Add, contentDescription = label)
            }
        }
    }
}

@Composable
private fun EmptyTraining(onAddStrength: () -> Unit, onAddCardio: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 32.dp, start = 12.dp, end = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(Res.string.workout_empty_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(Res.string.workout_empty_text),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onAddStrength, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(Res.string.workout_add_strength))
        }
        OutlinedButton(onClick = onAddCardio, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(Res.string.workout_add_cardio))
        }
    }
}

@Composable
private fun ArchivedButton(count: Int, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(stringResource(Res.string.workout_archived_count, count))
    }
}

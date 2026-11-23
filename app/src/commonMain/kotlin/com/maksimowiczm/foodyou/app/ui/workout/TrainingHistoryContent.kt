package com.maksimowiczm.foodyou.app.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.maksimowiczm.foodyou.workout.domain.WorkoutEntry
import com.maksimowiczm.foodyou.workout.domain.WorkoutKind
import foodyou.app.generated.resources.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TrainingHistoryContent(
    date: LocalDate,
    today: LocalDate,
    rows: List<TrainingHistoryRow>,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onPickDay: () -> Unit,
    onEdit: (TrainingHistoryRow) -> Unit,
    onDelete: (TrainingHistoryRow) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(12.dp),
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "day") {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
                Row(modifier = Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPreviousDay) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = stringResource(Res.string.workout_previous_day),
                        )
                    }
                    TextButton(onClick = onPickDay, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text =
                                    dayLabel(date, today).let {
                                        if (date.daysUntil(today) > 1) it else "$it · ${shortDate(date)}"
                                    },
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text =
                                    pluralStringResource(Res.plurals.workout_entries, rows.size, rows.size) +
                                        " · " + kcalLabel(rows.sumOf { it.entry.energyKcal }) + " · " +
                                        stringResource(Res.string.workout_pick_day),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = onNextDay, enabled = date < today) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(Res.string.workout_next_day),
                        )
                    }
                }
            }
        }
        item(key = "entries") {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
                if (rows.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.workout_history_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Column(modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)) {
                        rows.forEachIndexed { index, row ->
                            HistoryRow(row, onEdit = { onEdit(row) }, onDelete = { onDelete(row) })
                            if (index < rows.lastIndex) HorizontalDivider()
                        }
                    }
                }
            }
        }
        item(key = "add") {
            Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(
                    when (today.toEpochDays() - date.toEpochDays()) {
                        0L -> stringResource(Res.string.workout_add_for_today)
                        1L -> stringResource(Res.string.workout_add_for_yesterday)
                        else -> stringResource(Res.string.workout_add_for_day, shortDate(date))
                    },
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        item(key = "hint") {
            Text(
                text = stringResource(Res.string.workout_history_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
private fun HistoryRow(row: TrainingHistoryRow, onEdit: () -> Unit, onDelete: () -> Unit) {
    val name = row.exercise?.name ?: stringResource(Res.string.workout_unknown_exercise)
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = minuteLabel(row.entry.minuteOfDay),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(44.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = historyDetail(row.entry, row),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onEdit, enabled = row.exercise != null) {
            Icon(Icons.Filled.Edit, contentDescription = stringResource(Res.string.workout_edit_entry, name))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(Res.string.workout_delete_entry, name))
        }
    }
}

@Composable
private fun historyDetail(entry: WorkoutEntry, row: TrainingHistoryRow): String {
    val parts = mutableListOf<String>()
    entryValues(entry, row.exercise).takeIf { it.isNotBlank() }?.let { parts += it }
    if (row.exercise?.kind == WorkoutKind.Cardio && entry.deviceKcal != null) {
        parts += stringResource(Res.string.workout_device_line) + " " + kcalLabel(entry.deviceKcal, withSuffix = false) +
            " → " + kcalLabel(entry.energyKcal)
    } else {
        parts += kcalLabel(entry.energyKcal)
    }
    return parts.joinToString(" · ")
}

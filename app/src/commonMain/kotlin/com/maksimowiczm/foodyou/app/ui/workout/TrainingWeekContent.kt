package com.maksimowiczm.foodyou.app.ui.workout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.maksimowiczm.foodyou.workout.domain.WorkoutGoals
import com.maksimowiczm.foodyou.workout.domain.WorkoutKind
import com.maksimowiczm.foodyou.workout.domain.WorkoutWeek
import com.maksimowiczm.foodyou.workout.domain.WorkoutWeekRow
import foodyou.app.generated.resources.*
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TrainingWeekContent(
    week: WorkoutWeek,
    goals: WorkoutGoals,
    isCurrentWeek: Boolean,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onChangeGoal: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(12.dp),
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "nav") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPreviousWeek) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = stringResource(Res.string.workout_previous_week),
                    )
                }
                Text(
                    text =
                        stringResource(
                            Res.string.workout_week_range,
                            shortDate(week.start),
                            shortDate(week.start.plus(6, DateTimeUnit.DAY)),
                        ),
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onNextWeek, enabled = !isCurrentWeek) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(Res.string.workout_next_week),
                    )
                }
            }
        }
        item(key = "summary") { WeekSummary(week, goals, isCurrentWeek, onChangeGoal) }
        item(key = "grid") { WeekGrid(week) }
    }
}

@Composable
private fun WeekSummary(week: WorkoutWeek, goals: WorkoutGoals, isCurrentWeek: Boolean, onChangeGoal: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 16.dp, end = 8.dp, bottom = 8.dp)) {
            Text(stringResource(Res.string.workout_strength_days), style = MaterialTheme.typography.labelLarge)
            Text(
                text =
                    stringResource(
                        Res.string.workout_days_of,
                        week.strengthDays,
                        if (isCurrentWeek) week.elapsedDays.coerceAtLeast(1) else 7,
                    ),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Medium,
            )
            if (goals.cardioGoalEnabled) {
                Text(
                    text =
                        stringResource(
                            Res.string.workout_cardio_days,
                            goals.cardioMinMinutes,
                            week.cardioDays,
                            goals.cardioDaysPerWeek,
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text =
                        if (week.strengthDays >= goals.strengthDaysPerWeek)
                            stringResource(Res.string.workout_goal_reached, goals.strengthDaysPerWeek)
                        else stringResource(Res.string.workout_goal_open, goals.strengthDaysPerWeek),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = onChangeGoal,
                    colors =
                        androidx.compose.material3.ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(Res.string.workout_change_goal))
                }
            }
        }
    }
}

@Composable
private fun WeekGrid(week: WorkoutWeek) {
    val labels = weekdayLabels()
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (week.strengthRows.isEmpty() && week.cardioRows.isEmpty()) {
                Text(
                    stringResource(Res.string.workout_week_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    labels.forEachIndexed { index, label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center,
                            fontWeight = if (index == week.elapsedDays - 1) FontWeight.Bold else null,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (week.strengthRows.isNotEmpty()) {
                    SectionLabel(stringResource(Res.string.workout_section_strength), Modifier.padding(top = 4.dp))
                    week.strengthRows.forEach { WeekRow(it, week.elapsedDays, labels) }
                }
                if (week.cardioRows.isNotEmpty()) {
                    SectionLabel(stringResource(Res.string.workout_section_cardio), Modifier.padding(top = 4.dp))
                    week.cardioRows.forEach { WeekRow(it, week.elapsedDays, labels) }
                }
            }
            if (week.notDone.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    text =
                        stringResource(
                            Res.string.workout_not_done_this_week,
                            week.notDone.joinToString(", ") { it.name },
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(Res.string.workout_week_legend),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WeekRow(row: WorkoutWeekRow, elapsedDays: Int, labels: List<String>) {
    val cardio = row.exercise.kind == WorkoutKind.Cardio
    val doneColor = if (cardio) Color(0xFFB5560F) else MaterialTheme.colorScheme.primary
    val description =
        row.exercise.name + ": " +
            row.doneOnDay.mapIndexedNotNull { index, done -> labels.getOrNull(index)?.takeIf { done } }.joinToString(", ")
    Column(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(row.exercise.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                pluralStringResource(Res.plurals.workout_days, row.dayCount, row.dayCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row.doneOnDay.forEachIndexed { index, done ->
                val shape = RoundedCornerShape(6.dp)
                val cell = Modifier.weight(1f).height(22.dp).clip(shape)
                when {
                    done ->
                        Box(
                            modifier = cell.then(Modifier.padding(0.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Surface(color = doneColor, contentColor = Color.White, modifier = Modifier.fillMaxWidth().height(22.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    index < elapsedDays ->
                        Box(cell.border(BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline), shape))
                    else -> {
                        val dashColor = MaterialTheme.colorScheme.outlineVariant
                        Box(
                            cell.drawBehind {
                                drawRoundRect(
                                    color = dashColor,
                                    cornerRadius = CornerRadius(6.dp.toPx()),
                                    style =
                                        Stroke(
                                            width = 1.5.dp.toPx(),
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                                        ),
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

package com.maksimowiczm.foodyou.app.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.workout.domain.WorkoutGoals
import com.maksimowiczm.foodyou.workout.domain.WorkoutMilestone
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutGoalsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: WorkoutGoalsViewModel = koinViewModel()
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    val milestones by viewModel.milestones.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<WorkoutMilestone?>(null) }
    var adding by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.workout_goals)) },
                navigationIcon = { ArrowBackIconButton(onBack) },
            )
        },
    ) { padding ->
        val current = goals ?: return@Scaffold
        WorkoutGoalsContent(
            goals = current,
            milestones = milestones,
            onGoals = viewModel::updateGoals,
            onEditMilestone = { editing = it },
            onAddMilestone = { adding = true },
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }

    if (adding || editing != null) {
        MilestoneDialog(
            milestone = editing,
            onDismiss = {
                adding = false
                editing = null
            },
            onSave = { title, description, achieved ->
                viewModel.saveMilestone(editing?.id, title, description, achieved)
                adding = false
                editing = null
            },
            onDelete =
                editing?.let { milestone ->
                    {
                        viewModel.deleteMilestone(milestone.id)
                        editing = null
                    }
                },
        )
    }
}

@Composable
internal fun WorkoutGoalsContent(
    goals: WorkoutGoals,
    milestones: List<WorkoutMilestone>,
    onGoals: (WorkoutGoals.() -> WorkoutGoals) -> Unit,
    onEditMilestone: (WorkoutMilestone) -> Unit,
    onAddMilestone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GoalCard {
            SectionLabel(stringResource(Res.string.workout_section_strength), Modifier.padding(start = 0.dp))
            StepperRow(
                title = stringResource(Res.string.workout_goal_strength_days),
                subtitle = stringResource(Res.string.workout_goal_strength_days_hint),
                value = goals.strengthDaysPerWeek,
                range = 1..7,
                onValue = { value -> onGoals { copy(strengthDaysPerWeek = value) } },
            )
            StepperRow(
                title = stringResource(Res.string.workout_goal_min_sets),
                value = goals.strengthDayMinSets,
                range = 1..20,
                onValue = { value -> onGoals { copy(strengthDayMinSets = value) } },
            )
        }
        GoalCard {
            SectionLabel(stringResource(Res.string.workout_section_cardio), Modifier.padding(start = 0.dp))
            CheckRow(
                title = stringResource(Res.string.workout_goal_cardio_enabled),
                subtitle = stringResource(Res.string.workout_goal_cardio_enabled_hint),
                checked = goals.cardioGoalEnabled,
                onChange = { checked -> onGoals { copy(cardioGoalEnabled = checked) } },
            )
            if (goals.cardioGoalEnabled) {
                StepperRow(
                    title = stringResource(Res.string.workout_goal_cardio_days),
                    value = goals.cardioDaysPerWeek,
                    range = 1..7,
                    onValue = { value -> onGoals { copy(cardioDaysPerWeek = value) } },
                )
                StepperRow(
                    title = stringResource(Res.string.workout_goal_cardio_minutes),
                    value = goals.cardioMinMinutes,
                    range = 1..240,
                    step = 5,
                    onValue = { value -> onGoals { copy(cardioMinMinutes = value) } },
                )
            }
            HorizontalDivider()
            Text(
                stringResource(Res.string.workout_goal_steps),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        GoalCard {
            SectionLabel(stringResource(Res.string.workout_milestones), Modifier.padding(start = 0.dp))
            milestones.forEach { milestone ->
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (milestone.achieved) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = stringResource(Res.string.workout_milestone_achieved),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp).size(20.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(milestone.title, style = MaterialTheme.typography.bodyLarge)
                        if (milestone.description.isNotBlank()) {
                            Text(
                                milestone.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = { onEditMilestone(milestone) }) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = stringResource(Res.string.workout_edit_entry, milestone.title),
                        )
                    }
                }
                HorizontalDivider()
            }
            TextButton(onClick = onAddMilestone, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(Res.string.workout_milestone_add), modifier = Modifier.padding(start = 8.dp))
            }
        }
        Text(
            stringResource(Res.string.workout_milestones_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun GoalCard(content: @Composable () -> Unit) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun StepperRow(
    title: String,
    value: Int,
    range: IntRange,
    onValue: (Int) -> Unit,
    subtitle: String? = null,
    step: Int = 1,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        OutlinedIconButton(
            onClick = { onValue((((value - 1) / step) * step).coerceIn(range)) },
            enabled = value > range.first,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(Icons.Filled.Remove, contentDescription = stringResource(Res.string.workout_less) + " " + title)
        }
        Text(
            "$value",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(40.dp),
        )
        OutlinedIconButton(
            onClick = { onValue(((value / step + 1) * step).coerceIn(range)) },
            enabled = value < range.last,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(Res.string.workout_more) + " " + title)
        }
    }
}

@Composable
private fun MilestoneDialog(
    milestone: WorkoutMilestone?,
    onDismiss: () -> Unit,
    onSave: (String, String, Boolean) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var title by remember { mutableStateOf(milestone?.title.orEmpty()) }
    var description by remember { mutableStateOf(milestone?.description.orEmpty()) }
    var achieved by remember { mutableStateOf(milestone?.achieved ?: false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (milestone == null) Res.string.workout_milestone_add else Res.string.workout_milestone_edit
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(Res.string.workout_milestone_title)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(Res.string.workout_milestone_description)) },
                )
                CheckRow(
                    title = stringResource(Res.string.workout_milestone_achieved),
                    checked = achieved,
                    onChange = { achieved = it },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, description, achieved) }, enabled = title.isNotBlank()) {
                Text(stringResource(Res.string.action_save))
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text(stringResource(Res.string.action_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
            }
        },
    )
}

package com.maksimowiczm.foodyou.app.ui.activity

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.activity.domain.entity.StepExclusionPeriod
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.app.ui.common.component.DiscardDialog
import com.maksimowiczm.foodyou.common.compose.utility.LocalDateFormatter
import com.valentinilk.shimmer.ShimmerBounds
import com.valentinilk.shimmer.rememberShimmer
import com.valentinilk.shimmer.shimmer
import foodyou.app.generated.resources.Res
import foodyou.app.generated.resources.action_add_time_period
import foodyou.app.generated.resources.action_cancel
import foodyou.app.generated.resources.action_confirm
import foodyou.app.generated.resources.action_delete
import foodyou.app.generated.resources.action_retry
import foodyou.app.generated.resources.action_save
import foodyou.app.generated.resources.description_step_exclusions
import foodyou.app.generated.resources.error_step_exclusion_invalid_period
import foodyou.app.generated.resources.error_step_exclusion_save_failed
import foodyou.app.generated.resources.error_step_exclusion_sync_failed
import foodyou.app.generated.resources.headline_step_exclusions
import foodyou.app.generated.resources.label_counted_steps
import foodyou.app.generated.resources.label_end_time
import foodyou.app.generated.resources.label_excluded_steps
import foodyou.app.generated.resources.label_start_time
import foodyou.app.generated.resources.neutral_no_step_exclusions
import foodyou.app.generated.resources.neutral_no_step_exclusions_description
import foodyou.app.generated.resources.question_discard_changes
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun StepExclusionsScreen(
    date: LocalDate,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: StepExclusionsViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(date) { viewModel.load(date) }

    StepExclusionsContent(
        state = state,
        onBack = onBack,
        onAdd = viewModel::addPeriod,
        onUpdate = viewModel::updatePeriod,
        onDelete = viewModel::deletePeriod,
        onSave = { viewModel.save(onSaved) },
        onRetry = { viewModel.retrySync(onSaved) },
        modifier = modifier,
    )
}

@Composable
internal fun StepExclusionsContent(
    state: StepExclusionsUiState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onUpdate: (index: Int, startMinute: Int, endMinute: Int) -> Unit,
    onDelete: (index: Int) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    val requestBack = { if (state.hasUnsavedChanges) showDiscardDialog = true else onBack() }
    BackHandler(enabled = state.hasUnsavedChanges, onBack = requestBack)

    if (showDiscardDialog) {
        DiscardDialog(
            onDismissRequest = { showDiscardDialog = false },
            onDiscard = onBack,
        ) {
            Text(stringResource(Res.string.question_discard_changes))
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(Res.string.headline_step_exclusions)) },
                subtitle = {
                    state.date?.let { Text(LocalDateFormatter.current.formatDate(it)) }
                },
                navigationIcon = { ArrowBackIconButton(requestBack) },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            if (!state.isLoading) {
                Surface(shadowElevation = 3.dp) {
                    Button(
                        onClick = onSave,
                        enabled = state.canSave,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(end = 12.dp).height(20.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                        Text(stringResource(Res.string.action_save))
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier.fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = stringResource(Res.string.description_step_exclusions),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            if (state.isLoading) {
                item { StepExclusionsLoading() }
            } else {
                item {
                    StepSummary(
                        countedSteps = state.countedSteps,
                        excludedSteps = state.excludedSteps,
                    )
                }

                if (state.syncFailed) {
                    item {
                        InlineError(
                            message = stringResource(Res.string.error_step_exclusion_sync_failed),
                            onRetry = onRetry,
                            retryEnabled = !state.isSaving,
                        )
                    }
                } else if (state.saveFailed) {
                    item {
                        InlineError(
                            message = stringResource(Res.string.error_step_exclusion_save_failed),
                            onRetry = onSave,
                            retryEnabled = !state.isSaving,
                        )
                    }
                }

                if (state.periods.isEmpty()) {
                    item { EmptyStepExclusions() }
                } else {
                    itemsIndexed(state.periods, key = { index, period -> "$index-${period.startMinute}-${period.endMinute}" }) {
                            index,
                            period,
                        ->
                        StepExclusionPeriodCard(
                            period = period,
                            invalid = index in state.invalidPeriodIndices,
                            enabled = !state.isSaving,
                            onUpdate = { start, end -> onUpdate(index, start, end) },
                            onDelete = { onDelete(index) },
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }

                item {
                    OutlinedButton(
                        onClick = onAdd,
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text(
                            stringResource(Res.string.action_add_time_period),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepSummary(countedSteps: Long, excludedSteps: Long) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SummaryMetric(
            label = stringResource(Res.string.label_counted_steps),
            value = countedSteps.groupDigits(),
            modifier = Modifier.weight(1f),
        )
        SummaryMetric(
            label = stringResource(Res.string.label_excluded_steps),
            value = excludedSteps.groupDigits(),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SummaryMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier.clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StepExclusionPeriodCard(
    period: StepExclusionPeriod,
    invalid: Boolean,
    enabled: Boolean,
    onUpdate: (startMinute: Int, endMinute: Int) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (invalid) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.surfaceContainerLow
            ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "${period.startMinute.toTimeText()}–${period.endMinute.toTimeText()}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                )
                IconButton(onClick = onDelete, enabled = enabled) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(Res.string.action_delete),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeField(
                    label = stringResource(Res.string.label_start_time),
                    minuteOfDay = period.startMinute,
                    enabled = enabled,
                    onMinuteSelected = { onUpdate(it, period.endMinute) },
                    modifier = Modifier.weight(1f),
                )
                TimeField(
                    label = stringResource(Res.string.label_end_time),
                    minuteOfDay = period.endMinute.coerceAtMost(23 * 60 + 59),
                    enabled = enabled,
                    onMinuteSelected = { onUpdate(period.startMinute, it) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (invalid) {
                Text(
                    stringResource(Res.string.error_step_exclusion_invalid_period),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeField(
    label: String,
    minuteOfDay: Int,
    enabled: Boolean,
    onMinuteSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        OutlinedButton(
            onClick = { showPicker = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(minuteOfDay.toTimeText())
        }
    }

    if (showPicker) {
        val pickerState =
            rememberTimePickerState(
                initialHour = minuteOfDay / 60,
                initialMinute = minuteOfDay % 60,
            )
        TimePickerDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(label, modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPicker = false
                        onMinuteSelected(pickerState.hour * 60 + pickerState.minute)
                    }
                ) {
                    Text(stringResource(Res.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        ) {
            TimePicker(pickerState)
        }
    }
}

@Composable
private fun EmptyStepExclusions() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Outlined.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(Res.string.neutral_no_step_exclusions), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(Res.string.neutral_no_step_exclusions_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InlineError(message: String, onRetry: () -> Unit, retryEnabled: Boolean) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
            TextButton(onClick = onRetry, enabled = retryEnabled, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(Res.string.action_retry))
            }
        }
    }
}

@Composable
private fun StepExclusionsLoading() {
    val shimmer = rememberShimmer(ShimmerBounds.Window)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(3) {
            Box(
                Modifier.fillMaxWidth().height(if (it == 0) 72.dp else 132.dp)
                    .shimmer(shimmer)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        RoundedCornerShape(16.dp),
                    )
            )
        }
    }
}

private fun Int.toTimeText(): String {
    if (this == 24 * 60) return "24:00"
    val hour = this / 60
    val minute = this % 60
    return hour.toString().padStart(2, '0') + ":" + minute.toString().padStart(2, '0')
}

private fun Long.groupDigits(): String = toString().reversed().chunked(3).joinToString(" ").reversed()

package com.maksimowiczm.foodyou.app.ui.home.calendar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.home.shared.FoodYouHomeCard
import com.maksimowiczm.foodyou.app.ui.home.shared.HomeState
import com.maksimowiczm.foodyou.app.ui.common.utility.LocalEnergyFormatter
import com.maksimowiczm.foodyou.app.ui.common.utility.validatedNonNegativeEnergyKcal
import com.maksimowiczm.foodyou.app.ui.home.goals.GoalsViewModel
import com.maksimowiczm.foodyou.settings.domain.entity.LockedDaySurplus
import com.maksimowiczm.foodyou.common.compose.utility.LocalDateFormatter
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.extension.now
import foodyou.app.generated.resources.*
import kotlin.time.Instant
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
internal fun CalendarCard(
    homeState: HomeState,
    viewModel: GoalsViewModel,
    modifier: Modifier = Modifier,
) {
    val dateProvider = koinInject<DateProvider>()
    val today = dateProvider.observeDate().collectAsStateWithLifecycle(LocalDate.now()).value

    val dateFormatter = LocalDateFormatter.current

    val calendarState =
        rememberCalendarState(
            namesOfDayOfWeek = remember(dateFormatter) { dateFormatter.weekDayNamesShort },
            referenceDate = today,
            selectedDate = homeState.selectedDate,
        )

    LaunchedEffect(calendarState.selectedDate) {
        val date = calendarState.selectedDate

        if (date != homeState.selectedDate) {
            homeState.selectDate(date)
        }
    }

    val lockedDaySettings by viewModel.lockedDaySettings.collectAsStateWithLifecycle()
    CalendarCard(
        calendarState = calendarState,
        lockedDays = lockedDaySettings.lockedDays,
        defaultLockedDaySurplusKcal = lockedDaySettings.defaultSurplusKcal,
        onLockDay = viewModel::lockDay,
        onUnlockDay = viewModel::unlockDay,
        modifier = modifier,
    )
}

@Composable
internal fun CalendarCard(
    calendarState: CalendarState,
    lockedDays: List<LockedDaySurplus>,
    defaultLockedDaySurplusKcal: Double,
    onLockDay: (LocalDate, Double) -> Unit,
    onUnlockDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CalendarCardDefaults.colors()
    val dateFormatter = LocalDateFormatter.current
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var lockDialogDate by remember { mutableStateOf<LocalDate?>(null) }

    if (showDatePicker) {
        CalendarCardDatePickerDialog(
            calendarState = calendarState,
            onDismissRequest = { showDatePicker = false },
        )
    }
    lockDialogDate?.let { date ->
        LockedDayDialog(
            date = date,
            currentSurplusKcal = lockedDays.firstOrNull { it.date == date }?.surplusKcal,
            defaultSurplusKcal = defaultLockedDaySurplusKcal,
            onDismiss = { lockDialogDate = null },
            onSave = {
                onLockDay(date, it)
                lockDialogDate = null
            },
            onUnlock = {
                onUnlockDay(date)
                lockDialogDate = null
            },
        )
    }

    FoodYouHomeCard(onClick = { showDatePicker = true }, modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = dateFormatter.formatMonthYear(calendarState.firstVisibleDate))

                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = stringResource(Res.string.action_show_calendar),
                )
            }
            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                CalendarCardDatePicker(
                    calendarState = calendarState,
                    lockedDays = lockedDays,
                    onLongClick = { lockDialogDate = it },
                    colors = colors,
                )
            }
        }
    }
}

@Composable
private fun CalendarCardDatePickerDialog(
    calendarState: CalendarState,
    onDismissRequest: () -> Unit,
) {
    val state = calendarState.rememberDatePickerState()

    DatePickerDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let {
                        calendarState.onDateSelect(
                            date =
                                Instant.fromEpochMilliseconds(it)
                                    .toLocalDateTime(TimeZone.UTC)
                                    .date,
                            scroll = true,
                        )
                    }
                    onDismissRequest()
                }
            ) {
                Text(text = stringResource(Res.string.positive_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(Res.string.action_cancel))
            }
        },
    ) {
        DatePicker(
            state = state,
            title = {
                Row(
                    modifier =
                        Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    DatePickerDefaults.DatePickerTitle(displayMode = state.displayMode)

                    TextButton(
                        onClick = {
                            calendarState.onDateSelect(
                                date = calendarState.referenceDate,
                                scroll = true,
                            )
                            onDismissRequest()
                        }
                    ) {
                        Text(stringResource(Res.string.action_go_to_today))
                    }
                }
            },
            // It won't fit on small screens, so we need to scroll
            modifier = Modifier.verticalScroll(rememberScrollState()),
        )
    }
}

@Composable
private fun CalendarCardDatePicker(
    calendarState: CalendarState,
    lockedDays: List<LockedDaySurplus>,
    onLongClick: (LocalDate) -> Unit,
    colors: CalendarCardColors,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current

    // Tick when user scrolls
    LaunchedEffect(calendarState) {
        combine(
                snapshotFlow { calendarState.pagerState.isScrollInProgress },
                snapshotFlow { calendarState.pagerState.currentPage },
            ) { isScrollInProgress, _ ->
                isScrollInProgress
            }
            .filter { it }
            .collectLatest {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            }
    }

    HorizontalPager(modifier = modifier.fillMaxWidth(), state = calendarState.pagerState) { page ->
        val weekStart = calendarState.weekRange.firstDateForPage(page)
        Row(modifier = Modifier.fillMaxWidth()) {
            repeat(7) { dayIndex ->
                val date = weekStart.plus(dayIndex.toLong(), DateTimeUnit.DAY)
                DatePickerRowItem(
                    calendarState = calendarState,
                    date = date,
                    colors = colors,
                    enabled = calendarState.weekRange.isSelectable(date),
                    locked = lockedDays.any { it.date == date },
                    onClick = {
                        calendarState.onDateSelect(date = date, scroll = false)
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    },
                    onLongClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick(date)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DatePickerRowItem(
    calendarState: CalendarState,
    date: LocalDate,
    colors: CalendarCardColors,
    enabled: Boolean,
    locked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val namesOfDayOfWeek = calendarState.namesOfDayOfWeek
    val referenceDate = calendarState.referenceDate
    val selectedDate = calendarState.selectedDate
    val dayOfWeek = (date.dayOfWeek.isoDayNumber - 1) % 7
    val isReferenceDate = date == referenceDate
    val isSelectedDate = date == selectedDate

    val backgroundColor by
        animateColorAsState(
            targetValue =
                when {
                    isSelectedDate -> colors.selectedDateContainerColor
                    else -> colors.containerColor
                },
            animationSpec = tween(500),
            label = "Date background color",
        )
    val color by
        animateColorAsState(
            targetValue =
                when {
                    isSelectedDate -> colors.selectedDateContentColor
                    isReferenceDate -> colors.referenceDateContentColor
                    else -> colors.contentColor
                },
            animationSpec = tween(500),
            label = "Date text color",
        )
    val referenceDateIndicatorColor by
        animateColorAsState(
            targetValue =
                if (isReferenceDate) {
                    if (isSelectedDate) {
                        colors.selectedDateContentColor
                    } else {
                        colors.referenceDateIndicatorColor
                    }
                } else {
                    Color.Transparent
                },
            animationSpec = tween(500),
            label = "Reference date indicator color",
        )
    val shape = MaterialTheme.shapes.medium
    val lockedDescription = stringResource(Res.string.locked_day_status)
    val longClickDescription = stringResource(Res.string.locked_day_long_click_description)

    Box(
        modifier =
            modifier
                .padding(horizontal = 2.dp, vertical = 4.dp)
                .height(48.dp)
                .alpha(if (enabled) 1f else 0.38f)
                .border(1.dp, referenceDateIndicatorColor, shape)
                .clip(shape)
                .semantics {
                    if (locked) stateDescription = lockedDescription
                    onLongClick(label = longClickDescription) {
                        if (enabled) onLongClick()
                        enabled
                    }
                }
                .combinedClickable(
                    enabled = enabled,
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .drawBehind {
                    drawRect(backgroundColor)
                    drawCircle(
                        color = referenceDateIndicatorColor,
                        radius = 2.dp.toPx(),
                        center = center.copy(y = size.height - 4.dp.toPx()),
                    )
                }
                .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = namesOfDayOfWeek[dayOfWeek],
                style = MaterialTheme.typography.bodyMedium,
                color = color,
                textAlign = TextAlign.Center,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = date.day.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = color,
                    textAlign = TextAlign.Center,
                )
                if (locked) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.padding(start = 2.dp).size(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun LockedCalendarDatePreview(date: LocalDate, modifier: Modifier = Modifier) {
    val calendarState =
        rememberCalendarState(
            namesOfDayOfWeek = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"),
            referenceDate = date,
            selectedDate = date,
        )
    DatePickerRowItem(
        calendarState = calendarState,
        date = date,
        colors = CalendarCardDefaults.colors(),
        enabled = true,
        locked = true,
        onClick = {},
        onLongClick = {},
        modifier = modifier.width(54.dp),
    )
}

@Composable
internal fun LockedDayDialog(
    date: LocalDate,
    currentSurplusKcal: Double?,
    defaultSurplusKcal: Double,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit,
    onUnlock: () -> Unit,
) {
    val energyFormatter = LocalEnergyFormatter.current
    val initialKcal = currentSurplusKcal ?: defaultSurplusKcal
    var input by remember(date, currentSurplusKcal, defaultSurplusKcal) {
        mutableStateOf(
            energyFormatter.fromKcal(initialKcal).let {
                if (it % 1.0 == 0.0) it.toLong().toString() else it.toString()
            }
        )
    }
    val surplusKcal = validatedNonNegativeEnergyKcal(input, energyFormatter::toKcal)
    val showError = input.isNotBlank() && surplusKcal == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (currentSurplusKcal == null) {
                        Res.string.locked_day_dialog_title_new
                    } else {
                        Res.string.locked_day_dialog_title_edit
                    }
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(LocalDateFormatter.current.formatDate(date))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.locked_day_surplus_label)) },
                    suffix = { Text(energyFormatter.suffix()) },
                    supportingText = {
                        Text(
                            stringResource(
                                if (showError) Res.string.locked_day_surplus_error
                                else Res.string.locked_day_surplus_supporting_text
                            )
                        )
                    },
                    isError = showError,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = surplusKcal != null,
                onClick = { surplusKcal?.let(onSave) },
            ) {
                Text(stringResource(Res.string.action_save))
            }
        },
        dismissButton = {
            Row {
                if (currentSurplusKcal != null) {
                    TextButton(onClick = onUnlock) {
                        Text(stringResource(Res.string.locked_day_unlock))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        },
    )
}

@Immutable
private data class CalendarCardColors(
    val containerColor: Color,
    val contentColor: Color,
    val selectedDateContainerColor: Color,
    val selectedDateContentColor: Color,
    val referenceDateContentColor: Color,
    val referenceDateIndicatorColor: Color,
)

private object CalendarCardDefaults {
    @Composable
    fun colors(
        containerColor: Color = CardDefaults.elevatedCardColors().containerColor,
        contentColor: Color = CardDefaults.elevatedCardColors().contentColor,
        selectedDateContainerColor: Color = MaterialTheme.colorScheme.primary,
        selectedDateContentColor: Color = MaterialTheme.colorScheme.onPrimary,
        referenceDateContentColor: Color = MaterialTheme.colorScheme.primary,
        referenceDateIndicatorColor: Color = MaterialTheme.colorScheme.primary,
    ) =
        CalendarCardColors(
            containerColor = containerColor,
            contentColor = contentColor,
            selectedDateContainerColor = selectedDateContainerColor,
            selectedDateContentColor = selectedDateContentColor,
            referenceDateContentColor = referenceDateContentColor,
            referenceDateIndicatorColor = referenceDateIndicatorColor,
        )
}

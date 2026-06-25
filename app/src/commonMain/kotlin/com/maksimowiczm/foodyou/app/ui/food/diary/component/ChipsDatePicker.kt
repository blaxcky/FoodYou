package com.maksimowiczm.foodyou.app.ui.food.diary.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.maksimowiczm.foodyou.common.compose.utility.LocalDateFormatter
import com.maksimowiczm.foodyou.common.extension.minus
import com.maksimowiczm.foodyou.common.extension.now
import com.maksimowiczm.foodyou.common.extension.plus
import foodyou.app.generated.resources.*
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

@Composable
fun ChipsDatePicker(state: ChipsDatePickerState, modifier: Modifier = Modifier) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis
                            ?.let(Instant::fromEpochMilliseconds)
                            ?.toLocalDateTime(TimeZone.UTC)
                            ?.date
                            ?.let(state::addAndSelect)

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

    val yesterday = state.today.minus(1.days)
    val tomorrow = state.today.plus(1.days)
    val quickDates = remember(state.today) { listOf(yesterday, state.today, tomorrow) }
    val customDateSelected = state.selectedDate !in quickDates

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        DateIconButton(
            selected = state.selectedDate == yesterday,
            onClick = { state.selectDate(yesterday) },
            contentDescription = yesterday.stringResource(state.today),
        ) {
            CalendarOffsetIcon(offset = CalendarOffset.Yesterday)
        }
        DateIconButton(
            selected = state.selectedDate == state.today,
            onClick = { state.selectDate(state.today) },
            contentDescription = state.today.stringResource(state.today),
        ) {
            Icon(imageVector = Icons.Filled.Today, contentDescription = null)
        }
        DateIconButton(
            selected = state.selectedDate == tomorrow,
            onClick = { state.selectDate(tomorrow) },
            contentDescription = tomorrow.stringResource(state.today),
        ) {
            CalendarOffsetIcon(offset = CalendarOffset.Tomorrow)
        }
        DateIconButton(
            selected = customDateSelected,
            onClick = { showDatePicker = true },
            contentDescription = stringResource(Res.string.action_choose_other_date),
        ) {
            Icon(imageVector = Icons.Filled.CalendarMonth, contentDescription = null)
        }
    }
}

@Composable
private fun DateIconButton(
    selected: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    icon: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier =
            Modifier.size(48.dp).semantics { this.contentDescription = contentDescription },
        colors =
            IconButtonDefaults.iconButtonColors(
                containerColor =
                    if (selected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        Color.Transparent
                    },
                contentColor =
                    if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            ),
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            icon()
        }
    }
}

@Composable
private fun CalendarOffsetIcon(offset: CalendarOffset) {
    Box(modifier = Modifier.size(24.dp)) {
        Icon(
            imageVector = Icons.Filled.CalendarMonth,
            contentDescription = null,
            modifier = Modifier.align(Alignment.Center).size(22.dp),
        )
        Icon(
            imageVector =
                when (offset) {
                    CalendarOffset.Yesterday -> Icons.AutoMirrored.Filled.ArrowBack
                    CalendarOffset.Tomorrow -> Icons.AutoMirrored.Filled.ArrowForward
                },
            contentDescription = null,
            modifier =
                Modifier.align(
                        when (offset) {
                            CalendarOffset.Yesterday -> Alignment.BottomStart
                            CalendarOffset.Tomorrow -> Alignment.BottomEnd
                        }
                    )
                    .offset(y = 2.dp)
                    .size(13.dp),
        )
    }
}

private enum class CalendarOffset {
    Yesterday,
    Tomorrow,
}

@Composable
private fun LocalDate.stringResource(today: LocalDate): String {
    val formatter = LocalDateFormatter.current
    val str = formatter.formatDateShort(this)

    return when (this) {
        today -> stringResource(Res.string.headline_today, str)
        today.minus(1.days) -> stringResource(Res.string.headline_yesterday, str)
        today.plus(1.days) -> stringResource(Res.string.headline_tomorrow, str)
        else -> str
    }
}

@Composable
fun rememberChipsDatePickerState(
    today: LocalDate,
    initialDates: List<LocalDate>,
    selectedDate: LocalDate = initialDates.first(),
): ChipsDatePickerState =
    rememberSaveable(today, initialDates, selectedDate, saver = ChipsDatePickerState.saver) {
        ChipsDatePickerState(
            today = today,
            initialDates = initialDates,
            initialSelectedDate = selectedDate,
        )
    }

@Stable
class ChipsDatePickerState(
    val today: LocalDate,
    initialDates: List<LocalDate>,
    initialSelectedDate: LocalDate?,
) {
    private var datesSet by mutableStateOf(initialDates.ifEmpty { setOf(LocalDate.now()) })

    val dates by derivedStateOf { datesSet.sorted() }

    var selectedDate by mutableStateOf(initialSelectedDate ?: LocalDate.now())
        private set

    fun addAndSelect(date: LocalDate) {
        datesSet = (datesSet + date)

        selectedDate = date
    }

    fun selectDate(date: LocalDate) {
        if (date in datesSet) {
            selectedDate = date
        }
    }

    companion object {
        val saver: Saver<ChipsDatePickerState, List<Long>> =
            Saver(
                save = {
                    val mutableList = mutableListOf<Long>()

                    mutableList.add(it.today.toEpochDays())
                    mutableList.add(it.selectedDate.toEpochDays())
                    mutableList.addAll(it.datesSet.map(LocalDate::toEpochDays))

                    mutableList
                },
                restore = {
                    val selectedDate = it.firstOrNull()?.let(LocalDate::fromEpochDays)
                    val today = it.getOrNull(1)?.let(LocalDate::fromEpochDays) ?: LocalDate.now()
                    val dates = it.drop(2).map(LocalDate::fromEpochDays)

                    ChipsDatePickerState(
                        today = today,
                        initialDates = dates,
                        initialSelectedDate = selectedDate,
                    )
                },
            )
    }
}

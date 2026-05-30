package com.maksimowiczm.foodyou.app.ui.home.calendar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.home.shared.FoodYouHomeCard
import com.maksimowiczm.foodyou.app.ui.home.shared.HomeState
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
internal fun CalendarCard(homeState: HomeState, modifier: Modifier = Modifier) {
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

    CalendarCard(calendarState = calendarState, modifier = modifier)
}

@Composable
private fun CalendarCard(
    calendarState: CalendarState,
    modifier: Modifier = Modifier,
    colors: CalendarCardColors = CalendarCardDefaults.colors(),
) {
    val dateFormatter = LocalDateFormatter.current
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    if (showDatePicker) {
        CalendarCardDatePickerDialog(
            calendarState = calendarState,
            onDismissRequest = { showDatePicker = false },
        )
    }

    FoodYouHomeCard(onClick = { showDatePicker = true }, modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text =
                        dateFormatter.formatMonthYear(
                            calendarState.firstVisibleDate ?: calendarState.selectedDate
                        )
                )

                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = stringResource(Res.string.action_show_calendar),
                )
            }
            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                CalendarCardDatePicker(calendarState = calendarState, colors = colors)
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
    colors: CalendarCardColors,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current

    // Tick when user scrolls
    LaunchedEffect(calendarState) {
        combine(
                snapshotFlow { calendarState.lazyListState.isScrollInProgress },
                snapshotFlow { calendarState.lazyListState.firstVisibleItemIndex },
            ) { isScrollInProgress, _ ->
                isScrollInProgress
            }
            .filter { it }
            .collectLatest {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            }
    }

    LazyRow(modifier = modifier, state = calendarState.lazyListState) {
        items(count = calendarState.lazyListCount, key = { it }, contentType = { "date" }) {
            val date = calendarState.zeroDate.plus(it.toLong(), DateTimeUnit.DAY)
            DatePickerRowItem(
                calendarState = calendarState,
                date = date,
                colors = colors,
                onClick = {
                    calendarState.onDateSelect(date = date, scroll = false)
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                },
            )
        }
    }
}

@Composable
private fun DatePickerRowItem(
    calendarState: CalendarState,
    date: LocalDate,
    colors: CalendarCardColors,
    onClick: () -> Unit,
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

    Box(
        modifier =
            modifier
                .padding(4.dp)
                .size(48.dp)
                .border(1.dp, referenceDateIndicatorColor, shape)
                .clip(shape)
                .clickable { onClick() }
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
            Text(
                text = date.day.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = color,
                textAlign = TextAlign.Center,
            )
        }
    }
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

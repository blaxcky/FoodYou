package com.maksimowiczm.foodyou.app.ui.workout

import androidx.compose.runtime.Composable
import com.maksimowiczm.foodyou.app.ui.common.utility.LocalEnergyFormatter
import com.maksimowiczm.foodyou.workout.domain.StrengthMeasure
import com.maksimowiczm.foodyou.workout.domain.WorkoutEntry
import com.maksimowiczm.foodyou.workout.domain.WorkoutExercise
import com.maksimowiczm.foodyou.workout.domain.WorkoutKind
import com.maksimowiczm.foodyou.workout.domain.WorkoutTodayItem
import foodyou.app.generated.resources.*
import kotlin.math.roundToInt
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.number
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

internal fun minuteLabel(minuteOfDay: Int): String {
    val minute = minuteOfDay.coerceIn(0, 24 * 60)
    return "${(minute / 60).toString().padStart(2, '0')}:${(minute % 60).toString().padStart(2, '0')}"
}

internal fun Double.formatAmount(separator: String): String {
    val rounded = (this * 10).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toLong().toString()
    else rounded.toString().replace(".", separator)
}

@Composable
internal fun decimalSeparator(): String = stringResource(Res.string.daily_goals_decimal_separator)

@Composable
internal fun kcalLabel(kcal: Double): String =
    LocalEnergyFormatter.current.formatEnergy(kcal.roundToInt())

@Composable
internal fun weekdayLabels(): List<String> =
    stringResource(Res.string.workout_weekdays).split(',').map { it.trim() }

@Composable
internal fun dayLabel(date: LocalDate, today: LocalDate): String =
    when (date.daysUntil(today)) {
        0 -> stringResource(Res.string.workout_today_label)
        1 -> stringResource(Res.string.workout_yesterday_label)
        else -> shortDate(date)
    }

@Composable
internal fun shortDate(date: LocalDate): String {
    val weekday = weekdayLabels().getOrElse(date.dayOfWeek.ordinal) { "" }
    return "$weekday, ${date.day}.${date.month.number}."
}

/** "8 kg · 10 reps", "25 s", "level 4 · 10 reps" or "20 min" for one entry. */
@Composable
internal fun entryValues(entry: WorkoutEntry, exercise: WorkoutExercise?): String {
    val separator = decimalSeparator()
    val parts = mutableListOf<String>()
    if (exercise?.kind == WorkoutKind.Cardio || entry.durationMinutes != null) {
        entry.durationMinutes?.let { parts += stringResource(Res.string.workout_value_minutes, it) }
        entry.distanceMeters?.let { parts += stringResource(Res.string.workout_value_meters, it) }
    } else {
        entry.level?.let { parts += stringResource(Res.string.workout_value_level, it) }
        entry.weightKg?.let {
            parts += stringResource(Res.string.workout_value_kg, it.formatAmount(separator))
        }
        entry.seconds?.let { parts += stringResource(Res.string.workout_value_seconds, it) }
        entry.reps
            ?.takeIf { exercise?.measure != StrengthMeasure.Seconds }
            ?.let { parts += stringResource(Res.string.workout_value_reps, it) }
    }
    return parts.joinToString(" · ")
}

/** Subtitle of an exercise row on the start screen. */
@Composable
internal fun todayItemSubtitle(item: WorkoutTodayItem): String {
    val parts = mutableListOf<String>()
    val last = item.lastEntry
    when {
        item.doneToday -> {
            parts += stringResource(
                Res.string.workout_today_at,
                minuteLabel(item.entriesToday.first().minuteOfDay),
            )
            if (item.exercise.kind == WorkoutKind.Strength) {
                parts += pluralStringResource(
                    Res.plurals.workout_sets,
                    item.entriesToday.size,
                    item.entriesToday.size,
                )
            }
        }
        last == null -> parts += stringResource(Res.string.workout_never_done)
        item.daysSinceLast == 1 -> parts += stringResource(Res.string.workout_yesterday)
        else -> parts += stringResource(Res.string.workout_days_ago, item.daysSinceLast ?: 0)
    }
    val lastValues = (item.entriesToday.lastOrNull() ?: last)?.let { entryValues(it, item.exercise) }
    if (!lastValues.isNullOrBlank()) parts += lastValues
    if (item.exercise.kind == WorkoutKind.Cardio) parts += stringResource(Res.string.workout_cardio)
    else if (item.exercise.helpsWith.isNotBlank() && !item.doneToday) {
        parts += stringResource(Res.string.workout_helps_with_value, item.exercise.helpsWith)
    }
    return parts.joinToString(" · ")
}

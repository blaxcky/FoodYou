package com.maksimowiczm.foodyou.app.ui.weight

import com.maksimowiczm.foodyou.weight.domain.entity.DailyWeightEntry
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

internal enum class WeightChartRange(val shortLabel: String, val label: String, val months: Int?) {
    OneMonth("1M", "1 Monat", 1),
    ThreeMonths("3M", "3 Monate", 3),
    SixMonths("6M", "6 Monate", 6),
    OneYear("1J", "1 Jahr", 12),
    All("Alle", "Gesamter Zeitraum", null),
}

internal data class WeightChartSample(val epochDay: Double, val weightKg: Double)

internal data class WeightChartModel(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val minWeightKg: Double,
    val maxWeightKg: Double,
    /** Grid line values from top to bottom. */
    val gridWeightsKg: List<Double>,
    val measurements: List<DailyWeightEntry>,
    /** Smoothed weight at each measurement in range; empty when there is nothing to connect. */
    val trend: List<WeightChartSample>,
    val showsTarget: Boolean,
    val ticks: List<LocalDate>,
) {
    fun xFraction(epochDay: Double): Float {
        val start = startDate.toEpochDays().toDouble()
        val span = (endDate.toEpochDays() - startDate.toEpochDays()).coerceAtLeast(1)
        return ((epochDay - start) / span).toFloat()
    }

    fun yFraction(weightKg: Double): Float =
        ((maxWeightKg - weightKg) / (maxWeightKg - minWeightKg)).toFloat()
}

/** About a week of neighbouring measurements shapes each trend value, independent of the range. */
private const val TrendSigmaDays = 7.0
private val GridSteps = listOf(0.5, 1.0, 2.0, 2.5, 5.0, 10.0, 20.0, 25.0, 50.0)

/**
 * Builds the chart for [range] ending at [today]. Smoothing uses all [entries], also those before
 * the range start, so the curve does not restart at the left edge.
 */
internal fun buildWeightChartModel(
    entries: List<DailyWeightEntry>,
    range: WeightChartRange,
    today: LocalDate,
    targetWeightKg: Double?,
): WeightChartModel? {
    val sorted = entries.sortedBy { it.date }
    val rangeStart = range.months?.let { today.minus(it, DateTimeUnit.MONTH) }
    val visible = if (rangeStart == null) sorted else sorted.filter { it.date >= rangeStart }
    if (visible.isEmpty()) return null

    val endDate = maxOf(today, visible.last().date)
    val firstDate = if (rangeStart == null) visible.first().date else maxOf(rangeStart, visible.first().date)
    val startDate = if (firstDate < endDate) firstDate else endDate.minus(7, DateTimeUnit.DAY)
    val trend =
        if (visible.size < 2) {
            emptyList()
        } else {
            visible.mapNotNull { entry ->
                val day = entry.date.toEpochDays().toDouble()
                smoothedWeightAt(sorted, day, TrendSigmaDays)?.let { WeightChartSample(day, it) }
            }
        }

    val values = visible.map { it.weightKg } + trend.map { it.weightKg }
    var low = values.min()
    var high = values.max()
    val showsTarget =
        targetWeightKg != null &&
            targetWeightKg >= low - max(1.0, (high - low) / 2) &&
            targetWeightKg <= high + max(1.0, (high - low) / 2)
    if (showsTarget) {
        low = minOf(low, targetWeightKg)
        high = maxOf(high, targetWeightKg)
    }
    if (high - low < 2.0) {
        val center = (high + low) / 2
        low = center - 1.0
        high = center + 1.0
    }
    val step = GridSteps.firstOrNull { (high - low) / it <= 4.0 } ?: GridSteps.last()
    val minWeight = floor(low / step) * step
    val maxWeight = ceil(high / step) * step
    val lineCount = ((maxWeight - minWeight) / step).roundToInt() + 1
    val grid = List(lineCount) { index -> maxWeight - step * index }

    return WeightChartModel(
        startDate = startDate,
        endDate = endDate,
        minWeightKg = minWeight,
        maxWeightKg = maxWeight,
        gridWeightsKg = grid,
        measurements = visible,
        trend = trend,
        showsTarget = showsTarget,
        ticks = chartTicks(startDate, endDate),
    )
}

/**
 * Gaussian kernel smoothing over time. Unlike a moving average over entries, it handles irregular
 * gaps: a single outlier next to normal days is damped, isolated measurements keep their weight.
 */
internal fun smoothedWeightAt(
    entries: List<DailyWeightEntry>,
    epochDay: Double,
    sigmaDays: Double,
): Double? {
    val cutoff = sigmaDays * 4
    var weightSum = 0.0
    var valueSum = 0.0
    entries.forEach { entry ->
        val distance = entry.date.toEpochDays() - epochDay
        if (distance > -cutoff && distance < cutoff) {
            val weight = exp(-(distance * distance) / (2 * sigmaDays * sigmaDays))
            weightSum += weight
            valueSum += weight * entry.weightKg
        }
    }
    return if (weightSum > 1e-6) valueSum / weightSum else null
}

private fun chartTicks(startDate: LocalDate, endDate: LocalDate): List<LocalDate> {
    val days = (startDate.toEpochDays() + 1..endDate.toEpochDays()).map { LocalDate.fromEpochDays(it) }
    if (days.size <= 45) return days.filter { it.dayOfWeek == DayOfWeek.MONDAY }
    val monthStarts = days.filter { it.day == 1 }
    val stride = ceil(monthStarts.size / 6.0).toInt().coerceAtLeast(1)
    return monthStarts.filterIndexed { index, _ -> index % stride == 0 }
}

package com.maksimowiczm.foodyou.activity.domain.usecase

import com.maksimowiczm.foodyou.activity.domain.entity.StepExclusionPeriod

fun isValidStepExclusionPeriod(period: StepExclusionPeriod): Boolean =
    period.startMinute in 0..<MINUTES_PER_DAY &&
        period.endMinute in 1..MINUTES_PER_DAY &&
        period.startMinute < period.endMinute

fun mergeStepExclusionPeriods(periods: List<StepExclusionPeriod>): List<StepExclusionPeriod> {
    require(periods.all(::isValidStepExclusionPeriod)) { "Invalid step exclusion period" }
    if (periods.isEmpty()) return emptyList()

    val date = periods.first().date
    require(periods.all { it.date == date }) { "Step exclusion periods must share a date" }

    val sorted = periods.sortedWith(compareBy(StepExclusionPeriod::startMinute, StepExclusionPeriod::endMinute))
    return buildList {
        sorted.forEach { period ->
            val previous = lastOrNull()
            if (previous == null || period.startMinute > previous.endMinute) {
                add(period)
            } else if (period.endMinute > previous.endMinute) {
                this[lastIndex] = previous.copy(endMinute = period.endMinute)
            }
        }
    }
}

fun boundedExcludedSteps(rawSteps: Long, intervalSteps: Iterable<Long>): Long =
    intervalSteps.sumOf { it.coerceAtLeast(0) }.coerceAtMost(rawSteps.coerceAtLeast(0))

const val MINUTES_PER_DAY = 24 * 60

package com.maksimowiczm.foodyou.weight.domain.usecase

import com.maksimowiczm.foodyou.weight.domain.entity.DailyWeightEntry

fun latestWeightEntryPerDay(entries: List<DailyWeightEntry>): List<DailyWeightEntry> =
    entries.groupBy { it.date }.values.mapNotNull { dayEntries ->
        dayEntries.maxByOrNull { it.measuredAt }
    }

fun resolveWeightConflict(local: DailyWeightEntry?, incoming: DailyWeightEntry): DailyWeightEntry =
    if (local == null || incoming.measuredAt >= local.measuredAt) incoming else local

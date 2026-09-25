package com.maksimowiczm.foodyou.weight.domain.usecase

import com.maksimowiczm.foodyou.weight.domain.entity.DailyWeightEntry
import kotlinx.datetime.LocalDate

fun foodYouHealthConnectDate(
    originPackage: String,
    ownPackage: String,
    clientRecordId: String?,
): LocalDate? {
    if (originPackage != ownPackage || clientRecordId?.startsWith("foodyou-weight-") != true) {
        return null
    }
    val epochDay = clientRecordId.removePrefix("foodyou-weight-").toLongOrNull() ?: return null
    return try {
        LocalDate.fromEpochDays(epochDay)
    } catch (_: IllegalArgumentException) {
        null
    }
}

fun latestWeightEntryPerDay(entries: List<DailyWeightEntry>): List<DailyWeightEntry> =
    entries.filterNot { it.isHidden }.groupBy { it.date }.values.mapNotNull { dayEntries ->
        dayEntries.maxWithOrNull(compareBy<DailyWeightEntry> { it.measuredAt }.thenBy { it.id })
    }

fun isExportableFoodYouWeightEntry(
    entry: DailyWeightEntry,
    ownPackage: String,
): Boolean =
    entry.isFoodYouRecord &&
        entry.id == "local:${entry.date.toEpochDays()}" &&
        (entry.sourcePackageName == null || entry.sourcePackageName == ownPackage)

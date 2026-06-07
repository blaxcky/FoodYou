package com.maksimowiczm.foodyou.weight.domain.usecase

import com.maksimowiczm.foodyou.weight.domain.entity.DailyWeightEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

class WeightSyncRulesTest {
    @Test
    fun multipleHealthConnectValuesOnSameDayChooseNewest() {
        val date = LocalDate(2026, 6, 7)
        val entries =
            listOf(
                weight(date, 81.2, "2026-06-07T06:00:00Z"),
                weight(date, 80.9, "2026-06-07T20:00:00Z"),
            )

        val result = latestWeightEntryPerDay(entries)

        assertEquals(1, result.size)
        assertEquals(80.9, result.single().weightKg)
    }

    @Test
    fun newestTimestampWinsConflict() {
        val date = LocalDate(2026, 6, 7)
        val local = weight(date, 81.2, "2026-06-07T20:00:00Z")
        val olderIncoming = weight(date, 80.9, "2026-06-07T06:00:00Z")
        val newerIncoming = weight(date, 80.7, "2026-06-07T21:00:00Z")

        assertEquals(local, resolveWeightConflict(local, olderIncoming))
        assertEquals(newerIncoming, resolveWeightConflict(local, newerIncoming))
    }
}

private fun weight(date: LocalDate, kg: Double, measuredAt: String) =
    DailyWeightEntry(
        date = date,
        weightKg = kg,
        measuredAt = Instant.parse(measuredAt),
        healthConnectRecordId = null,
        isFoodYouRecord = false,
    )

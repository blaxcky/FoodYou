package com.maksimowiczm.foodyou.weight.domain.usecase

import com.maksimowiczm.foodyou.weight.domain.entity.DailyWeightEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
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
    fun hiddenLatestMeasurementFallsBackToEarlierSameDay() {
        val date = LocalDate(2026, 6, 7)
        val earlier = weight(date, 81.2, "2026-06-07T06:00:00Z")
        val hidden = weight(date, 80.9, "2026-06-07T20:00:00Z").copy(isHidden = true)

        assertEquals(earlier, latestWeightEntryPerDay(listOf(earlier, hidden)).single())
    }

    @Test
    fun onlyOwnPackageAndClientIdAreRecognizedAsFoodYouEcho() {
        val date = LocalDate(2026, 6, 7)
        val id = "foodyou-weight-${date.toEpochDays()}"
        assertEquals(date, foodYouHealthConnectDate("com.foodyou", "com.foodyou", id))
        assertEquals(null, foodYouHealthConnectDate("com.fitbit", "com.foodyou", id))
        assertEquals(null, foodYouHealthConnectDate("com.foodyou", "com.foodyou", "other"))
    }

    @Test
    fun onlyCanonicalFoodYouEntriesAreExportable() {
        val date = LocalDate(2026, 6, 7)
        val imported = weight(date, 80.0, "2026-06-07T08:00:00Z")
        val canonical =
            imported.copy(
                id = "local:${date.toEpochDays()}",
                healthConnectRecordId = "own-id",
                isFoodYouRecord = true,
            )

        assertTrue(isExportableFoodYouWeightEntry(canonical, "com.foodyou"))
        assertTrue(
            isExportableFoodYouWeightEntry(
                canonical.copy(sourcePackageName = "com.foodyou"),
                "com.foodyou",
            )
        )
        assertFalse(isExportableFoodYouWeightEntry(imported, "com.foodyou"))
        assertFalse(
            isExportableFoodYouWeightEntry(canonical.copy(id = "hc:own-id"), "com.foodyou")
        )
        assertFalse(
            isExportableFoodYouWeightEntry(
                canonical.copy(sourcePackageName = "com.fitbit"),
                "com.foodyou",
            )
        )
        assertFalse(
            isExportableFoodYouWeightEntry(
                canonical.copy(isFoodYouRecord = false),
                "com.foodyou",
            )
        )
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

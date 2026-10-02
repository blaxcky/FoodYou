package com.maksimowiczm.foodyou.app.ui.weight

import com.maksimowiczm.foodyou.weight.domain.entity.DailyWeightEntry
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

class WeightChartModelTest {
    private val today = LocalDate(2026, 10, 2)

    @Test
    fun constantWeightStaysConstant() {
        val entries = (1..20).map { entry(LocalDate(2026, 9, it), 100.0) }

        val model = assertNotNull(buildWeightChartModel(entries, WeightChartRange.OneMonth, today, null))

        assertTrue(model.trend.size > 2)
        assertTrue(model.trend.all { abs(it.weightKg - 100.0) < 1e-9 })
    }

    @Test
    fun singleDayOutlierIsDamped() {
        val entries =
            (1..20).map { day -> entry(LocalDate(2026, 9, day), if (day == 10) 97.0 else 100.0) }
        val outlierDay = LocalDate(2026, 9, 10).toEpochDays().toDouble()

        val smoothed = assertNotNull(smoothedWeightAt(entries, outlierDay, sigmaDays = 2.0))

        assertTrue(smoothed > 99.0, "smoothed value was $smoothed")
    }

    @Test
    fun rangeLimitsMeasurementsButSmoothingUsesEarlierHistory() {
        val entries =
            listOf(
                entry(LocalDate(2026, 6, 1), 90.0),
                entry(LocalDate(2026, 9, 3), 100.0),
                entry(LocalDate(2026, 9, 4), 100.0),
            )

        val model = assertNotNull(buildWeightChartModel(entries, WeightChartRange.OneMonth, today, null))

        assertEquals(listOf(LocalDate(2026, 9, 3), LocalDate(2026, 9, 4)), model.measurements.map { it.date })
        assertEquals(LocalDate(2026, 9, 3), model.startDate)
        assertEquals(today, model.endDate)
    }

    @Test
    fun allRangeStartsAtFirstMeasurement() {
        val entries = listOf(entry(LocalDate(2026, 4, 1), 98.0), entry(LocalDate(2026, 9, 28), 102.4))

        val model = assertNotNull(buildWeightChartModel(entries, WeightChartRange.All, today, null))

        assertEquals(LocalDate(2026, 4, 1), model.startDate)
        assertEquals(0f, model.xFraction(LocalDate(2026, 4, 1).toEpochDays().toDouble()))
        assertEquals(1f, model.xFraction(today.toEpochDays().toDouble()))
    }

    @Test
    fun axisFitsDataAndOmitsDistantTarget() {
        val entries =
            listOf(
                entry(LocalDate(2026, 9, 3), 99.4),
                entry(LocalDate(2026, 9, 10), 105.6),
                entry(LocalDate(2026, 9, 20), 102.4),
            )

        val model = assertNotNull(buildWeightChartModel(entries, WeightChartRange.OneMonth, today, 85.0))

        assertFalse(model.showsTarget)
        assertTrue(model.minWeightKg <= 99.4 && model.minWeightKg > 95.0)
        assertTrue(model.maxWeightKg >= 105.6 && model.maxWeightKg < 110.0)
        assertTrue(model.gridWeightsKg.size in 3..5)
        assertEquals(model.maxWeightKg, model.gridWeightsKg.first())
        assertEquals(model.minWeightKg, model.gridWeightsKg.last())
    }

    @Test
    fun nearbyTargetIsIncludedInAxis() {
        val entries = listOf(entry(LocalDate(2026, 9, 1), 92.0), entry(LocalDate(2026, 9, 20), 91.0))

        val model = assertNotNull(buildWeightChartModel(entries, WeightChartRange.OneMonth, today, 90.5))

        assertTrue(model.showsTarget)
        assertTrue(model.minWeightKg <= 90.5)
    }

    @Test
    fun singleMeasurementHasNoCurveButValidAxis() {
        val model =
            assertNotNull(
                buildWeightChartModel(listOf(entry(today, 80.0)), WeightChartRange.OneYear, today, null)
            )

        assertEquals(emptyList(), model.trend)
        assertTrue(model.minWeightKg < 80.0 && model.maxWeightKg > 80.0)
        assertTrue(model.startDate < model.endDate)
    }

    @Test
    fun emptyRangeHasNoModel() {
        val entries = listOf(entry(LocalDate(2026, 3, 1), 100.0))

        assertNull(buildWeightChartModel(entries, WeightChartRange.OneMonth, today, null))
    }

    @Test
    fun ticksUseMondaysForShortRangesAndMonthsOtherwise() {
        val entries = listOf(entry(LocalDate(2026, 9, 3), 100.0), entry(LocalDate(2025, 10, 5), 100.0))

        val month = assertNotNull(buildWeightChartModel(entries, WeightChartRange.OneMonth, today, null))
        val year = assertNotNull(buildWeightChartModel(entries, WeightChartRange.OneYear, today, null))

        assertEquals(
            listOf(7, 14, 21, 28).map { LocalDate(2026, 9, it) },
            month.ticks,
        )
        assertTrue(year.ticks.all { it.day == 1 })
        assertTrue(year.ticks.size in 4..6)
    }

    private fun entry(date: LocalDate, weightKg: Double) =
        DailyWeightEntry(
            date = date,
            weightKg = weightKg,
            measuredAt = Instant.parse("${date}T06:00:00Z"),
            healthConnectRecordId = null,
            isFoodYouRecord = true,
        )
}

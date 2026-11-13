package com.maksimowiczm.foodyou.app.widget.gauge

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maksimowiczm.foodyou.app.ui.home.goals.gaugeMarkerGap
import com.maksimowiczm.foodyou.app.widget.CalorieWidgetModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class CalorieGaugeTest {
    @Test
    fun goalsBelowTheNormalGoalSitOnTheRingInOrder() {
        val gauge = calorieGauge(model(optimizedGoalKcal = 1684, dietGoalKcal = 721))

        assertEquals(
            listOf(CalorieGaugeZoneKind.Diet, CalorieGaugeZoneKind.Optimized),
            gauge.zones.map { it.kind },
        )
        assertEquals(721f / 2100f, gauge.zones[0].fraction)
        assertEquals(-368, gauge.zones[0].remainingKcal)
        assertTrue(gauge.zones[0].exceeded)
        assertEquals(595, gauge.zones[1].remainingKcal)
        assertEquals(1011, gauge.remainingKcal)
        assertFalse(gauge.exceeded)
    }

    @Test
    fun aGoalCloseToTheNormalGoalMovesBackAndTheFillFollows() {
        val gauge = calorieGauge(model(netKcal = 2090, optimizedGoalKcal = 2090))

        val zone = gauge.zones.single()
        assertEquals(2090f / 2100f, zone.goalFraction)
        assertEquals(1f - gaugeMarkerGap(20), zone.fraction)
        assertEquals(zone.fraction, gauge.progress, 1e-6f)
    }

    @Test
    fun theMarkerGapComesFromTheModel() {
        val model = model(optimizedGoalKcal = 2090).copy(markerGapDegrees = 0)

        assertEquals(2090f / 2100f, calorieGauge(model).zones.single().fraction)
    }

    @Test
    fun aGoalAtTheNormalGoalAndMissingGoalsAreLeftOut() {
        val gauge = calorieGauge(model(optimizedGoalKcal = 2100, dietGoalKcal = null))

        assertEquals(emptyList(), gauge.zones)
    }

    @Test
    fun goalsAboveTheNormalGoalSitPastTheRingsEndUnspread() {
        val gauge = calorieGauge(model(optimizedGoalKcal = 2760, dietGoalKcal = 2260))

        assertEquals(emptyList(), gauge.onRingZones)
        assertEquals(emptyList(), gauge.capGoals.atStart)
        assertEquals(
            listOf(CalorieGaugeZoneKind.Diet, CalorieGaugeZoneKind.Optimized),
            gauge.capGoals.atEnd.map { it.kind },
        )
        assertEquals(2260f / 2100f, gauge.zones[0].fraction)
        assertEquals(1171, gauge.zones[0].remainingKcal)
    }

    @Test
    fun aUsedUpGoalSitsBeforeTheRingsStartAndIsExceeded() {
        val gauge = calorieGauge(model(optimizedGoalKcal = 1935, dietGoalKcal = -240))

        assertEquals(listOf(CalorieGaugeZoneKind.Optimized), gauge.onRingZones.map { it.kind })
        val diet = gauge.capGoals.atStart.single()
        assertEquals(CalorieGaugeZoneKind.Diet, diet.kind)
        assertEquals(emptyList(), gauge.capGoals.atEnd)
        assertTrue(diet.exceeded)
    }

    @Test
    fun theLegendTakesARowUnderTheRing() {
        val size = DpSize(320.dp, 180.dp)
        val plain = calorieGaugeLayoutSpec(size)
        val withLegend = calorieGaugeLayoutSpec(size, showLegend = true)

        assertTrue(withLegend.legendHeight > 0.dp)
        assertEquals(0.dp, plain.legendHeight)
        assertTrue(withLegend.diameter < plain.diameter, "${withLegend.diameter}")
        assertTrue(
            withLegend.gaugeHeight <= withLegend.rowHeight + 0.01.dp,
            "${withLegend.gaugeHeight}",
        )
    }

    @Test
    fun noGoalMeansNoZones() {
        val gauge = calorieGauge(model(normalGoalKcal = 0, optimizedGoalKcal = 1684, dietGoalKcal = 721))

        assertEquals(emptyList(), gauge.zones)
    }

    @Test
    fun overTheNormalGoalTheRingOverflows() {
        val gauge = calorieGauge(model(netKcal = 2400))

        assertTrue(gauge.exceeded)
        assertTrue(gauge.overflow > 0f)
    }

    @Test
    fun typicalCellHasHeaderAndLargeRing() {
        val spec = calorieGaugeLayoutSpec(DpSize(320.dp, 180.dp))
        val withLegend = calorieGaugeLayoutSpec(DpSize(320.dp, 180.dp), showLegend = true)

        assertTrue(spec.showHeader)
        assertEquals(10.dp, spec.stroke)
        assertEquals(22.sp, spec.centerValueTextSize)
        assertTrue(spec.diameter in 130.dp..140.dp, "${spec.diameter}")
        assertTrue(spec.gaugeHeight <= spec.rowHeight + 0.01.dp, "${spec.gaugeHeight}")
        // The legend row takes room from the ring, but not its line or text sizes.
        assertEquals(10.dp, withLegend.stroke)
        assertTrue(withLegend.diameter in 105.dp..115.dp, "${withLegend.diameter}")
    }

    @Test
    fun widerCellsGiveTheRingMostOfTheWidth() {
        val spec = calorieGaugeLayoutSpec(DpSize(330.dp, 200.dp))

        assertTrue(spec.diameter > 145.dp, "${spec.diameter}")
        assertEquals(11.sp, spec.centerLabelTextSize)
    }

    @Test
    fun flatWidgetDropsHeader() {
        val spec = calorieGaugeLayoutSpec(DpSize(320.dp, 130.dp))

        assertFalse(spec.showHeader)
        assertTrue(spec.gaugeHeight <= 106.5.dp, "${spec.gaugeHeight}")

        val min = calorieGaugeLayoutSpec(DpSize(250.dp, 110.dp))
        assertFalse(min.showHeader)
        assertEquals(8.dp, min.stroke)
        assertEquals(18.sp, min.centerValueTextSize)
        assertTrue(min.gaugeHeight <= 86.5.dp, "${min.gaugeHeight}")
    }

    @Test
    fun actionButtonsSitWellBelowTheSideValues() {
        val cell = calorieGaugeLayoutSpec(DpSize(320.dp, 180.dp))
        assertEquals(36.dp, cell.actionButtonSize)
        assertEquals(16.dp, cell.actionDrop)

        val flat = calorieGaugeLayoutSpec(DpSize(320.dp, 130.dp))
        assertEquals(32.dp, flat.actionButtonSize)
        assertEquals(12.dp, flat.actionDrop)
    }

    @Test
    fun actionButtonsFitTheRowAtEverySize() {
        listOf(
                DpSize(250.dp, 110.dp) to 1f,
                DpSize(250.dp, 110.dp) to 1.3f,
                DpSize(320.dp, 130.dp) to 1f,
                DpSize(320.dp, 180.dp) to 1.3f,
                DpSize(330.dp, 200.dp) to 1f,
            )
            .forEach { (size, fontScale) ->
                val spec = calorieGaugeLayoutSpec(size, fontScale)
                assertTrue(spec.actionDrop >= 0.dp, "$size: ${spec.actionDrop}")
                assertTrue(
                    spec.sideMetricHeight <= spec.rowHeight || spec.actionDrop == 0.dp,
                    "$size @ $fontScale: ${spec.sideMetricHeight} > ${spec.rowHeight}",
                )
            }
    }

    private fun model(
        netKcal: Int = 1089,
        normalGoalKcal: Int = 2100,
        optimizedGoalKcal: Int? = null,
        dietGoalKcal: Int? = null,
    ) =
        CalorieWidgetModel(
            date = LocalDate(2026, 11, 5),
            countedSteps = 0,
            eatenKcal = netKcal,
            burnedKcal = 0,
            netKcal = netKcal,
            normalGoalKcal = normalGoalKcal,
            normalLeftKcal = normalGoalKcal - netKcal,
            optimizedGoalKcal = optimizedGoalKcal,
            optimizedLeftKcal = optimizedGoalKcal?.let { it - netKcal },
            dietGoalConfigured = dietGoalKcal != null,
            dietGoalKcal = dietGoalKcal,
            dietLeftKcal = dietGoalKcal?.let { it - netKcal },
        )
}

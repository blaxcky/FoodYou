package com.maksimowiczm.foodyou.app.widget.gauge

import com.maksimowiczm.foodyou.app.ui.home.goals.gaugeRingPosition
import com.maksimowiczm.foodyou.app.ui.home.goals.spreadGaugeMarkers
import com.maksimowiczm.foodyou.app.widget.CalorieWidgetModel
import com.maksimowiczm.foodyou.app.widget.calorieWidgetProgress

internal enum class CalorieGaugeZoneKind {
    Optimized,
    Diet,
}

/**
 * A supplemental goal that splits the ring, as on the home screen's goals card. The ring keeps the
 * normal goal as its full sweep; [goalFraction] is this goal's share of it, and [fraction] is where
 * its marker sits, moved back from a marker it would crowd (see [spreadGaugeMarkers]).
 */
internal data class CalorieGaugeZone(
    val kind: CalorieGaugeZoneKind,
    val goalFraction: Float,
    val fraction: Float,
    val remainingKcal: Int,
) {
    val exceeded: Boolean
        get() = remainingKcal < 0
}

internal data class CalorieGauge(
    /** Where the fill ends on the ring, following the zones' moved markers. */
    val progress: Float,
    val overflow: Float,
    val zones: List<CalorieGaugeZone>,
    /** The normal goal's remaining energy, shown at the ring's end. */
    val remainingKcal: Int,
) {
    val exceeded: Boolean
        get() = remainingKcal < 0
}

/**
 * The ring of the normal goal with the Optimized and Diet goals on it. A goal at or above the
 * normal goal has no place on the ring, so it is left out.
 */
internal fun calorieGauge(model: CalorieWidgetModel): CalorieGauge {
    val goal = model.normalGoalKcal
    val progress = calorieWidgetProgress(model.netKcal, goal)

    fun zone(kind: CalorieGaugeZoneKind, goalKcal: Int?, leftKcal: Int?): CalorieGaugeZone? {
        if (goalKcal == null || leftKcal == null || goalKcal !in 1 until goal) return null
        val fraction = goalKcal.toFloat() / goal
        return CalorieGaugeZone(kind, goalFraction = fraction, fraction = fraction, leftKcal)
    }

    val goalZones =
        listOfNotNull(
                zone(CalorieGaugeZoneKind.Optimized, model.optimizedGoalKcal, model.optimizedLeftKcal),
                zone(CalorieGaugeZoneKind.Diet, model.dietGoalKcal, model.dietLeftKcal),
            )
            .sortedBy { it.goalFraction }
    val goalFractions = goalZones.map { it.goalFraction }
    val markers = spreadGaugeMarkers(goalFractions)
    val zones = goalZones.mapIndexed { index, zone -> zone.copy(fraction = markers[index]) }
    return CalorieGauge(
        progress = gaugeRingPosition(progress.progress, goalFractions, markers),
        overflow = progress.overflow,
        zones = zones,
        remainingKcal = model.normalLeftKcal,
    )
}

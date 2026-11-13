package com.maksimowiczm.foodyou.app.widget.gauge

import com.maksimowiczm.foodyou.app.ui.home.goals.GaugeCapGoals
import com.maksimowiczm.foodyou.app.ui.home.goals.gaugeCapGoals
import com.maksimowiczm.foodyou.app.ui.home.goals.gaugeRingPosition
import com.maksimowiczm.foodyou.app.ui.home.goals.spreadGaugeMarkers
import com.maksimowiczm.foodyou.app.widget.CalorieWidgetModel
import com.maksimowiczm.foodyou.app.widget.calorieWidgetProgress
import com.maksimowiczm.foodyou.settings.domain.entity.GoalGaugeCapMarkerStyle

internal enum class CalorieGaugeZoneKind {
    Optimized,
    Diet,
}

/**
 * A supplemental goal that splits the ring, as on the home screen's goals card. The ring keeps the
 * normal goal as its full sweep; [goalFraction] is this goal's share of it, and [fraction] is where
 * its marker sits, moved back from a marker it would crowd (see [spreadGaugeMarkers]).
 *
 * A goal can lie off the ring: at or below zero once the week's budget for it is used up, or above
 * the normal goal after enough was saved earlier in the week. Its dot is then pinned to the cap at
 * the matching end, as on the card.
 */
internal data class CalorieGaugeZone(
    val kind: CalorieGaugeZoneKind,
    val goalFraction: Float,
    val fraction: Float,
    val remainingKcal: Int,
) {
    val exceeded: Boolean
        get() = remainingKcal < 0

    val onRing: Boolean
        get() = goalFraction > 0f && goalFraction < 1f
}

internal data class CalorieGauge(
    /** Where the fill ends on the ring, following the zones' moved markers. */
    val progress: Float,
    val overflow: Float,
    /** Every supplemental goal, on the ring or off it, ordered by its goal. */
    val zones: List<CalorieGaugeZone>,
    /** The normal goal's remaining energy, shown in the ring's center. */
    val remainingKcal: Int,
    /** How the goals off the ring are marked on its end caps. */
    val capMarkerStyle: GoalGaugeCapMarkerStyle = GoalGaugeCapMarkerStyle.Teardrop,
) {
    val exceeded: Boolean
        get() = remainingKcal < 0

    val onRingZones: List<CalorieGaugeZone>
        get() = zones.filter { it.onRing }

    /** How many brackets close around the fuller cap; they need room below the ring. */
    val capBrackets: Int
        get() =
            if (capMarkerStyle != GoalGaugeCapMarkerStyle.Bracket) {
                0
            } else {
                maxOf(capGoals.atStart.size, capGoals.atEnd.size)
            }

    /** The goals off the ring, pinned to its start and end caps. */
    val capGoals: GaugeCapGoals<CalorieGaugeZone>
        get() = gaugeCapGoals(zones) { it.goalFraction }
}

/**
 * The ring of the normal goal with the Optimized and Diet goals on it. A goal off the ring keeps
 * its dot on the matching end cap; one equal to the normal goal is left out, as the ring's end
 * marker already stands for it.
 */
internal fun calorieGauge(model: CalorieWidgetModel): CalorieGauge {
    val goal = model.normalGoalKcal
    val progress = calorieWidgetProgress(model.netKcal, goal)

    fun zone(kind: CalorieGaugeZoneKind, goalKcal: Int?, leftKcal: Int?): CalorieGaugeZone? {
        if (goal <= 0 || goalKcal == null || leftKcal == null || goalKcal == goal) return null
        val fraction = goalKcal.toFloat() / goal
        return CalorieGaugeZone(kind, goalFraction = fraction, fraction = fraction, leftKcal)
    }

    val goalZones =
        listOfNotNull(
                zone(CalorieGaugeZoneKind.Optimized, model.optimizedGoalKcal, model.optimizedLeftKcal),
                zone(CalorieGaugeZoneKind.Diet, model.dietGoalKcal, model.dietLeftKcal),
            )
            .sortedBy { it.goalFraction }
    // Only the markers on the ring are spread; a goal off the ring keeps its own share.
    val onRing = goalZones.filter { it.onRing }
    val goalFractions = onRing.map { it.goalFraction }
    val markers = spreadGaugeMarkers(goalFractions, model.markerGapDegrees)
    val moved = onRing.zip(markers).toMap()
    val zones = goalZones.map { zone -> moved[zone]?.let { zone.copy(fraction = it) } ?: zone }
    return CalorieGauge(
        progress = gaugeRingPosition(progress.progress, goalFractions, markers),
        overflow = progress.overflow,
        zones = zones,
        remainingKcal = model.normalLeftKcal,
        capMarkerStyle = model.capMarkerStyle,
    )
}

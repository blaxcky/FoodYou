package com.maksimowiczm.foodyou.app.ui.home.goals

/** [degrees] of the ring's 270° sweep as a share of it. */
internal fun gaugeMarkerGap(degrees: Int): Float = degrees / 270f

/**
 * Where the markers of the goals at [goalFractions] (ascending shares of the ring's own goal) sit on
 * the ring. A goal closer than [minGapDegrees] to the ring's end or to the next marker moves back
 * until it keeps that distance, even though its position then no longer matches its value; closer
 * markers and their values run together.
 */
internal fun spreadGaugeMarkers(
    goalFractions: List<Float>,
    minGapDegrees: Int,
): List<Float> {
    val minGap = gaugeMarkerGap(minGapDegrees)
    val markers = goalFractions.toMutableList()
    var next = 1f
    for (index in markers.indices.reversed()) {
        markers[index] = minOf(markers[index], next - minGap).coerceAtLeast(0f)
        next = markers[index]
    }
    return markers
}

/**
 * Where [progress] (a share of the ring's own goal) is drawn on a ring whose goal markers were
 * moved by [spreadGaugeMarkers]. Each section between two markers is filled proportionally, so the
 * fill passes a marker exactly when its goal is reached.
 */
internal fun gaugeRingPosition(
    progress: Float,
    goalFractions: List<Float>,
    markerFractions: List<Float>,
): Float {
    if (progress <= 0f || progress >= 1f) return progress
    val goals = listOf(0f) + goalFractions + 1f
    val markers = listOf(0f) + markerFractions + 1f
    val section = (1..goals.lastIndex).first { progress <= goals[it] }
    val goalStart = goals[section - 1]
    val goalWidth = goals[section] - goalStart
    if (goalWidth <= 0f) return markers[section]
    val markerStart = markers[section - 1]
    return markerStart + (progress - goalStart) / goalWidth * (markers[section] - markerStart)
}

/** Where the first off-ring goal's dot sits past the ring's end, and the step to the next one. */
internal const val GaugeOffRingFirstAngle = 15f
internal const val GaugeOffRingStepAngle = 12f

/** The dotted track past the ring's end starts this far out, clear of the ring's round cap. */
internal const val GaugeOffRingTrackStartAngle = 5f

/**
 * Where on the ring's circle the dots of goals off the ring sit, in degrees: a goal at or below
 * zero ([goalFraction] ≤ 0) before the ring's start, one above the ring's own goal (≥ 1) past its
 * end. The first dot sits [first] degrees out, each next one [step] further, so the goal nearest
 * the ring's own range sits nearest its end. Goals on the ring are left out.
 */
internal fun <T> offRingDotAngles(
    goals: List<T>,
    goalFraction: (T) -> Float,
    first: Float = GaugeOffRingFirstAngle,
    step: Float = GaugeOffRingStepAngle,
): List<Pair<T, Float>> {
    val start = 135f
    val end = start + 270f
    val beforeStart =
        goals
            .filter { goalFraction(it) <= 0f }
            .sortedByDescending(goalFraction)
            .mapIndexed { index, goal -> goal to start - first - step * index }
    val pastEnd =
        goals
            .filter { goalFraction(it) >= 1f }
            .sortedBy(goalFraction)
            .mapIndexed { index, goal -> goal to end + first + step * index }
    return beforeStart + pastEnd
}

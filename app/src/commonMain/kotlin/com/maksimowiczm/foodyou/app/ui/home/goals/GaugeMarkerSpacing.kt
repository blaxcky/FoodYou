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

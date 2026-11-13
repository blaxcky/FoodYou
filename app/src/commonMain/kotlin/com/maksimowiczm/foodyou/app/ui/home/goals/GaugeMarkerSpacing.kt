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

/**
 * The goals that lie off the ring, pinned to its ends: a goal at or below zero to the start cap, one
 * above the ring's own goal to the end cap. Pinning them keeps the ring's outline symmetric however
 * far off a goal lies; the legend gives the number. Each list holds the nearest goal first.
 */
internal data class GaugeCapGoals<T>(val atStart: List<T>, val atEnd: List<T>)

internal fun <T> gaugeCapGoals(goals: List<T>, goalFraction: (T) -> Float): GaugeCapGoals<T> =
    GaugeCapGoals(
        atStart = goals.filter { goalFraction(it) <= 0f }.sortedByDescending(goalFraction),
        atEnd = goals.filter { goalFraction(it) >= 1f }.sortedBy(goalFraction),
    )

/**
 * The start angle and sweep, in degrees, of each of [count] equal slices of a marker shared by
 * several goals, the first one starting at the top and going clockwise.
 */
internal fun gaugeMarkerSlices(count: Int): List<Pair<Float, Float>> {
    val sweep = 360f / count
    return List(count) { index -> -90f + sweep * index to sweep }
}

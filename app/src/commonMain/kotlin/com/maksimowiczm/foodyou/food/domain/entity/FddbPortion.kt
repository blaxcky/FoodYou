package com.maksimowiczm.foodyou.food.domain.entity

data class FddbPortion(
    val label: String,
    val amount: Double,
    val unit: Unit,
) {
    enum class Unit {
        Gram,
        Milliliter,
    }
}

internal fun List<FddbPortion>.distinctByNormalizedLabel(): List<FddbPortion> =
    distinctBy { it.normalizedLabel() }

internal fun FddbPortion.normalizedLabel(): String =
    label.trim().lowercase().replace(Regex("""\s+"""), " ")

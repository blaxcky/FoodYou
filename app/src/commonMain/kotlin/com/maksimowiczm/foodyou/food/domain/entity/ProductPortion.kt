package com.maksimowiczm.foodyou.food.domain.entity

/** A named amount of a product that can be selected when measuring food. */
data class ProductPortion(
    val label: String,
    val amount: Double,
    val unit: Unit,
) {
    enum class Unit { Gram, Milliliter }
}

internal fun ProductPortion.normalizedLabel(): String =
    label.trim().lowercase().replace(Regex("""\s+"""), " ")

internal fun List<ProductPortion>.distinctByNormalizedLabel(): List<ProductPortion> =
    distinctBy { it.normalizedLabel() }

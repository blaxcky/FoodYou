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

package com.maksimowiczm.foodyou.fooddiary.domain.usecase

/** Product preferences only seed new entry forms; saving always uses the user's selection. */
internal fun selectInitialProductMealId(
    mealIds: List<Long>,
    preferredMealId: Long?,
    requestedMealId: Long,
): Long? = preferredMealId?.takeIf { it in mealIds }
    ?: requestedMealId.takeIf { it in mealIds }
    ?: mealIds.firstOrNull()

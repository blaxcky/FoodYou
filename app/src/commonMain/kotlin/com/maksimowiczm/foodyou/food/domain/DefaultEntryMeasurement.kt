package com.maksimowiczm.foodyou.food.domain

import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.food.domain.entity.Food

internal fun defaultEntryMeasurement(isLiquid: Boolean): Measurement =
    if (isLiquid) Measurement.Milliliter(100.0) else Measurement.Gram(100.0)

internal fun defaultEntryMeasurement(food: Food, latestMeasurement: Measurement?): Measurement =
    defaultEntryMeasurement(
        isLiquid = food.isLiquid,
        totalWeight = food.totalWeight,
        servingWeight = food.servingWeight,
        latestMeasurement = latestMeasurement,
    )

internal fun defaultEntryMeasurement(
    isLiquid: Boolean,
    totalWeight: Double?,
    servingWeight: Double?,
    latestMeasurement: Measurement?,
): Measurement =
    if (latestMeasurement.isValidFor(isLiquid, totalWeight, servingWeight)) {
        latestMeasurement ?: defaultEntryMeasurement(isLiquid)
    } else {
        defaultEntryMeasurement(isLiquid)
    }

internal fun Measurement?.isValidFor(food: Food): Boolean =
    isValidFor(
        isLiquid = food.isLiquid,
        totalWeight = food.totalWeight,
        servingWeight = food.servingWeight,
    )

private fun Measurement?.isValidFor(
    isLiquid: Boolean,
    totalWeight: Double?,
    servingWeight: Double?,
): Boolean =
    when (this) {
        is Measurement.Gram -> !isLiquid
        is Measurement.Milliliter -> isLiquid
        is Measurement.Package -> totalWeight != null
        is Measurement.Serving -> servingWeight != null
        is Measurement.Ounce,
        is Measurement.FluidOunce,
        null -> false
    }

package com.maksimowiczm.foodyou.food.domain

import com.maksimowiczm.foodyou.common.domain.measurement.Measurement

internal fun defaultEntryMeasurement(isLiquid: Boolean): Measurement =
    if (isLiquid) Measurement.Milliliter(100.0) else Measurement.Gram(100.0)

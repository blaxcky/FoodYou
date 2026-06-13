package com.maksimowiczm.foodyou.food.domain

import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultEntryMeasurementTest {
    @Test
    fun returnsGramForSolidFood() {
        assertEquals(Measurement.Gram(100.0), defaultEntryMeasurement(isLiquid = false))
    }

    @Test
    fun returnsMilliliterForLiquidFood() {
        assertEquals(Measurement.Milliliter(100.0), defaultEntryMeasurement(isLiquid = true))
    }
}

package com.maksimowiczm.foodyou.app.ui.common.utility

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EnergyInputTest {
    @Test
    fun acceptsZeroDecimalCommaAndEnergyConversion() {
        assertEquals(0.0, validatedNonNegativeEnergyKcal("0") { it })
        assertEquals(125.5, validatedNonNegativeEnergyKcal("125,5") { it })
        assertEquals(
            100.0,
            validatedNonNegativeEnergyKcal("418.4") { it / 4.184 } ?: error("Expected value"),
            absoluteTolerance = 0.000_001,
        )
    }

    @Test
    fun rejectsBlankNegativeAndNonFiniteValues() {
        assertNull(validatedNonNegativeEnergyKcal("") { it })
        assertNull(validatedNonNegativeEnergyKcal("-1") { it })
        assertNull(validatedNonNegativeEnergyKcal("NaN") { it })
        assertNull(validatedNonNegativeEnergyKcal("Infinity") { it })
    }
}

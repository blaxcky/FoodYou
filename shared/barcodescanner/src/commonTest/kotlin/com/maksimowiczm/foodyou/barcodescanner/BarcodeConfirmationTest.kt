package com.maksimowiczm.foodyou.barcodescanner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BarcodeConfirmationTest {
    @Test
    fun confirmsThirdConsecutiveObservationOnlyOnce() {
        val confirmation = BarcodeConfirmation()

        assertNull(confirmation.observe("123"))
        assertNull(confirmation.observe("123"))
        assertEquals("123", confirmation.observe("123"))
        assertNull(confirmation.observe("123"))
    }

    @Test
    fun doesNotConfirmOneOrTwoObservations() {
        val confirmation = BarcodeConfirmation()

        assertNull(confirmation.observe("123"))
        assertNull(confirmation.observe("123"))
    }

    @Test
    fun differentValueStartsNewSequence() {
        val confirmation = BarcodeConfirmation()

        assertNull(confirmation.observe("123"))
        assertNull(confirmation.observe("123"))
        assertNull(confirmation.observe("456"))
        assertNull(confirmation.observe("456"))
        assertEquals("456", confirmation.observe("456"))
    }

    @Test
    fun nullAndBlankObservationsResetSequence() {
        listOf<String?>(null, "", "   ").forEach { unusableValue ->
            val confirmation = BarcodeConfirmation()

            assertNull(confirmation.observe("123"))
            assertNull(confirmation.observe("123"))
            assertNull(confirmation.observe(unusableValue))
            assertNull(confirmation.observe("123"))
            assertNull(confirmation.observe("123"))
            assertEquals("123", confirmation.observe("123"))
        }
    }

    @Test
    fun resetClearsSequenceAndAllowsAnotherConfirmation() {
        val confirmation = BarcodeConfirmation()

        repeat(2) { assertNull(confirmation.observe("123")) }
        confirmation.reset()
        repeat(2) { assertNull(confirmation.observe("123")) }
        assertEquals("123", confirmation.observe("123"))

        confirmation.reset()
        repeat(2) { assertNull(confirmation.observe("456")) }
        assertEquals("456", confirmation.observe("456"))
    }
}

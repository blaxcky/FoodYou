package com.maksimowiczm.foodyou.app.ui.activity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ActivitySettingsViewModelTest {
    @Test
    fun kcalPerStepTextKeepsCurrentInputOverPersistedValue() {
        assertEquals("0,", kcalPerStepText(input = "0,", persisted = 0.0))
    }

    @Test
    fun kcalPerStepTextUsesPersistedValueWhenInputHasNotBeenEdited() {
        assertEquals("0.004", kcalPerStepText(input = null, persisted = 0.004))
    }

    @Test
    fun kcalPerStepTextAllowsEmptyInput() {
        assertEquals("", kcalPerStepText(input = "", persisted = 0.0))
    }

    @Test
    fun completeKcalPerStepAcceptsCommaDecimalNumber() {
        assertEquals(0.004, "0,004".toCompleteKcalPerStepOrNull())
    }

    @Test
    fun completeKcalPerStepRejectsIntermediateDecimalSeparator() {
        assertNull("0,".toCompleteKcalPerStepOrNull())
        assertNull("0.".toCompleteKcalPerStepOrNull())
    }
}

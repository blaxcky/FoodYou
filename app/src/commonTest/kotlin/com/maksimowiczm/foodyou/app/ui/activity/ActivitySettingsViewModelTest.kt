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

    @Test
    fun dailyStepGoalTextPrefersInputOverPersistedValue() {
        assertEquals("", dailyStepGoalText(input = "", persisted = 8_000))
        assertEquals("8000", dailyStepGoalText(input = null, persisted = 8_000))
        assertEquals("", dailyStepGoalText(input = null, persisted = null))
    }

    @Test
    fun dailyStepGoalAcceptsOnlyPositiveWholeNumbers() {
        assertEquals(8_000, "8000".toDailyStepGoalOrNull())
        assertNull("0".toDailyStepGoalOrNull())
        assertNull("".toDailyStepGoalOrNull())
    }
}

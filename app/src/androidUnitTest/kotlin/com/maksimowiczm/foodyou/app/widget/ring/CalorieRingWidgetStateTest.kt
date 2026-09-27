package com.maksimowiczm.foodyou.app.widget.ring

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.maksimowiczm.foodyou.app.widget.calorieWidgetModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class CalorieRingWidgetStateTest {
    @Test
    fun roundTripsModelWithAllGoals() {
        val model =
            calorieWidgetModel(
                today = LocalDate(2026, 9, 15),
                eatenKcal = 2960.0,
                burnedKcal = 307.0,
                countedSteps = 8_169,
                baseGoalKcal = 2100.0,
                dietEnergyDeficitKcal = 500.0,
                previousDays = emptyList(),
            )

        val preferences = mutablePreferencesOf().apply { write(model) }

        assertEquals(model, preferences.toCalorieWidgetModel())
    }

    @Test
    fun roundTripsModelWithoutOptionalGoalsAndClearsStaleValues() {
        val withGoals =
            calorieWidgetModel(
                today = LocalDate(2026, 9, 15),
                eatenKcal = 1000.0,
                burnedKcal = 0.0,
                baseGoalKcal = 2000.0,
                dietEnergyDeficitKcal = 300.0,
                previousDays = emptyList(),
            )
        val withoutGoals =
            calorieWidgetModel(
                today = LocalDate(2026, 9, 16),
                eatenKcal = 1000.0,
                burnedKcal = 0.0,
                baseGoalKcal = 2000.0,
                dietEnergyDeficitKcal = null,
                previousDays = emptyList(),
            )

        val preferences = mutablePreferencesOf().apply { write(withGoals) }
        preferences.write(withoutGoals)

        assertEquals(withoutGoals, preferences.toCalorieWidgetModel())
        assertFalse(preferences.toCalorieWidgetModel()!!.dietGoalConfigured)
        assertNull(preferences.toCalorieWidgetModel()?.dietLeftKcal)
    }

    @Test
    fun roundTripsConfiguredDietWithoutDistinctGoal() {
        val model =
            calorieWidgetModel(
                today = LocalDate(2026, 9, 15),
                eatenKcal = 1000.0,
                burnedKcal = 0.0,
                baseGoalKcal = 2000.0,
                dietEnergyDeficitKcal = 0.4,
                previousDays = emptyList(),
            )

        val restored = mutablePreferencesOf().apply { write(model) }.toCalorieWidgetModel()!!

        assertTrue(restored.dietGoalConfigured)
        assertNull(restored.dietGoalKcal)
        assertNull(restored.dietLeftKcal)
    }

    @Test
    fun restoresConfiguredDietFromLegacyStateWithDietValues() {
        val restored =
            mutablePreferencesOf(
                    CalorieRingWidgetState.date to "2026-09-15",
                    CalorieRingWidgetState.dietGoalKcal to 1700,
                    CalorieRingWidgetState.dietLeftKcal to 700,
                )
                .toCalorieWidgetModel()!!

        assertTrue(restored.dietGoalConfigured)
        assertEquals(1700, restored.dietGoalKcal)
        assertEquals(700, restored.dietLeftKcal)
    }

    @Test
    fun emptyStateHasNoModel() {
        assertNull(mutablePreferencesOf().toCalorieWidgetModel())
    }
}

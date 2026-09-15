package com.maksimowiczm.foodyou.app.widget.ring

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.maksimowiczm.foodyou.app.widget.calorieWidgetModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
        assertNull(preferences.toCalorieWidgetModel()?.dietLeftKcal)
    }

    @Test
    fun emptyStateHasNoModel() {
        assertNull(mutablePreferencesOf().toCalorieWidgetModel())
    }
}

package com.maksimowiczm.foodyou.fooddiary.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalTime
import com.maksimowiczm.foodyou.fooddiary.domain.entity.Meal

class SelectInitialProductMealTest {
    private val meals = listOf(
        Meal(1, "Breakfast", LocalTime(6, 0), LocalTime(10, 0), 0),
        Meal(2, "Lunch", LocalTime(10, 0), LocalTime(15, 0), 1),
        Meal(3, "Dinner", LocalTime(15, 0), LocalTime(21, 0), 2),
        Meal(4, "Snacks", LocalTime(0, 0), LocalTime(0, 0), 3),
    )

    @Test fun snackPreferenceOverridesTimeBasedBarcodeEntryAtEveryMealTime() {
        for (hour in listOf(8, 12, 18, 23)) {
            val requested = selectMealForBarcodeShortcut(meals, LocalTime(hour, 0))!!
            assertEquals(4L, selectInitialProductMealId(meals.map { it.id }, 4, requested.id))
        }
    }

    @Test fun preferenceOverridesSearchEntryMealAndSurvivesReorderingAndRenaming() {
        val renamed = meals.reversed().map { it.copy(name = "Meal ${it.id}") }
        assertEquals(4L, selectInitialProductMealId(renamed.map { it.id }, 4, 1))
    }

    @Test fun automaticAndDeletedPreferencesFallBackToRequestedThenFirstMeal() {
        assertEquals(2L, selectInitialProductMealId(listOf(1, 2), null, 2))
        assertEquals(2L, selectInitialProductMealId(listOf(1, 2), 4, 2))
        assertEquals(1L, selectInitialProductMealId(listOf(1, 2), 4, 99))
        assertNull(selectInitialProductMealId(emptyList(), 4, 99))
    }
}

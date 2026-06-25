package com.maksimowiczm.foodyou.fooddiary.domain.usecase

import com.maksimowiczm.foodyou.fooddiary.domain.entity.Meal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalTime

class SelectMealForBarcodeShortcutUseCaseTest {

    @Test
    fun sevenSelectsBreakfast() {
        assertEquals(Breakfast, selectMealForBarcodeShortcut(DefaultMeals, LocalTime(7, 0)))
    }

    @Test
    fun noonSelectsLunch() {
        assertEquals(Lunch, selectMealForBarcodeShortcut(DefaultMeals, LocalTime(12, 0)))
    }

    @Test
    fun eighteenSelectsDinner() {
        assertEquals(Dinner, selectMealForBarcodeShortcut(DefaultMeals, LocalTime(18, 0)))
    }

    @Test
    fun twentyThreeFallsBackToSnacksAllDayMeal() {
        assertEquals(Snacks, selectMealForBarcodeShortcut(DefaultMeals, LocalTime(23, 0)))
    }

    @Test
    fun disabledTimeBasedSortingStillSelectsByCurrentTime() {
        val useTimeBasedSorting = false

        assertEquals(false, useTimeBasedSorting)
        assertEquals(Lunch, selectMealForBarcodeShortcut(DefaultMeals, LocalTime(12, 0)))
    }

    @Test
    fun emptyMealsReturnNull() {
        assertNull(selectMealForBarcodeShortcut(emptyList(), LocalTime(12, 0)))
    }

    private companion object {
        val Breakfast = Meal(1, "Breakfast", LocalTime(6, 0), LocalTime(10, 0), 0)
        val Lunch = Meal(2, "Lunch", LocalTime(10, 0), LocalTime(15, 0), 1)
        val Dinner = Meal(3, "Dinner", LocalTime(15, 0), LocalTime(21, 0), 2)
        val Snacks = Meal(4, "Snacks", LocalTime(0, 0), LocalTime(0, 0), 3)

        val DefaultMeals = listOf(Breakfast, Lunch, Dinner, Snacks)
    }
}

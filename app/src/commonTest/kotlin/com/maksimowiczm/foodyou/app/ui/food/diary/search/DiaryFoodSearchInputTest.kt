package com.maksimowiczm.foodyou.app.ui.food.diary.search

import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DiaryFoodSearchInputTest {
    @Test
    fun validAmountSuffixIsSeparatedFromSearchText() {
        assertParsed("Nudeln;50", "Nudeln", 50)
        assertParsed("  Nudeln  ;  50  ", "Nudeln", 50)
        assertParsed("Nudeln;00050", "Nudeln", 50)
        assertParsed("Nudeln;000000000000000000000050", "Nudeln", 50)
        assertParsed("Nudeln;Vollkorn;50", "Nudeln;Vollkorn", 50)
    }

    @Test
    fun invalidAmountSuffixLeavesCompleteInputAsSearchText() {
        listOf(
                ";50",
                "Nudeln;0",
                "Nudeln;-5",
                "Nudeln;50,5",
                "Nudeln;50.5",
                "Nudeln;50g",
                "Nudeln;",
                "Nudeln;2147483648",
            )
            .forEach { input -> assertParsed(input, input, null) }
    }

    @Test
    fun inputWithoutSuffixIsNormalTrimmedSearchText() {
        assertParsed("  Nudeln  ", "Nudeln", null)
    }

    @Test
    fun amountCreatesMeasurementMatchingFoodType() {
        val suggested = Measurement.Serving(1.0)

        assertEquals(Measurement.Gram(50.0), diarySearchMeasurement(suggested, false, 50))
        assertEquals(Measurement.Milliliter(50.0), diarySearchMeasurement(suggested, true, 50))
    }

    @Test
    fun missingAmountKeepsSuggestedMeasurement() {
        val suggested = Measurement.Serving(1.0)

        assertEquals(suggested, diarySearchMeasurement(suggested, false, null))
        assertEquals(suggested, diarySearchMeasurement(suggested, true, null))
    }

    private fun assertParsed(input: String, expectedSearchText: String, expectedAmount: Int?) {
        val parsed = parseDiaryFoodSearchInput(input)

        assertEquals(input, parsed.originalText)
        assertEquals(expectedSearchText, parsed.searchText)
        if (expectedAmount == null) {
            assertNull(parsed.amount)
        } else {
            assertEquals(expectedAmount, parsed.amount)
        }
    }
}

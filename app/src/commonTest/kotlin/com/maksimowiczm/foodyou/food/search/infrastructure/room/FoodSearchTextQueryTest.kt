package com.maksimowiczm.foodyou.food.search.infrastructure.room

import com.maksimowiczm.foodyou.common.infrastructure.room.FoodSourceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class FoodSearchTextQueryTest {
    @Test
    fun normalizesGermanLettersAndDecomposedUmlauts() {
        assertEquals("knodel", normalizeFoodSearchTerm("KNÖDEL"))
        assertEquals("apfel", normalizeFoodSearchTerm("Äpfel"))
        assertEquals("suss", normalizeFoodSearchTerm("SÜẞ"))
        assertEquals("knodel", normalizeFoodSearchTerm("KNO\u0308DEL"))
        assertEquals("grosse", normalizeFoodSearchTerm("große"))
        // Non-German letters stay available to the unicode61 FTS tokenizer unchanged.
        assertEquals("cafÉ", normalizeFoodSearchTerm("CAFÉ"))
    }

    @Test
    fun textIsBoundInBothResultAndCountQueries() {
        val input = "Knödel' OR 1=1; --"
        for (countOnly in listOf(false, true)) {
            val query = FoodSearchTextQuery(
                query = input,
                sources = listOf(FoodSourceType.User, FoodSourceType.FDDB),
                includeRecipes = true,
                excludedRecipeId = 123456789,
                nowEpochSeconds = 987654321,
            ).build(countOnly)

            assertFalse(query.sql.contains("Knödel"))
            assertFalse(query.sql.contains("1=1"))
            assertFalse(query.sql.contains("123456789"))
            assertFalse(query.sql.contains("987654321"))
        }
    }
}

package com.maksimowiczm.foodyou.app.ui.food.quickcapture

import com.maksimowiczm.foodyou.food.domain.entity.QuickCaptureFoodName
import com.maksimowiczm.foodyou.food.domain.entity.normalizeQuickCaptureFoodName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class QuickCaptureNameSuggestionsTest {
    @Test
    fun gemuseFindsGemuseWithUmlautAndPreservesTheStoredName() {
        val vegetables = name(1, "Gemüse")
        val names = listOf(name(2, "Apfel"), vegetables, name(3, "Brot"))

        assertEquals(listOf(vegetables), quickCaptureNameSuggestions("gemuse", names))
        assertEquals(listOf(vegetables), quickCaptureNameSuggestions("GEMÜSE", names))
        assertEquals("Gemüse", vegetables.name)
        assertEquals("gemüse", vegetables.normalizedName)
    }

    @Test
    fun allGermanUmlautsAndSharpSMatchPlainLetters() {
        val names = listOf(name(1, "Käse"), name(2, "Öl"), name(3, "Süße Soße"))

        assertEquals(listOf(names[0]), quickCaptureNameSuggestions("kase", names))
        assertEquals(listOf(names[1]), quickCaptureNameSuggestions("ol", names))
        assertEquals(listOf(names[2]), quickCaptureNameSuggestions("susse sosse", names))
    }

    @Test
    fun umlautsInTheQueryAlsoFindNamesStoredWithoutUmlauts() {
        val vegetables = name(1, "Gemuse")

        assertEquals(listOf(vegetables), quickCaptureNameSuggestions("Gemüse", listOf(vegetables)))
    }

    @Test
    fun decomposedUmlautsMatchInBothNamesAndQueries() {
        val vegetables = name(1, "Gemu\u0308se")

        assertEquals(listOf(vegetables), quickCaptureNameSuggestions("gemuse", listOf(vegetables)))
        assertEquals(
            listOf(name(2, "Gemüse")),
            quickCaptureNameSuggestions("Gemu\u0308se", listOf(name(2, "Gemüse"))),
        )
    }

    @Test
    fun partialQueriesIgnoreCaseAndExtraWhitespace() {
        val vegetables = name(1, "Gebratenes   Gemüse")

        assertEquals(
            listOf(vegetables),
            quickCaptureNameSuggestions("  BRATENES  gemu  ", listOf(vegetables)),
        )
    }

    @Test
    fun blankAndUnmatchedQueriesHaveNoSuggestions() {
        val names = listOf(name(1, "Gemüse"))

        assertTrue(quickCaptureNameSuggestions("", names).isEmpty())
        assertTrue(quickCaptureNameSuggestions("   ", names).isEmpty())
        assertTrue(quickCaptureNameSuggestions("Apfel", names).isEmpty())
    }

    @Test
    fun atMostEightMatchingNamesKeepTheirLibraryOrder() {
        val vegetables = (1L..10L).map { name(it, "Gemüse $it") }
        val names = listOf(name(11, "Apfel")) + vegetables

        assertEquals(vegetables.take(8), quickCaptureNameSuggestions("gemuse", names))
    }

    private fun name(id: Long, name: String) =
        QuickCaptureFoodName(
            id = id,
            name = name,
            normalizedName = normalizeQuickCaptureFoodName(name),
            usageCount = 1,
            lastUsedAt = Instant.fromEpochSeconds(id),
        )
}

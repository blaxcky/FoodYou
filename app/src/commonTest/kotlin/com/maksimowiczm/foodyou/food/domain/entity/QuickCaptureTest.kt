package com.maksimowiczm.foodyou.food.domain.entity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class QuickCaptureTest {
    @Test
    fun namesAreCanonicalizedAndNormalized() {
        assertEquals("Greek Yoghurt", canonicalQuickCaptureFoodName("  Greek   Yoghurt  "))
        assertEquals("greek yoghurt", normalizeQuickCaptureFoodName("  GREEK   Yoghurt  "))
    }

    @Test
    fun beforeAfterRequiresAValidDifference() {
        val ready = entry(id = 1, before = 410.0, after = 175.5)
        val awaiting = entry(id = 2, before = 410.0, after = null)
        val invalid = entry(id = 3, before = 410.0, after = 410.0)

        assertEquals(234.5, ready.effectiveWeightInGrams)
        assertTrue(ready.isReady)
        assertTrue(awaiting.isAwaitingAfter)
        assertFalse(awaiting.isReady)
        assertNull(invalid.effectiveWeightInGrams)
    }

    @Test
    fun aggregationUsesLibraryIdentityAndExcludesPendingAndCompletedRows() {
        val entries =
            listOf(
                entry(id = 1, foodNameId = 7, foodName = "Apple", direct = 100.0),
                entry(id = 2, foodNameId = 7, foodName = "Apple", direct = 52.5),
                entry(id = 3, foodNameId = 8, foodName = "Apple", direct = 25.0),
                entry(id = 4, foodNameId = 7, foodName = "Apple", before = 200.0, after = null),
                entry(id = 5, foodNameId = 7, foodName = "Apple", direct = 10.0, completed = true),
            )

        val grouped = entries.quickCaptureGroups(aggregateSameFoods = true)
        assertEquals(2, grouped.size)
        assertEquals(listOf(1L, 2L), grouped.first().entries.map { it.id })
        assertEquals(152.5, grouped.first().weightInGrams)

        assertEquals(3, entries.quickCaptureGroups(aggregateSameFoods = false).size)
    }

    @Test
    fun promptOutputIsExactAndContainsOnlyReadyGroups() {
        val groups =
            listOf(
                QuickCaptureLogGroup("library:1", "Apfel", emptyList(), 152.5),
                QuickCaptureLogGroup("library:2", "Skyr", emptyList(), 200.0),
            )

        assertEquals(
            "$QuickCaptureChatGptPrompt\n\n152.5g Apfel\n200g Skyr",
            groups.quickCapturePrompt(),
        )
    }

    private fun entry(
        id: Long,
        foodNameId: Long? = 1,
        foodName: String = "Test",
        direct: Double? = null,
        before: Double? = null,
        after: Double? = null,
        completed: Boolean = false,
    ) =
        QuickCaptureLogEntry(
            id = id,
            foodNameId = foodNameId,
            foodName = foodName,
            weightMode = if (before == null) QuickCaptureWeightMode.Direct else QuickCaptureWeightMode.BeforeAfter,
            directWeightInGrams = direct,
            beforeWeightInGrams = before,
            afterWeightInGrams = after,
            photoPath = null,
            createdAt = Instant.fromEpochSeconds(id),
            completedAt = if (completed) Instant.fromEpochSeconds(100) else null,
        )
}

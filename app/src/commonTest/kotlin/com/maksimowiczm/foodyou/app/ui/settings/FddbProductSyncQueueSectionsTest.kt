package com.maksimowiczm.foodyou.app.ui.settings

import com.maksimowiczm.foodyou.food.domain.entity.FddbProductSyncQueueItem
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FddbProductSyncQueueSectionsTest {
    @Test
    fun emptyQueueHasEmptySections() {
        val sections = emptyList<FddbProductSyncQueueItem>().toSections()

        assertTrue(sections.failed.isEmpty())
        assertTrue(sections.next.isEmpty())
        assertTrue(sections.remaining.isEmpty())
    }

    @Test
    fun failedItemsAreSeparatedAndQueueOrderIsKept() {
        val queue = listOf(item(1), item(2, error = "404"), item(3), item(4, error = "timeout"), item(5))

        val sections = queue.toSections(nextCount = 2)

        assertEquals(listOf(2L, 4L), sections.failed.ids())
        assertEquals(listOf(1L, 3L), sections.next.ids())
        assertEquals(listOf(5L), sections.remaining.ids())
    }

    @Test
    fun nextIsLimitedToNextCount() {
        val queue = (1L..8L).map { item(it) }

        val sections = queue.toSections(nextCount = 5)

        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), sections.next.ids())
        assertEquals(listOf(6L, 7L, 8L), sections.remaining.ids())
    }

    private fun List<FddbProductSyncQueueItem>.ids() = map { it.productId.id }

    private fun item(id: Long, error: String? = null) =
        FddbProductSyncQueueItem(
            productId = FoodId.Product(id),
            name = "Product $id",
            brand = null,
            sourceUrl = "https://fddb.info/db/de/lebensmittel/product_$id/index.html",
            lastSyncedAt = null,
            lastAttemptAt = null,
            lastError = error,
        )
}

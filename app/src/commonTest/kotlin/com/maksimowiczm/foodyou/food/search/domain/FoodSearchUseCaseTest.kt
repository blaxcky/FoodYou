package com.maksimowiczm.foodyou.food.search.domain

import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import kotlin.test.*

class FoodSearchUseCaseTest {
    @Test
    fun resultQueriesNeverRecordHistory() {
        val fixture = FoodSearchTestFixture()
        fixture.useCase.search("Reis", FoodSource.Type.User, null)
        fixture.useCase.search("Reis", FoodSource.Type.FDDB, null)
        fixture.useCase.searchRecent("Reis", null)
        assertTrue(fixture.events.isEmpty())
    }

    @Test
    fun explicitRecordingNormalizesTextAndSkipsBlankAndBarcode() {
        val fixture = FoodSearchTestFixture()
        fixture.useCase.recordSearch("  Reis   gekocht  ")
        fixture.useCase.recordSearch(null)
        fixture.useCase.recordSearch("  ")
        fixture.useCase.recordSearch("1234567890")
        val event = fixture.events.single() as FoodSearchEvent
        assertEquals("Reis gekocht", event.query.query)
        assertEquals(fixture.dateProvider.nowInstant(), event.timestamp)
    }
}

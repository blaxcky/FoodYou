package com.maksimowiczm.foodyou.app.ui.food.search

import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.common.domain.search.SearchQuery
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.FoodSearchHistory
import com.maksimowiczm.foodyou.food.search.domain.FoodSearchEvent
import com.maksimowiczm.foodyou.food.search.domain.FoodSearchTestFixture
import kotlin.test.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*

class FoodSearchViewModelTest {
    @Test
    fun typingStartsAfter300MsAndNormalizedDuplicatesDoNotRestart() = withSearch { fixture, model ->
        fixture.repository.calls.clear()
        model.changeQuery(" N ")
        runCurrent()
        assertTrue(model.uiState.value.isPending)
        assertTrue(model.uiState.value.sources.isEmpty())
        advanceTimeBy(299)
        runCurrent()
        assertTrue(fixture.repository.calls.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals("N", model.uiState.value.query)
        assertFalse(model.uiState.value.isPending)
        assertTrue(fixture.repository.calls.all { it.query == SearchQuery.Text("N") })
        assertTrue(fixture.events.isEmpty())

        fixture.repository.calls.clear()
        model.changeQuery("N  ")
        model.search(" N ")
        advanceTimeBy(300)
        runCurrent()
        assertTrue(fixture.repository.calls.isEmpty())
    }

    @Test
    fun rapidTypingCancelsIntermediateRequestsAndInFlightCounters() = withSearch { fixture, model ->
        fixture.repository.calls.clear()
        model.changeQuery("R")
        runCurrent()
        advanceTimeBy(200)
        model.changeQuery("Reis")
        runCurrent()
        advanceTimeBy(300)
        runCurrent()
        assertTrue(fixture.repository.calls.none { it.query.query == "R" })
        assertEquals("Reis", model.uiState.value.query)

        val gate = CompletableDeferred<Unit>()
        fixture.repository.gates["Langsam"] = gate
        model.search("Langsam")
        runCurrent()
        assertTrue(model.uiState.value.isPending)
        model.search("Apfel")
        runCurrent()
        assertTrue("Langsam" in fixture.repository.cancelledQueries)
        gate.complete(Unit)
        runCurrent()
        assertEquals("Apfel", model.uiState.value.query)
        assertFalse(model.uiState.value.isPending)
    }

    @Test
    fun immediatelyReturningToPreviousQueryCancelsPendingDifferentQuery() = withSearch { fixture, model ->
        model.search("Apfel")
        runCurrent()
        fixture.repository.calls.clear()
        model.changeQuery("Banane")
        // Submit before the asynchronous result state has seen the intermediate input.
        model.search("Apfel")
        runCurrent()
        advanceTimeBy(300)
        runCurrent()
        assertEquals("Apfel", model.uiState.value.query)
        assertFalse(model.uiState.value.isPending)
        assertTrue(fixture.repository.calls.none { it.query.query == "Banane" })
    }

    @Test
    fun clearingReturnsToSelectedHistoryTabAndKeepsSearchFilter() = withSearch { _, model ->
        assertEquals(FoodSearchHistoryTab.RecentFood, model.uiState.value.historyTab)
        assertEquals(FoodFilter.Source.Recent, model.uiState.value.displayedSource)
        model.changeHistoryTab(FoodSearchHistoryTab.RecentSearches)
        model.changeSource(FoodFilter.Source.FDDB)
        model.search("Apfel")
        runCurrent()
        assertFalse(model.uiState.value.showingHistory)
        assertEquals(FoodFilter.Source.FDDB, model.uiState.value.displayedSource)
        model.changeQuery("   ")
        runCurrent()
        assertTrue(model.uiState.value.showingHistory)
        assertFalse(model.uiState.value.isPending)
        assertEquals(FoodSearchHistoryTab.RecentSearches, model.uiState.value.historyTab)
        assertEquals(FoodFilter.Source.FDDB, model.uiState.value.filter.source)
    }

    @Test
    fun enterHistoryRestoreAndBarcodeAreImmediateButOnlyExplicitRecordingSaves() = withSearch { fixture, model ->
        fixture.repository.calls.clear()
        model.changeQuery("Apfel")
        model.search("Apfel")
        runCurrent()
        assertFalse(model.uiState.value.isPending)
        assertTrue(fixture.events.isEmpty())
        model.recordSearch("Apfel")
        assertEquals(listOf("Apfel"), fixture.events.filterIsInstance<FoodSearchEvent>().map { it.query.query })
        model.search("1234567890")
        runCurrent()
        assertFalse(model.uiState.value.isPending)
        assertTrue(fixture.repository.calls.any { it.query is SearchQuery.Barcode })
        model.recordSearch("1234567890")
        model.recordSearch(null)
        assertEquals(1, fixture.events.size)
    }

    @Test
    fun historyKeepsRepositoryOrderAndRecipeExclusionReachesEveryQuery() = withSearch { fixture, model ->
        fixture.history.entries.value = (12 downTo 1).map {
            FoodSearchHistory(fixture.dateProvider.nowInstant(), SearchQuery.Text("Suche $it"))
        }
        model.search("Reis")
        runCurrent()
        assertEquals(10, fixture.history.requestedLimit)
        assertEquals((12 downTo 3).map { "Suche $it" }, model.uiState.value.recentSearches)
        assertTrue(fixture.repository.calls.all { it.excludedRecipe == FoodId.Recipe(42) })
        assertTrue(fixture.events.isEmpty())
    }

    @Test
    fun sourceFallbackIgnoresPendingAndOldQueriesAndRespectsManualChoice() = withSearch { fixture, model ->
        fixture.repository.sourceCounts["Reis" to null] = MutableStateFlow(0)
        val gate = CompletableDeferred<Unit>()
        fixture.repository.gates["Reis"] = gate
        model.search("Reis")
        runCurrent()
        model.resultsLoaded("Apfel", FoodFilter.Source.All)
        model.resultsLoaded("Reis", FoodFilter.Source.All)
        assertEquals(FoodFilter.Source.All, model.uiState.value.filter.source)
        gate.complete(Unit)
        runCurrent()
        model.resultsLoaded("Reis", FoodFilter.Source.All)
        runCurrent()
        assertEquals(FoodFilter.Source.Recent, model.uiState.value.filter.source)
        model.changeSource(FoodFilter.Source.All)
        runCurrent()
        model.resultsLoaded("Reis", FoodFilter.Source.All)
        runCurrent()
        assertEquals(FoodFilter.Source.All, model.uiState.value.filter.source)
        model.changeQuery("Banane")
        model.resultsLoaded("Reis", FoodFilter.Source.All)
        runCurrent()
        assertEquals(FoodFilter.Source.All, model.uiState.value.filter.source)
    }

    private fun withSearch(block: suspend TestScope.(FoodSearchTestFixture, FoodSearchViewModel) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = FoodSearchTestFixture()
        val model = FoodSearchViewModel(
            FoodId.Recipe(42), fixture.preferences, fixture.history, fixture.repository,
            fixture.useCase, fixture.favoriteUseCase, fixture.dateProvider,
        )
        try {
            runCurrent()
            block(fixture, model)
        } finally {
            model.viewModelScope.cancel()
            runCurrent()
            Dispatchers.resetMain()
        }
    }
}

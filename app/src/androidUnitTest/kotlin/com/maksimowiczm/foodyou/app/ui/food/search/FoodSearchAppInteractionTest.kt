package com.maksimowiczm.foodyou.app.ui.food.search

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.app.ui.food.diary.search.diarySearchMeasurement
import com.maksimowiczm.foodyou.app.ui.food.diary.search.parseDiaryFoodSearchInput
import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.common.domain.food.NutrientValue
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.FoodSearchHistory
import com.maksimowiczm.foodyou.common.domain.search.SearchQuery
import com.maksimowiczm.foodyou.food.search.domain.FoodSearch
import com.maksimowiczm.foodyou.food.search.domain.FoodSearchEvent
import com.maksimowiczm.foodyou.food.search.domain.FoodSearchTestFixture
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "de-rDE-w390dp-h844dp-mdpi", application = Application::class)
class FoodSearchAppInteractionTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ActivityController<ComponentActivity>
    private lateinit var model: FoodSearchViewModel
    private val fixture = FoodSearchTestFixture()
    private var selected: Measurement? = null
    private var amount: Int? = null

    @After
    fun tearDown() {
        if (::activity.isInitialized) activity.close()
        if (::model.isInitialized) model.viewModelScope.cancel()
    }

    @Test
    fun emptyFieldShowsRecentFoodAndHistoryTapSearchesImmediatelyWithoutRecording() {
        fixture.repository.foods[null] = listOf(food("Zuletzt gegessen"))
        fixture.repository.foods["Reis"] = listOf(food("Reis gekocht"))
        fixture.history.entries.value = listOf(FoodSearchHistory(fixture.dateProvider.nowInstant(), SearchQuery.Text("Reis")))
        show()
        compose.onNodeWithText("Kürzlich eingetragen").assertIsSelected()
        compose.onNodeWithText("Zuletzt gegessen").assertExists()
        compose.onNodeWithText("Zuletzt gesucht").performClick().assertIsSelected()
        compose.onNodeWithText("Reis").performClick()
        awaitFood("Reis gekocht")
        compose.onNodeWithText("Zuletzt gesucht").assertDoesNotExist()
        assertTrue(fixture.events.isEmpty())
        compose.onNodeWithContentDescription("Löschen").performClick()
        compose.onNodeWithText("Zuletzt gesucht").assertIsSelected()
        compose.onNodeWithText("Reis").assertExists()
    }

    @Test
    fun typingShowsResultsWithoutEnterAndSelectionUsesLatestAmount() {
        fixture.repository.foods["Nudeln"] = listOf(food("Nudeln gekocht"))
        show(parseAmount = true)
        val field = compose.onNode(hasSetTextAction())
        field.performClick().performTextInput("Nudeln;50")
        awaitFood("Nudeln gekocht")
        field.assertIsFocused()
        assertTrue(fixture.events.isEmpty())
        field.performTextReplacement("Nudeln;75")
        awaitFood("Nudeln gekocht")
        compose.onNodeWithText("Nudeln gekocht").performClick()
        assertEquals(Measurement.Gram(75.0), selected)
        assertEquals(listOf("Nudeln"), fixture.events.filterIsInstance<FoodSearchEvent>().map { it.query.query })
        field.assertIsNotFocused()
    }

    @Test
    fun newerInputHidesOldFoodAndEnterSearchesAndRecordsImmediately() {
        fixture.repository.foods["Reis"] = listOf(food("Reis gekocht"))
        fixture.repository.foods["Apfel"] = listOf(food("Apfel frisch"))
        show()
        val field = compose.onNode(hasSetTextAction())
        field.performClick().performTextInput("Reis")
        awaitFood("Reis gekocht")
        field.performTextReplacement("Apfel")
        compose.onNodeWithText("Reis gekocht").assertDoesNotExist()
        field.performImeAction()
        awaitFood("Apfel frisch")
        field.assertIsNotFocused()
        assertEquals(listOf("Apfel"), fixture.events.filterIsInstance<FoodSearchEvent>().map { it.query.query })
    }

    @Test
    fun restoredTextSearchesImmediatelyWithoutRecordingAndShowsClearAndScanner() {
        fixture.repository.foods["Apfel"] = listOf(food("Apfel frisch"))
        show(restored = "Apfel")
        awaitFood("Apfel frisch")
        compose.onNodeWithContentDescription("Löschen").assertExists()
        compose.onNodeWithContentDescription("Barcode scannen").assertExists()
        assertTrue(fixture.events.isEmpty())
    }

    @Test
    fun manuallySelectedSourceStaysVisibleWhenNextQueryHasNoMatches() {
        fixture.repository.foods["Reis"] = listOf(food("Reis gekocht"))
        fixture.repository.foods["Apfel"] = listOf(food("Apfel frisch"))
        fixture.repository.sourceFoods["Apfel" to FoodSource.Type.FDDB] = emptyList()
        fixture.repository.sourceCounts["Apfel" to FoodSource.Type.FDDB] = MutableStateFlow(0)
        show()
        val field = compose.onNode(hasSetTextAction())
        field.performClick().performTextInput("Reis")
        awaitFood("Reis gekocht")
        // The filters share one scrollable row, so FDDB may start off screen.
        compose.onNode(hasScrollToKeyAction() and hasAnyDescendant(hasText("Alle")))
            .performScrollToKey(FoodFilter.Source.FDDB)
        compose.onNode(hasText("FDDB") and hasClickAction()).performClick().assertIsSelected()
        field.performTextReplacement("Apfel")
        awaitFood("Lebensmittel nicht gefunden")
        compose.onNode(hasText("FDDB") and hasClickAction()).assertIsSelected()
        assertTrue(fixture.events.isEmpty())
    }

    @Test
    fun bothEmptyHistoryTabsShowTheirOwnMessages() {
        show()
        awaitFood("Noch keine kürzlich eingetragenen Lebensmittel")
        compose.onNodeWithText("Noch keine kürzlich eingetragenen Lebensmittel").assertExists()
        compose.onNodeWithText("Zuletzt gesucht").performClick()
        compose.onNodeWithText("Noch keine Suchbegriffe im Verlauf").assertExists()
    }

    @Test
    fun compactItemShowsBrandLabelledNutrientsAndLongPressTogglesFavorite() {
        val egg = FoodSearch.Product(
            id = FoodId.Product(2),
            headline = "Ei, vom Huhn (Naturprodukt)",
            isLiquid = false,
            nutritionFacts = NutritionFacts(
                proteins = NutrientValue.Complete(13.0),
                carbohydrates = NutrientValue.Complete(1.1),
                energy = NutrientValue.Complete(156.0),
                fats = NutrientValue.Complete(11.3),
            ),
            totalWeight = null,
            servingWeight = null,
            isFavorite = false,
            suggestedMeasurement = Measurement.Gram(100.0),
            name = "Ei, vom Huhn",
            brand = "Naturprodukt",
        )
        fixture.repository.foods[null] = listOf(egg)
        var favoriteChange: Pair<FoodId.Product, Boolean>? = null
        show(onFavoriteChange = { id, favorite -> favoriteChange = id to favorite })
        awaitFood("Ei, vom Huhn")

        compose.onNodeWithText("Naturprodukt", substring = true).assertExists()
        compose.onNodeWithText("E 13 g").assertExists()
        compose.onNodeWithText("K 1,1 g").assertExists()
        compose.onNodeWithText("F 11,3 g").assertExists()

        compose.onNodeWithText("Ei, vom Huhn").performTouchInput { longClick() }
        compose.waitForIdle()

        assertEquals(FoodId.Product(2) to true, favoriteChange)
        awaitFood("Zu Favoriten hinzugefügt")
        // Let the snackbar time out so its pending delay does not leak into later tests.
        compose.mainClock.advanceTimeBy(10_000)
        compose.waitForIdle()
        compose.onNodeWithText("Zu Favoriten hinzugefügt").assertDoesNotExist()
    }

    @Test
    fun highlightMarksEveryCaseInsensitiveMatchInBold() {
        val text = "Eierspätzle mit Ei".highlight(listOf("ei"))

        assertEquals(listOf(0 until 2, 16 until 18), text.spanStyles.map { it.start until it.end })
    }

    private fun show(
        restored: String = "",
        parseAmount: Boolean = false,
        onFavoriteChange: (FoodId.Product, Boolean) -> Unit = { _, _ -> },
    ) {
        model = FoodSearchViewModel(null, fixture.preferences, fixture.history, fixture.repository,
            fixture.useCase, fixture.favoriteUseCase, fixture.dateProvider)
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        activity.get().setContent {
            MaterialTheme {
                FoodSearchApp(
                    uiState = model.uiState.collectAsState().value,
                    appState = rememberFoodSearchAppState(searchTextFieldState = rememberTextFieldState(restored)),
                    onSearch = model::search,
                    onQueryChange = model::changeQuery,
                    onRecordSearch = model::recordSearch,
                    onHistoryTabChange = model::changeHistoryTab,
                    onResultsLoaded = model::resultsLoaded,
                    onSourceChange = model::changeSource,
                    onProductFavoriteChange = onFavoriteChange,
                    onFoodClick = { food, measurement ->
                        selected = diarySearchMeasurement(measurement, food.isLiquid, amount)
                    },
                    onUpdateUsdaApiKey = {},
                    onUpdateOpenFoodFactsCredentials = {},
                    transformSearch = { text ->
                        if (parseAmount) {
                            val input = parseDiaryFoodSearchInput(text ?: "")
                            amount = input.amount
                            input.searchText.takeIf { it.isNotBlank() }
                        } else text
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        compose.waitForIdle()
    }

    private fun awaitFood(text: String) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
    }

    private fun food(name: String) = FoodSearch.Product(
        FoodId.Product(1), name, false, NutritionFacts.Empty, null, null, false, Measurement.Gram(100.0),
    )
}

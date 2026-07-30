package com.maksimowiczm.foodyou.app.ui.food.diary.search

import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.common.domain.event.EventBus
import com.maksimowiczm.foodyou.common.domain.event.IntegrationEvent
import com.maksimowiczm.foodyou.fooddiary.domain.entity.Meal
import com.maksimowiczm.foodyou.fooddiary.domain.repository.MealRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalTime

class DiaryFoodSearchViewModelTest {
    @Test
    fun submittedAmountSearchKeepsOriginalTextAndReturnsOnlySearchTerm() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val viewModel = DiaryFoodSearchViewModel(1L, EmptyEventBus, EmptyMealRepository)

        try {
            assertEquals("Nudeln", viewModel.prepareSearch("Nudeln;50"))
            assertEquals(
                DiaryFoodSearchInput(
                    originalText = "Nudeln;50",
                    searchText = "Nudeln",
                    amount = 50,
                ),
                viewModel.searchInput.value,
            )
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun clearAndNormalSearchRemovePreviousAmount() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val viewModel = DiaryFoodSearchViewModel(1L, EmptyEventBus, EmptyMealRepository)

        try {
            viewModel.prepareSearch("Nudeln;50")
            assertEquals("Reis", viewModel.prepareSearch("Reis"))
            assertNull(viewModel.searchInput.value.amount)
            assertEquals("Reis", viewModel.searchInput.value.originalText)

            assertNull(viewModel.prepareSearch(null))
            assertEquals(DiaryFoodSearchInput(), viewModel.searchInput.value)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    private object EmptyEventBus : EventBus {
        override val events: Flow<IntegrationEvent> = emptyFlow()

        override fun publish(integrationEvent: IntegrationEvent) = Unit
    }

    private object EmptyMealRepository : MealRepository {
        override fun observeMeal(mealId: Long): Flow<Meal?> = flowOf(null)

        override fun observeMeals(): Flow<List<Meal>> = flowOf(emptyList())

        override suspend fun insertMealWithLastRank(name: String, from: LocalTime, to: LocalTime) =
            Unit

        override suspend fun deleteMeal(mealId: Long) = Unit

        override suspend fun updateMeal(
            id: Long,
            name: String,
            from: LocalTime,
            to: LocalTime,
        ) = Unit

        override suspend fun reorderMeals(order: List<Long>) = Unit
    }
}

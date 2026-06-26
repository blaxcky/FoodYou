package com.maksimowiczm.foodyou.app.ui.food

import com.maksimowiczm.foodyou.app.ui.food.product.foodProduct
import com.maksimowiczm.foodyou.app.ui.food.recipe.foodRecipe
import com.maksimowiczm.foodyou.app.ui.food.search.FoodSearchViewModel
import com.maksimowiczm.foodyou.app.ui.food.snap.FoodSnapEntryViewModel
import com.maksimowiczm.foodyou.app.ui.food.snap.FoodSnapInboxViewModel
import com.maksimowiczm.foodyou.common.infrastructure.koin.userPreferencesRepository
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel

fun Module.food() {
    viewModel { FoodSnapInboxViewModel(observeEntries = get(), capturePhoto = get()) }
    viewModel { (entryId: Long) ->
        FoodSnapEntryViewModel(
            entryId = entryId,
            observeEntries = get(),
            observeFood = get(),
            mealRepository = get(),
            dateProvider = get(),
            completeEntry = get(),
            deleteEntry = get(),
        )
    }
    viewModel { (excluded: FoodId.Recipe?) ->
        FoodSearchViewModel(
            excludedRecipeId = excluded,
            foodSearchPreferencesRepository = userPreferencesRepository(),
            searchHistoryRepository = get(),
            foodSearchRepository = get(),
            foodSearchUseCase = get(),
            setProductFavoriteUseCase = get(),
            dateProvider = get(),
        )
    }

    foodProduct()
    foodRecipe()
}

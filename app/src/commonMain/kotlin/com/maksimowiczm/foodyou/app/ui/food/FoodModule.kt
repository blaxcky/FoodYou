package com.maksimowiczm.foodyou.app.ui.food

import com.maksimowiczm.foodyou.app.ui.food.product.foodProduct
import com.maksimowiczm.foodyou.app.ui.food.recipe.foodRecipe
import com.maksimowiczm.foodyou.app.ui.food.search.FoodSearchViewModel
import com.maksimowiczm.foodyou.app.ui.food.quickcapture.QuickCaptureCameraViewModel
import com.maksimowiczm.foodyou.app.ui.food.quickcapture.QuickCaptureCsvImporter
import com.maksimowiczm.foodyou.app.ui.food.quickcapture.QuickCaptureCsvImporterImpl
import com.maksimowiczm.foodyou.app.ui.food.quickcapture.QuickCapturePhotoViewModel
import com.maksimowiczm.foodyou.app.ui.food.quickcapture.QuickCaptureViewModel
import com.maksimowiczm.foodyou.common.infrastructure.koin.userPreferencesRepository
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind

fun Module.food() {
    factoryOf(::QuickCaptureCsvImporterImpl).bind<QuickCaptureCsvImporter>()
    viewModel { QuickCaptureCameraViewModel(observe = get(), capture = get()) }
    viewModel {
        QuickCaptureViewModel(
            observe = get(),
            saveEntry = get(),
            capture = get(),
            completeAfter = get(),
            deleteEntries = get(),
            updateLibrary = get(),
            settingsRepository = userPreferencesRepository(),
            csvParser = get(),
            csvImporter = get(),
            savedStateHandle = get(),
        )
    }
    viewModel { (entryId: Long) ->
        QuickCapturePhotoViewModel(
            entryId = entryId,
            observe = get(),
            processPhoto = get(),
            deleteEntries = get(),
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

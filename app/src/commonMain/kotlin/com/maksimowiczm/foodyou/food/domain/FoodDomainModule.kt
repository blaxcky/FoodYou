package com.maksimowiczm.foodyou.food.domain

import com.maksimowiczm.foodyou.common.infrastructure.koin.eventHandlerOf
import com.maksimowiczm.foodyou.food.domain.event.FoodDiaryEntryCreatedEventHandler
import com.maksimowiczm.foodyou.food.domain.usecase.AddPendingProductPhotoUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.AddFddbLinksToQueueUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.CreateProductUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.CompletePendingProductUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.CreatePendingProductUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.CreateRecipeUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.DeleteFddbImportQueueItemUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.DeletePendingProductUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.DeleteFoodUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.DownloadProductUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.FddbDiarySyncUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.ImportFddbProductsUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.ObserveFddbImportQueueUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.ObserveFoodUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.ObserveMeasurementSuggestionsUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.ObservePendingProductUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.ObservePendingProductsUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.UpdateProductUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.UpdateRecipeUseCase
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf

fun Module.foodDomainModule() {
    factoryOf(::AddFddbLinksToQueueUseCase)
    factoryOf(::AddPendingProductPhotoUseCase)
    factoryOf(::CompletePendingProductUseCase)
    factoryOf(::CreatePendingProductUseCase)
    factoryOf(::CreateProductUseCase)
    factoryOf(::CreateRecipeUseCase)
    factoryOf(::DeletePendingProductUseCase)
    factoryOf(::DeleteFoodUseCase)
    factoryOf(::DeleteFddbImportQueueItemUseCase)
    factoryOf(::DownloadProductUseCase)
    factoryOf(::FddbDiarySyncUseCase)
    factory {
        ImportFddbProductsUseCase(
            fddbProductGateway = get(),
            queueRepository = get(),
            productRepository = get(),
            historyRepository = get(),
            transactionProvider = get(),
            dateProvider = get(),
        )
    }
    factoryOf(::ObserveFddbImportQueueUseCase)
    factoryOf(::ObserveFoodUseCase)
    factoryOf(::ObserveMeasurementSuggestionsUseCase)
    factoryOf(::ObservePendingProductUseCase)
    factoryOf(::ObservePendingProductsUseCase)
    factoryOf(::UpdateProductUseCase)
    factoryOf(::UpdateRecipeUseCase)

    eventHandlerOf(::FoodDiaryEntryCreatedEventHandler)
}

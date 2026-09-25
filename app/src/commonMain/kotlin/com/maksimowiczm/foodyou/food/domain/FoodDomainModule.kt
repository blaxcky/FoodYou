package com.maksimowiczm.foodyou.food.domain

import com.maksimowiczm.foodyou.common.infrastructure.koin.eventHandlerOf
import com.maksimowiczm.foodyou.common.infrastructure.koin.applicationCoroutineScope
import com.maksimowiczm.foodyou.food.domain.event.FoodDiaryEntryCreatedEventHandler
import com.maksimowiczm.foodyou.food.domain.usecase.AddPendingProductPhotoUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.AddFddbLinksToQueueUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.CreateProductUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.CompletePendingProductUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.CaptureQuickCapturePhotoUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.CompleteQuickCaptureAfterUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.CreatePendingProductUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.CreateRecipeUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.DeleteFddbImportQueueItemUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.DeletePendingProductUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.DeleteQuickCaptureEntriesUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.DeleteFoodUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.DownloadProductUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.FddbDiarySyncUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.FddbProductSyncCoordinator
import com.maksimowiczm.foodyou.food.domain.usecase.FddbProductSyncForegroundLauncher
import com.maksimowiczm.foodyou.food.domain.usecase.ImportFddbProductsUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.ManualFddbDiarySyncUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.ObserveFddbImportQueueUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.ObserveFoodUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.ObserveMeasurementSuggestionsUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.ObservePendingProductUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.ObservePendingProductsUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.MarkQuickCaptureCompletedUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.ObserveQuickCaptureUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.ProcessQuickCapturePhotoUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.SaveQuickCaptureEntryUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.UpdateQuickCaptureLibraryUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.ResyncFddbProductUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.SetProductFavoriteUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.SetProductQuickCaptureUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.SyncDueFddbProductsUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.SyncFddbProductUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.UnlinkFddbProductUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.UpdateFddbProductLinkUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.UpdateProductUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.UpdateRecipeUseCase
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import com.maksimowiczm.foodyou.common.infrastructure.koin.userPreferencesRepository

fun Module.foodDomainModule() {
    factoryOf(::AddFddbLinksToQueueUseCase)
    factoryOf(::AddPendingProductPhotoUseCase)
    factoryOf(::CompletePendingProductUseCase)
    factoryOf(::CaptureQuickCapturePhotoUseCase)
    factoryOf(::CompleteQuickCaptureAfterUseCase)
    factoryOf(::CreatePendingProductUseCase)
    factoryOf(::CreateProductUseCase)
    factoryOf(::CreateRecipeUseCase)
    factoryOf(::DeletePendingProductUseCase)
    factoryOf(::DeleteQuickCaptureEntriesUseCase)
    factoryOf(::DeleteFoodUseCase)
    factoryOf(::DeleteFddbImportQueueItemUseCase)
    factoryOf(::DownloadProductUseCase)
    factoryOf(::FddbDiarySyncUseCase)
    factory {
        ManualFddbDiarySyncUseCase(
            settingsRepository = userPreferencesRepository(),
            diarySyncUseCase = get(),
        )
    }
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
    factoryOf(::MarkQuickCaptureCompletedUseCase)
    factoryOf(::ObserveQuickCaptureUseCase)
    factoryOf(::ProcessQuickCapturePhotoUseCase)
    factoryOf(::SaveQuickCaptureEntryUseCase)
    factoryOf(::UpdateQuickCaptureLibraryUseCase)
    factoryOf(::ResyncFddbProductUseCase)
    factoryOf(::SetProductFavoriteUseCase)
    factoryOf(::SetProductQuickCaptureUseCase)
    factory {
        SyncFddbProductUseCase(
            statusRepository = get(),
            resyncFddbProductUseCase = get(),
            dateProvider = get(),
            settingsRepository = userPreferencesRepository(),
        )
    }
    factoryOf(::SyncDueFddbProductsUseCase)
    single {
        FddbProductSyncCoordinator(
            settingsRepository = userPreferencesRepository(),
            statusRepository = get(),
            syncDueFddbProductsUseCase = get(),
            syncFddbProductUseCase = get(),
            dateProvider = get(),
        )
    }
    single {
        FddbProductSyncForegroundLauncher(
            applicationScope = applicationCoroutineScope(),
            coordinator = get(),
            logger = get(),
        )
    }
    factoryOf(::UnlinkFddbProductUseCase)
    factoryOf(::UpdateFddbProductLinkUseCase)
    factoryOf(::UpdateProductUseCase)
    factoryOf(::UpdateRecipeUseCase)

    eventHandlerOf(::FoodDiaryEntryCreatedEventHandler)
}

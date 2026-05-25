package com.maksimowiczm.foodyou.food.infrastructure

import com.maksimowiczm.foodyou.food.domain.repository.FddbCredentialsRepository
import com.maksimowiczm.foodyou.food.domain.repository.FddbDiarySyncEntryRepository
import com.maksimowiczm.foodyou.food.domain.repository.FoodHistoryRepository
import com.maksimowiczm.foodyou.food.domain.repository.FddbImportQueueRepository
import com.maksimowiczm.foodyou.food.domain.repository.FoodMeasurementSuggestionRepository
import com.maksimowiczm.foodyou.food.domain.repository.PendingProductRepository
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository
import com.maksimowiczm.foodyou.food.domain.repository.RecipeRepository
import com.maksimowiczm.foodyou.food.domain.repository.RemoteProductRequestFactory
import com.maksimowiczm.foodyou.food.infrastructure.fddb.FddbCredentialsRepositoryImpl
import com.maksimowiczm.foodyou.food.infrastructure.network.RemoteProductMapper
import com.maksimowiczm.foodyou.food.infrastructure.repository.RoomFddbDiarySyncEntryRepository
import com.maksimowiczm.foodyou.food.infrastructure.network.RemoteProductRequestFactoryImpl
import com.maksimowiczm.foodyou.food.infrastructure.openfoodfacts.openFoodFactsModule
import com.maksimowiczm.foodyou.food.infrastructure.fddb.fddbModule
import com.maksimowiczm.foodyou.food.infrastructure.repository.RoomFoodHistoryRepository
import com.maksimowiczm.foodyou.food.infrastructure.repository.RoomFddbImportQueueRepository
import com.maksimowiczm.foodyou.food.infrastructure.repository.RoomFoodMeasurementSuggestionRepository
import com.maksimowiczm.foodyou.food.infrastructure.repository.RoomPendingProductRepository
import com.maksimowiczm.foodyou.food.infrastructure.repository.RoomProductRepository
import com.maksimowiczm.foodyou.food.infrastructure.repository.RoomRecipeRepository
import com.maksimowiczm.foodyou.food.infrastructure.room.FoodDatabase
import com.maksimowiczm.foodyou.food.infrastructure.usda.USDAModule
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.scope.Scope
import org.koin.dsl.bind

fun Module.foodInfrastructureModule() {
    factory { database.fddbDiarySyncEntryDao }
    factory { database.fddbImportQueueDao }
    factory { database.foodEventDao }
    factory { database.measurementSuggestionDao }
    factory { database.pendingProductDao }
    factory { database.productDao }
    factory { database.recipeDao }

    factoryOf(::RoomFddbDiarySyncEntryRepository).bind<FddbDiarySyncEntryRepository>()
    factoryOf(::RoomFoodHistoryRepository).bind<FoodHistoryRepository>()
    factoryOf(::RoomFddbImportQueueRepository).bind<FddbImportQueueRepository>()
    factoryOf(::RoomFoodMeasurementSuggestionRepository).bind<FoodMeasurementSuggestionRepository>()
    factoryOf(::RoomPendingProductRepository).bind<PendingProductRepository>()
    factoryOf(::RoomProductRepository).bind<ProductRepository>()
    factoryOf(::RoomRecipeRepository).bind<RecipeRepository>()
    factoryOf(::FddbCredentialsRepositoryImpl).bind<FddbCredentialsRepository>()

    factoryOf(::RemoteProductRequestFactoryImpl).bind<RemoteProductRequestFactory>()
    factoryOf(::RemoteProductMapper)

    pendingProductPhotoStorageModule()
    USDAModule()
    openFoodFactsModule()
    fddbModule()
}

private val Scope.database: FoodDatabase
    get() = get()

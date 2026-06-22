package com.maksimowiczm.foodyou.food.infrastructure.room

interface FoodDatabase {
    val fddbDiarySyncEntryDao: FddbDiarySyncEntryDao
    val fddbImportQueueDao: FddbImportQueueDao
    val fddbProductSyncStatusDao: FddbProductSyncStatusDao
    val productDao: ProductDao
    val pendingProductDao: PendingProductDao
    val recipeDao: RecipeDao
    val foodEventDao: FoodEventDao
    val measurementSuggestionDao: MeasurementSuggestionDao
    val productPortionDao: ProductPortionDao
}

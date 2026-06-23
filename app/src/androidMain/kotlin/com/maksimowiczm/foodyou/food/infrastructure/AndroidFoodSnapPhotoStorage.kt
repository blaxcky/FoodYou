package com.maksimowiczm.foodyou.food.infrastructure

import android.content.Context
import com.maksimowiczm.foodyou.food.domain.repository.FoodSnapPhotoStorage
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.bind

internal actual fun Module.foodSnapPhotoStorageModule() {
    factory { AndroidFoodSnapPhotoStorage(androidContext()) }.bind<FoodSnapPhotoStorage>()
}

private class AndroidFoodSnapPhotoStorage(private val context: Context) : FoodSnapPhotoStorage {
    override fun delete(photoPath: String) {
        context.filesDir.resolve(FOOD_SNAP_PHOTO_DIRECTORY).resolve(photoPath).delete()
    }
}

internal const val FOOD_SNAP_PHOTO_DIRECTORY = "food-snap-photos"

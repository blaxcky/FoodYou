package com.maksimowiczm.foodyou.food.infrastructure

import android.content.Context
import com.maksimowiczm.foodyou.food.domain.repository.PendingProductPhotoStorage
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.bind

internal actual fun Module.pendingProductPhotoStorageModule() {
    factory { AndroidPendingProductPhotoStorage(androidContext()) }.bind<PendingProductPhotoStorage>()
}

private class AndroidPendingProductPhotoStorage(private val context: Context) :
    PendingProductPhotoStorage {
    override fun delete(photoPath: String) {
        context.filesDir.resolve(PENDING_PRODUCT_PHOTO_DIRECTORY).resolve(photoPath).delete()
    }
}

internal const val PENDING_PRODUCT_PHOTO_DIRECTORY = "pending-product-photos"

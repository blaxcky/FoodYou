package com.maksimowiczm.foodyou.food.infrastructure

import com.maksimowiczm.foodyou.food.domain.repository.PendingProductPhotoStorage
import org.koin.core.module.Module
import org.koin.dsl.bind

internal actual fun Module.pendingProductPhotoStorageModule() {
    factory { NoOpPendingProductPhotoStorage() }.bind<PendingProductPhotoStorage>()
}

private class NoOpPendingProductPhotoStorage : PendingProductPhotoStorage {
    override fun delete(photoPath: String) = Unit
}

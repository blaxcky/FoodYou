package com.maksimowiczm.foodyou.food.domain.repository

interface PendingProductPhotoStorage {
    fun delete(photoPath: String)
}

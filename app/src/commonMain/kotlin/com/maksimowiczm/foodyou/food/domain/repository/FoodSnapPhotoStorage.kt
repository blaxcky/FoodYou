package com.maksimowiczm.foodyou.food.domain.repository

/** Owns the private files captured for the active FoodSnap session. */
interface FoodSnapPhotoStorage {
    fun delete(photoPath: String)
}

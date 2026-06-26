package com.maksimowiczm.foodyou.food.domain.usecase

import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository

class SetProductFavoriteUseCase(private val productRepository: ProductRepository) {
    suspend fun setFavorite(id: FoodId.Product, isFavorite: Boolean) {
        productRepository.setProductFavorite(id, isFavorite)
    }
}

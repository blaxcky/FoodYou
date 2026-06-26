package com.maksimowiczm.foodyou.food.domain.usecase

import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository

class SetProductQuickCaptureUseCase(private val productRepository: ProductRepository) {
    suspend fun setQuickCapture(id: FoodId.Product, isQuickCapture: Boolean) {
        productRepository.setProductQuickCapture(id, isQuickCapture)
    }
}

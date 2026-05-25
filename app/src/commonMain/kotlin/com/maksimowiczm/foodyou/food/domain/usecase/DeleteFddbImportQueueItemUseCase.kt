package com.maksimowiczm.foodyou.food.domain.usecase

import com.maksimowiczm.foodyou.food.domain.repository.FddbImportQueueRepository

class DeleteFddbImportQueueItemUseCase(private val repository: FddbImportQueueRepository) {
    suspend operator fun invoke(id: Long) = repository.delete(id)
}

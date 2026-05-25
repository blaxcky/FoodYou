package com.maksimowiczm.foodyou.food.domain.usecase

import com.maksimowiczm.foodyou.food.domain.repository.FddbImportQueueRepository

class ObserveFddbImportQueueUseCase(private val repository: FddbImportQueueRepository) {
    operator fun invoke() = repository.observeQueue()
}

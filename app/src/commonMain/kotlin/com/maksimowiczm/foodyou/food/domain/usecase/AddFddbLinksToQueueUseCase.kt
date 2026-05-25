package com.maksimowiczm.foodyou.food.domain.usecase

import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.food.domain.repository.FddbImportQueueRepository

class AddFddbLinksToQueueUseCase(
    private val repository: FddbImportQueueRepository,
    private val dateProvider: DateProvider,
) {
    suspend operator fun invoke(text: String): Int {
        val links = FddbLinkExtractor.extractLinks(text)
        val now = dateProvider.nowInstant()
        links.forEach { repository.add(url = it, createdAt = now) }
        return links.size
    }

    fun countLinks(text: String): Int = FddbLinkExtractor.extractLinks(text).size
}

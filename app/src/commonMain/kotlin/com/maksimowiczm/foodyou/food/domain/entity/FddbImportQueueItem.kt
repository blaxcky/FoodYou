package com.maksimowiczm.foodyou.food.domain.entity

import kotlin.time.Instant

data class FddbImportQueueItem(
    val id: Long,
    val url: String,
    val createdAt: Instant,
    val lastAttemptedAt: Instant?,
    val lastError: String?,
)

package com.maksimowiczm.foodyou.food.domain.entity

import kotlin.time.Instant

data class PendingProduct(
    val id: Long,
    val barcode: String?,
    val photoPaths: List<String>,
    val createdAt: Instant,
    val updatedAt: Instant?,
)

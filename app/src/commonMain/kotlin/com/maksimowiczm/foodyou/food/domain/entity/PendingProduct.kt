package com.maksimowiczm.foodyou.food.domain.entity

import kotlinx.datetime.Instant

data class PendingProduct(
    val id: Long,
    val barcode: String?,
    val photoPaths: List<String>,
    val createdAt: Instant,
    val updatedAt: Instant?,
)

package com.maksimowiczm.foodyou.common.auth

import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Session(
    val userId: String,
    val userEmail: String,
    val accessToken: String,
    val expiresAt: Instant,
)

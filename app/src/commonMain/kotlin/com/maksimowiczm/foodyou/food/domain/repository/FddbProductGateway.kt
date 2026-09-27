package com.maksimowiczm.foodyou.food.domain.repository

import com.maksimowiczm.foodyou.food.domain.entity.FddbProduct

interface FddbProductGateway {
    suspend fun getProduct(url: String, priority: FddbRequestPriority = FddbRequestPriority.Normal): FddbProduct
}

enum class FddbRequestPriority { Normal, Diary }

class FddbAccessBlockedException(
    message: String,
    val retryAfterMillis: Long? = null,
) : Exception(message)

class FddbHttpException(val statusCode: Int) :
    Exception("FDDB request failed with HTTP $statusCode")

class FddbParseException(message: String) : Exception(message)

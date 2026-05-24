package com.maksimowiczm.foodyou.food.domain.repository

import com.maksimowiczm.foodyou.food.domain.entity.FddbProduct

interface FddbProductGateway {
    suspend fun getProduct(url: String): FddbProduct
}

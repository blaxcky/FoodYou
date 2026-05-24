package com.maksimowiczm.foodyou.food.infrastructure.fddb

import com.maksimowiczm.foodyou.food.domain.repository.FddbProductGateway
import com.maksimowiczm.foodyou.food.domain.entity.FddbProduct
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders

internal class FddbRemoteDataSource(
    private val client: HttpClient,
    private val parser: FddbProductParser,
) : FddbProductGateway {
    override suspend fun getProduct(url: String): FddbProduct {
        val response =
            client.get(url) {
                header(HttpHeaders.UserAgent, "FoodYou FDDB import")
            }
        return parser.parse(response.bodyAsText())
    }
}

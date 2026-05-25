package com.maksimowiczm.foodyou.food.infrastructure.fddb

import com.maksimowiczm.foodyou.common.config.NetworkConfig
import com.maksimowiczm.foodyou.food.domain.repository.FddbProductGateway
import com.maksimowiczm.foodyou.food.domain.entity.FddbProduct
import com.maksimowiczm.foodyou.food.domain.repository.FddbAccessBlockedException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.userAgent

internal class FddbRemoteDataSource(
    private val client: HttpClient,
    private val parser: FddbProductParser,
    private val networkConfig: NetworkConfig,
) : FddbProductGateway {
    override suspend fun getProduct(url: String): FddbProduct {
        val response =
            client.get(url) {
                userAgent(networkConfig.userAgent)
            }
        val html = response.bodyAsText()

        if (response.status.isFddbBlockStatus()) {
            throw FddbAccessBlockedException(
                message = "FDDB request blocked (${response.status.value})",
                retryAfterMillis = response.headers[HttpHeaders.RetryAfter]?.toRetryAfterMillis(),
            )
        }

        if (html.isFddbBlockPage()) {
            throw FddbAccessBlockedException(message = "FDDB request blocked")
        }

        return parser.parse(html)
    }
}

private fun HttpStatusCode.isFddbBlockStatus(): Boolean =
    this == HttpStatusCode.TooManyRequests ||
        this == HttpStatusCode.Forbidden ||
        this == HttpStatusCode.ServiceUnavailable

private fun String.toRetryAfterMillis(): Long? =
    trim().toLongOrNull()?.coerceAtLeast(0)?.times(1_000)

private fun String.isFddbBlockPage(): Boolean {
    val lower = lowercase()
    return listOf("captcha", "too many requests", "rate limit", "access denied", "blocked")
        .any { it in lower }
}

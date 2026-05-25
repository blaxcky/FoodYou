package com.maksimowiczm.foodyou.food.infrastructure.fddb

import com.maksimowiczm.foodyou.common.config.NetworkConfig
import com.maksimowiczm.foodyou.food.domain.entity.FddbDiaryEntry
import com.maksimowiczm.foodyou.food.domain.repository.FddbCredentialsRepository
import com.maksimowiczm.foodyou.food.domain.repository.FddbDiaryGateway
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.userAgent
import kotlinx.datetime.LocalDate

internal class FddbDiaryRemoteDataSource(
    private val client: HttpClient,
    private val parser: FddbDiaryParser,
    private val credentialsRepository: FddbCredentialsRepository,
    private val networkConfig: NetworkConfig,
) : FddbDiaryGateway {
    override suspend fun login(username: String, password: String) {
        val response =
            client.submitForm(
                url = "https://fddb.info/db/i18n/account/?lang=de&action=login",
                formParameters =
                    Parameters.build {
                        append("loginemailorusername", username)
                        append("loginpassword", password)
                        append("returnurl", "/db/i18n/notepad/?lang=de")
                    },
            ) {
                userAgent(networkConfig.userAgent)
            }
        val body = response.bodyAsText()
        if (response.status.value !in 200..399 || body.contains("loginpassword")) {
            error("FDDB login failed")
        }
    }

    override suspend fun getLastSevenDays(referenceDate: LocalDate): List<FddbDiaryEntry> {
        val credentials = credentialsRepository.loadCredentials() ?: error("FDDB credentials missing")
        login(credentials.first, credentials.second)
        val response =
            client.get("https://fddb.info/db/i18n/notepad/") {
                userAgent(networkConfig.userAgent)
                parameter("lang", "de")
                parameter("action", "npfe")
                parameter("mode", "1")
                parameter("option", "4")
            }

        return parser.parse(response.bodyAsText(), referenceDate)
    }
}

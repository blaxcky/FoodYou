package com.maksimowiczm.foodyou.food.infrastructure.fddb

import com.maksimowiczm.foodyou.common.config.NetworkConfig
import com.maksimowiczm.foodyou.food.domain.repository.FddbCredentialsRepository
import com.maksimowiczm.foodyou.food.domain.repository.FddbRequestPriority
import com.maksimowiczm.foodyou.sync.SyncLog
import com.maksimowiczm.foodyou.sync.recordActiveSyncStep
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.userAgent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One account/session owner across all gateways, credential changes, and backup restores. */
internal class FddbSessionManager(
    private val client: HttpClient,
    private val cookies: FddbCookieStorage,
    private val credentials: FddbCredentialStore,
    private val networkConfig: NetworkConfig,
    private val requestQueue: FddbRequestQueue,
    private val syncLog: SyncLog? = null,
) : FddbCredentialsRepository {
    private val mutex = Mutex()

    override fun hasCredentials(): Flow<Boolean> = credentials.hasCredentials

    override suspend fun loadCredentials(): Pair<String, String>? = mutex.withLock {
        credentials.load()?.let { it.login to it.password }
    }

    override suspend fun store(login: String, password: String) = mutex.withLock {
        credentials.store(login, password)
        cookies.discard()
    }

    override suspend fun clear() = mutex.withLock {
        credentials.clear()
        cookies.discard()
    }

    /** Verifies the account before replacing credentials; the dialog must not store them again. */
    suspend fun login(login: String, password: String) = mutex.withLock {
        cookies.discard()
        try {
            performLogin(login, password)
            val response = fetchDiary()
            requireDiary(response)
            val revision = credentials.store(login, password)
            persistSession(revision)
        } catch (error: Exception) {
            discardAfterFailure(error)
            throw error
        }
    }

    suspend fun getDiaryHtml(): String = mutex.withLock {
        val account = credentials.load() ?: error("FDDB-Zugangsdaten fehlen")
        val availability = cookies.restoreFor(account.revision)
        sessionEvent(when (availability) {
            FddbSessionAvailability.Missing -> "FDDB: Anmeldung erforderlich"
            FddbSessionAvailability.Reused -> "FDDB: Sitzung wiederverwendet"
            FddbSessionAvailability.Restored -> "FDDB: Sitzung wiederhergestellt"
        })
        var loggedIn = false
        if (availability == FddbSessionAvailability.Missing) {
            cookies.discard()
            loginForSync(account)
            loggedIn = true
        }
        var response = fetchDiary()
        if (response.page == FddbPage.AuthenticationRequired && !loggedIn) {
            sessionEvent("FDDB: Sitzung abgelaufen")
            cookies.discard()
            loginForSync(account)
            response = fetchDiary()
        }
        if (response.page == FddbPage.AuthenticationRequired) cookies.discard()
        requireDiary(response)
        persistSession(account.revision)
        response.html
    }

    private suspend fun loginForSync(account: FddbStoredCredentials) {
        try { performLogin(account.login, account.password) }
        catch (error: Exception) {
            discardAfterFailure(error)
            throw error
        }
    }

    private suspend fun performLogin(login: String, password: String) {
        syncLog.recordActiveSyncStep("FDDB: Anmelden") {
            requestQueue.execute(FddbRequestPriority.Diary) {
                val response = client.submitForm(
                    url = "https://fddb.info/db/i18n/account/?lang=de&action=login",
                    formParameters = Parameters.build {
                        append("loginemailorusername", login)
                        append("loginpassword", password)
                        append("returnurl", "/db/i18n/notepad/?lang=de")
                    },
                ) { userAgent(networkConfig.userAgent) }
                val body = response.bodyAsText()
                if (response.status.value !in 200..399) throw FddbUnexpectedResponseException()
                if (containsFddbLoginForm(body)) throw FddbAuthenticationException()
                // A fresh diary GET proves success, including responses without a login form.
            }
        }
    }

    private suspend fun fetchDiary(): DiaryResponse = syncLog.recordActiveSyncStep("FDDB: Tagebuch abrufen") {
        requestQueue.execute(FddbRequestPriority.Diary) {
            val response = client.get("https://fddb.info/db/i18n/notepad/") {
                userAgent(networkConfig.userAgent)
                parameter("lang", "de")
                parameter("action", "npfe")
                parameter("mode", "1")
                parameter("option", "4")
            }
            val html = response.bodyAsText()
            DiaryResponse(classifyFddbDiaryResponse(response.status.value, response.call.request.url, html), html)
        }
    }

    private fun requireDiary(response: DiaryResponse) {
        when (response.page) {
            FddbPage.Diary -> Unit
            FddbPage.AuthenticationRequired -> throw FddbAuthenticationException()
            FddbPage.Unexpected -> throw FddbUnexpectedResponseException()
        }
    }

    private suspend fun persistSession(revision: String) {
        syncLog.recordActiveSyncStep("FDDB: Sitzung speichern") {
            val saved = cookies.persistFor(revision)
            if (!saved) sessionEvent("FDDB: Sitzung nur im Arbeitsspeicher verfügbar")
        }
    }

    private suspend fun sessionEvent(title: String) = syncLog.recordActiveSyncStep(title) { Unit }

    private suspend fun discardAfterFailure(error: Exception) {
        // Don't replace the caller's cancellation with cache-cleanup errors.
        if (error !is CancellationException) cookies.discard()
    }

    private class DiaryResponse(val page: FddbPage, val html: String)
}

package com.maksimowiczm.foodyou.food.infrastructure.fddb

import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.edit
import com.maksimowiczm.foodyou.common.config.NetworkConfig
import com.maksimowiczm.foodyou.sync.SyncLog
import com.maksimowiczm.foodyou.sync.SyncLogRun
import com.maksimowiczm.foodyou.sync.SyncLogStatus
import com.maksimowiczm.foodyou.sync.SyncLogStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.HttpResponseData
import io.ktor.http.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.*

class FddbSessionManagerTest {
    @Test
    fun firstLoginThenReuseAndRestartOnlyFetchFreshDiary() = runTest {
        val state = SharedState()
        val first = fixture(state) { request ->
            if (request.method == HttpMethod.Post) loginResponse()
            else {
                assertTrue(request.headers[HttpHeaders.Cookie].orEmpty().contains("session=secret-session"))
                respond(Diary, headers = htmlHeaders())
            }
        }
        assertEquals(Diary, first.manager.getDiaryHtml())
        assertEquals(Diary, first.manager.getDiaryHtml())
        assertEquals(listOf(HttpMethod.Post, HttpMethod.Get, HttpMethod.Get), first.requests.map { it.method })
        assertEquals(1, state.sessions.writes)
        val ciphertext = state.sessions.state.value[SessionKey]!!
        assertFalse(ciphertext.decodeToString().contains("secret-session"))
        first.client.close()

        val restored = fixture(state) { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertTrue(request.headers[HttpHeaders.Cookie].orEmpty().contains("secret-session"))
            respond(Diary, headers = htmlHeaders())
        }
        assertEquals(Diary, restored.manager.getDiaryHtml())
        assertEquals(1, restored.requests.size)
        assertEquals(1, state.sessions.writes)
        restored.client.close()
    }

    @Test
    fun expiredGuestHttp200RenewsOnceAndReplacesCookie() = runTest {
        var expired = false
        var renewed = false
        val f = fixture { request ->
            if (request.method == HttpMethod.Post) {
                renewed = expired
                loginResponse(if (renewed) "renewed-session" else "secret-session")
            } else if (expired && !renewed) respond(Guest, headers = htmlHeaders())
            else respond(Diary, headers = htmlHeaders())
        }
        f.manager.getDiaryHtml()
        f.requests.clear()
        expired = true
        assertEquals(Diary, f.manager.getDiaryHtml())
        assertEquals(listOf(HttpMethod.Get, HttpMethod.Post, HttpMethod.Get), f.requests.map { it.method })
        assertTrue(f.requests.last().headers[HttpHeaders.Cookie].orEmpty().contains("renewed-session"))
        assertNull(f.requests[1].headers[HttpHeaders.Cookie])
        f.client.close()
    }

    @Test
    fun loginRedirectIsAcceptedButDiaryStillProvesAuthentication() = runTest {
        val f = fixture { request ->
            if (request.method == HttpMethod.Post) respond("", HttpStatusCode.Found, headersOf(
                HttpHeaders.Location to listOf("/db/i18n/notepad/?lang=de"),
                HttpHeaders.SetCookie to listOf("session=secret-session; Path=/; Secure; HttpOnly"),
            )) else respond(EmptyDiary, headers = htmlHeaders())
        }
        assertEquals(EmptyDiary, f.manager.getDiaryHtml())
        assertEquals(1, f.requests.count { it.method == HttpMethod.Post })
        f.client.close()
    }

    @Test
    fun loginPageRedirectTriggersRenewal() = runTest {
        var expired = false
        var renewed = false
        val f = fixture { request ->
            when {
                request.method == HttpMethod.Post -> {
                    renewed = expired
                    loginResponse()
                }
                request.url.encodedPath.contains("/account/") -> respond(LoginForm, headers = htmlHeaders())
                expired && !renewed -> respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location,
                    "https://fddb.info/db/i18n/account/?lang=de&action=login"))
                else -> respond(Diary, headers = htmlHeaders())
            }
        }
        f.manager.getDiaryHtml()
        f.requests.clear()
        expired = true
        f.manager.getDiaryHtml()
        assertEquals(1, f.requests.count { it.method == HttpMethod.Post })
        assertTrue(f.requests.any { it.url.encodedPath.contains("/account/") && it.method == HttpMethod.Get })
        f.client.close()
    }

    @Test
    fun unsuccessfulRenewalNeverLoopsOrReplacesCredentials() = runTest {
        var loginValid = true
        val f = fixture { request ->
            if (request.method == HttpMethod.Post) {
                if (loginValid) loginResponse() else respond(LoginForm, headers = htmlHeaders())
            } else respond(if (loginValid) Diary else Guest, headers = htmlHeaders())
        }
        f.manager.getDiaryHtml()
        loginValid = false
        f.requests.clear()
        assertFailsWith<FddbAuthenticationException> { f.manager.getDiaryHtml() }
        assertEquals(listOf(HttpMethod.Get, HttpMethod.Post), f.requests.map { it.method })
        assertEquals("user" to "password", f.manager.loadCredentials())
        assertNull(f.state.sessions.state.value[SessionKey])
        f.client.close()
    }

    @Test
    fun missingSessionWithUnauthenticatedDiaryOnlyLogsInOnce() = runTest {
        val f = fixture { request ->
            if (request.method == HttpMethod.Post) loginResponse() else respond(Guest, headers = htmlHeaders())
        }
        assertFailsWith<FddbAuthenticationException> { f.manager.getDiaryHtml() }
        assertEquals(listOf(HttpMethod.Post, HttpMethod.Get), f.requests.map { it.method })
        assertNull(f.state.sessions.state.value[SessionKey])
        f.client.close()
    }

    @Test
    fun errorsAndBlocksDoNotLoginAgainOrEraseValidSession() = runTest {
        var mode = "valid"
        val f = fixture { request ->
            if (request.method == HttpMethod.Post) loginResponse() else when (mode) {
                "valid" -> respond(Diary, headers = htmlHeaders())
                "offline" -> error("Network unavailable")
                "unknown" -> respond("<html>Wartungsseite</html>", headers = htmlHeaders())
                else -> respond("Fehler", HttpStatusCode.fromValue(mode.toInt()), htmlHeaders())
            }
        }
        f.manager.getDiaryHtml()
        val before = f.state.sessions.state.value
        for (next in listOf("403", "429", "500", "unknown", "offline")) {
            mode = next
            f.requests.clear()
            assertFails { f.manager.getDiaryHtml() }
            assertEquals(listOf(HttpMethod.Get), f.requests.map { it.method })
            assertEquals(before, f.state.sessions.state.value)
        }
        f.client.close()
    }

    @Test
    fun explicitLoginVerifiesAndStoresWithoutSecondCredentialWrite() = runTest {
        val f = fixture { request ->
            if (request.method == HttpMethod.Post) {
                assertNull(request.headers[HttpHeaders.Cookie])
                assertEquals("new-user", (request.body as FormDataContent).formData["loginemailorusername"])
                loginResponse("new-session")
            } else respond(EmptyDiary, headers = htmlHeaders())
        }
        val previousRevision = f.credentialStore.load()!!.revision
        f.manager.login("new-user", "new-password")
        assertEquals("new-user" to "new-password", f.manager.loadCredentials())
        assertNotEquals(previousRevision, f.credentialStore.load()!!.revision)
        f.requests.clear()
        f.manager.getDiaryHtml()
        assertEquals(listOf(HttpMethod.Get), f.requests.map { it.method })
        assertTrue(f.requests.single().headers[HttpHeaders.Cookie].orEmpty().contains("new-session"))
        f.client.close()
    }

    @Test
    fun failedExplicitLoginKeepsOldCredentials() = runTest {
        val f = fixture { request ->
            if (request.method == HttpMethod.Post) respond(LoginForm, headers = htmlHeaders())
            else respond(Guest, headers = htmlHeaders())
        }
        val oldRevision = f.credentialStore.load()!!.revision
        assertFailsWith<FddbAuthenticationException> { f.manager.login("wrong-user", "wrong-password") }
        assertEquals("user" to "password", f.manager.loadCredentials())
        assertEquals(oldRevision, f.credentialStore.load()!!.revision)
        f.client.close()
    }

    @Test
    fun directCredentialChangesAndClearInvalidateSessions() = runTest {
        val f = fixture { request ->
            if (request.method == HttpMethod.Post) loginResponse() else respond(Diary, headers = htmlHeaders())
        }
        f.manager.getDiaryHtml()
        val oldRevision = f.credentialStore.load()!!.revision
        f.manager.store("other-user", "other-password")
        assertNull(f.state.sessions.state.value[SessionKey])
        assertNotEquals(oldRevision, f.credentialStore.load()!!.revision)
        f.requests.clear()
        f.manager.getDiaryHtml()
        assertEquals(listOf(HttpMethod.Post, HttpMethod.Get), f.requests.map { it.method })
        assertNull(f.requests.first().headers[HttpHeaders.Cookie])
        f.manager.clear()
        assertNull(f.manager.loadCredentials())
        assertFalse(f.manager.hasCredentials().first())
        assertNull(f.state.sessions.state.value[SessionKey])
        assertTrue(f.cookies.get(Url("https://fddb.info/db/i18n/notepad/")).isEmpty())
        f.requests.clear()
        assertFails { f.manager.getDiaryHtml() }
        assertTrue(f.requests.isEmpty())
        f.client.close()
    }

    @Test
    fun concurrentAbrufsShareOneLoginAndCredentialChangesWait() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val f = fixture { request ->
            if (request.method == HttpMethod.Post) {
                started.complete(Unit)
                release.await()
                loginResponse()
            } else respond(Diary, headers = htmlHeaders())
        }
        val first = async { f.manager.getDiaryHtml() }
        started.await()
        val second = async { f.manager.getDiaryHtml() }
        runCurrent()
        assertEquals(1, f.requests.size)
        release.complete(Unit)
        first.await(); second.await()
        assertEquals(listOf(HttpMethod.Post, HttpMethod.Get, HttpMethod.Get), f.requests.map { it.method })
        f.client.close()
    }

    @Test
    fun credentialClearWaitsForActiveRequestAndNoSnapshotReappearsAfterwards() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val f = fixture { request ->
            if (request.method == HttpMethod.Post) loginResponse() else {
                started.complete(Unit)
                release.await()
                respond(Diary, headers = htmlHeaders())
            }
        }
        val sync = async { f.manager.getDiaryHtml() }
        started.await()
        val clear = async { f.manager.clear() }
        runCurrent()
        assertFalse(clear.isCompleted)
        release.complete(Unit)
        sync.await(); clear.await()
        assertNull(f.state.sessions.state.value[SessionKey])
        assertNull(f.manager.loadCredentials())
        f.client.close()
    }

    @Test
    fun corruptSnapshotAndWriteFailureDoNotPreventSuccessfulSync() = runTest {
        val state = SharedState()
        state.sessions.edit { it[SessionKey] = byteArrayOf(0, 1, 2) }
        val f = fixture(state) { request ->
            if (request.method == HttpMethod.Post) loginResponse() else respond(Diary, headers = htmlHeaders())
        }
        assertEquals(Diary, f.manager.getDiaryHtml())
        assertEquals(1, f.requests.count { it.method == HttpMethod.Post })
        f.manager.store("user", "password")
        state.sessions.failWrites = true
        f.requests.clear()
        assertEquals(Diary, f.manager.getDiaryHtml())
        assertEquals(Diary, f.manager.getDiaryHtml())
        assertEquals(listOf(HttpMethod.Post, HttpMethod.Get, HttpMethod.Get), f.requests.map { it.method })
        f.client.close()
    }

    @Test
    fun cancellationReleasesMutexAndDoesNotPersistUnverifiedSession() = runTest {
        val started = CompletableDeferred<Unit>()
        var cancelled = false
        val f = fixture { request ->
            if (request.method == HttpMethod.Post && !cancelled) {
                started.complete(Unit)
                awaitCancellation()
            } else if (request.method == HttpMethod.Post) loginResponse()
            else respond(Diary, headers = htmlHeaders())
        }
        val job = launch { f.manager.getDiaryHtml() }
        started.await()
        job.cancelAndJoin()
        assertNull(f.state.sessions.state.value[SessionKey])
        cancelled = true
        assertEquals(Diary, f.manager.getDiaryHtml())
        f.client.close()
    }

    @Test
    fun logsExplainReuseRestorationAndExpirationWithoutSecrets() = runTest {
        val state = SharedState()
        val store = object : SyncLogStore {
            override val runs = MutableStateFlow<List<SyncLogRun>>(emptyList())
            override suspend fun update(transform: (List<SyncLogRun>) -> List<SyncLogRun>) { runs.value = transform(runs.value) }
        }
        val log = SyncLog(store)
        var expired = false
        val f = fixture(state, log) { request ->
            if (request.method == HttpMethod.Post) { expired = false; loginResponse() }
            else respond(if (expired) Guest else Diary, headers = htmlHeaders())
        }
        log.run("Sync") { f.manager.getDiaryHtml() }
        log.run("Sync") { f.manager.getDiaryHtml() }
        expired = true
        log.run("Sync") { f.manager.getDiaryHtml() }
        f.client.close()
        val restored = fixture(state, log) { respond(Diary, headers = htmlHeaders()) }
        log.run("Sync") { restored.manager.getDiaryHtml() }
        val titles = store.runs.value.flatMap { it.steps }.map { it.title }
        assertTrue(titles.containsAll(listOf("FDDB: Anmeldung erforderlich", "FDDB: Sitzung wiederverwendet",
            "FDDB: Sitzung abgelaufen", "FDDB: Sitzung wiederhergestellt", "FDDB: Anmelden", "FDDB: Tagebuch abrufen")))
        assertTrue(store.runs.value.all { it.status == SyncLogStatus.Success })
        val serialized = store.runs.value.toString()
        assertFalse(serialized.contains("secret-session"))
        assertFalse(serialized.contains("password"))
        restored.client.close()
    }

    @Test
    fun gatewayParsesAuthenticatedDiaryAndAcceptsEmptyDiary() = runTest {
        var empty = false
        val f = fixture { request ->
            if (request.method == HttpMethod.Post) loginResponse()
            else respond(if (empty) EmptyDiary else Diary, headers = htmlHeaders())
        }
        val gateway = FddbDiaryRemoteDataSource(f.manager, FddbDiaryParser())
        assertEquals("123", gateway.getLastSevenDays(LocalDate(2026, 10, 3)).single().entryId)
        empty = true
        assertTrue(gateway.getLastSevenDays(LocalDate(2026, 10, 3)).isEmpty())
        f.client.close()
    }

    private suspend fun fixture(
        state: SharedState = SharedState(),
        log: SyncLog? = null,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): Fixture {
        val store = FddbCredentialStore(state.credentials, state.crypto)
        if (store.load() == null) store.store("user", "password")
        return Fixture(state, store, log, handler)
    }

    private class SharedState {
        val credentials = SessionTestDataStore()
        val sessions = SessionTestDataStore()
        val crypto = SessionTestCrypto()
    }

    private class Fixture(
        val state: SharedState,
        val credentialStore: FddbCredentialStore,
        log: SyncLog?,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) {
        val requests = mutableListOf<HttpRequestData>()
        val cookies = FddbCookieStorage(state.sessions, state.crypto)
        val client = HttpClient(MockEngine { request -> requests += request; handler(request) }) {
            install(HttpCookies) { storage = cookies }
        }
        val manager = FddbSessionManager(client, cookies, credentialStore,
            object : NetworkConfig { override val userAgent = "FoodYou-test" }, FddbRequestQueue(log), log)
    }

    private fun MockRequestHandleScope.loginResponse(session: String = "secret-session") =
        respond("<html>Anmeldung erfolgreich</html>", headers = headersOf(
            HttpHeaders.ContentType to listOf("text/html"),
            HttpHeaders.SetCookie to listOf("session=$session; Path=/; Secure; HttpOnly"),
        ))

    private fun htmlHeaders() = headersOf(HttpHeaders.ContentType, "text/html")

    private companion object {
        val SessionKey = byteArrayPreferencesKey("fddb:sessionV1")
        const val Guest = "<html>Bereits bei Fddb registriert? <a>Melde Dich mit Deinen Zugangsdaten an</a></html>"
        const val LoginForm = "<html><input type='password' name='loginpassword'></html>"
        const val EmptyDiary = "<html><a href='/db/i18n/account/?lang=de&amp;action=logout'>Abmelden</a><p>Keine Einträge</p></html>"
        const val Diary = """<td class=notepaddate><h3>Samstag 3. Oktober</h3></td><h4>Morgens</h4>
            <tr id='np123'><td><a href='/db/de/lebensmittel/apfel/index.html'>100 g Apfel</a></td></tr>"""
    }
}

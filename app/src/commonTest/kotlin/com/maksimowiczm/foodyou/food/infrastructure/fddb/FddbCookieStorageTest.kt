package com.maksimowiczm.foodyou.food.infrastructure.fddb

import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.edit
import io.ktor.http.Cookie
import io.ktor.http.CookieEncoding
import io.ktor.http.Url
import io.ktor.util.date.GMTDate
import kotlin.test.*
import kotlinx.coroutines.test.runTest

class FddbCookieStorageTest {
    private val url = Url("https://fddb.info/db/i18n/notepad/")

    @Test
    fun preservesAttributesAndDoesNotExtendMaxAgeAfterRestart() = runTest {
        val data = SessionTestDataStore()
        val crypto = SessionTestCrypto()
        var now = 1_000_000L
        val first = FddbCookieStorage(data, crypto, { now })
        first.addCookie(url, Cookie("session", "private-value", CookieEncoding.RAW, maxAge = 2,
            path = "/db/", secure = true, httpOnly = true, extensions = mapOf("SameSite" to "Lax")))
        assertTrue(first.persistFor("account"))
        now += 1_000L
        val restored = FddbCookieStorage(data, crypto, { now })
        assertEquals(FddbSessionAvailability.Restored, restored.restoreFor("account"))
        val cookie = restored.get(url).single()
        assertEquals("private-value", cookie.value)
        assertEquals("/db/", cookie.path)
        assertEquals("fddb.info", cookie.domain)
        assertTrue(cookie.secure)
        assertTrue(cookie.httpOnly)
        assertEquals(mapOf("SameSite" to "Lax"), cookie.extensions)
        assertEquals(GMTDate(1_002_000L), cookie.expires)
        assertNull(cookie.maxAge)
        assertTrue(restored.get(Url("http://fddb.info/db/i18n/notepad/")).isEmpty())
        assertTrue(restored.get(Url("https://fddb.info/other/")).isEmpty())
        assertTrue(restored.get(Url("https://example.com/db/i18n/notepad/")).isEmpty())
        now += 1_000L
        assertTrue(restored.get(url).isEmpty())
        assertEquals(FddbSessionAvailability.Missing, restored.restoreFor("account"))
    }

    @Test
    fun sessionCookiesWithoutExpirySurviveRestartAndUnchangedSnapshotIsNotWritten() = runTest {
        val data = SessionTestDataStore()
        val crypto = SessionTestCrypto()
        val first = FddbCookieStorage(data, crypto)
        first.addCookie(url, Cookie("session", "value", path = "/"))
        assertEquals(0, data.writes)
        first.persistFor("account")
        assertEquals(1, data.writes)
        first.addCookie(url, Cookie("session", "value", path = "/"))
        first.persistFor("account")
        assertEquals(1, data.writes)
        val restored = FddbCookieStorage(data, crypto)
        assertEquals(FddbSessionAvailability.Restored, restored.restoreFor("account"))
        assertEquals(FddbSessionAvailability.Reused, restored.restoreFor("account"))
        assertNull(restored.get(url).single().expires)
    }

    @Test
    fun repeatingUnchangedCookiesInAnotherOrderDoesNotWriteSnapshot() = runTest {
        val data = SessionTestDataStore()
        val storage = FddbCookieStorage(data, SessionTestCrypto())
        val session = Cookie("session", "value", path = "/")
        val preference = Cookie("language", "de", path = "/")
        storage.addCookie(url, session)
        storage.addCookie(url, preference)
        storage.persistFor("account")
        storage.addCookie(url, session)
        storage.persistFor("account")
        assertEquals(1, data.writes)
    }

    @Test
    fun accountMismatchDeletesCookiesAndExpiredCookiesAreRemoved() = runTest {
        val data = SessionTestDataStore()
        val crypto = SessionTestCrypto()
        val first = FddbCookieStorage(data, crypto, { 1_000L })
        first.addCookie(url, Cookie("session", "value", path = "/"))
        first.addCookie(url, Cookie("expired", "value", expires = GMTDate(999L), path = "/"))
        first.persistFor("account-a")
        assertEquals(listOf("session"), first.get(url).map { it.name })
        val restored = FddbCookieStorage(data, crypto)
        assertEquals(FddbSessionAvailability.Missing, restored.restoreFor("account-b"))
        assertTrue(restored.get(url).isEmpty())
        assertNull(data.state.value[byteArrayPreferencesKey("fddb:sessionV1")])
    }

    @Test
    fun zeroMaxAgeDeletesCorrectCookieAndOtherDomainsAreNotAccepted() = runTest {
        val storage = FddbCookieStorage(SessionTestDataStore(), SessionTestCrypto())
        storage.addCookie(url, Cookie("session", "value", path = "/"))
        storage.addCookie(url, Cookie("session", "value", path = "/db/"))
        storage.addCookie(url, Cookie("session", "", maxAge = 0, path = "/db/"))
        storage.addCookie(url, Cookie("untrusted", "value", domain = "example.com", path = "/"))
        storage.addCookie(Url("https://example.com/"), Cookie("untrusted", "value", path = "/"))
        assertEquals(listOf("/"), storage.get(url).map { it.path })
    }

    @Test
    fun corruptOrIncompatibleEncryptedSnapshotsAreDiscarded() = runTest {
        for (payload in listOf("invalid JSON", """{"version":2,"revision":"account","cookies":[]}""")) {
            val data = SessionTestDataStore()
            val crypto = SessionTestCrypto()
            data.edit { it[byteArrayPreferencesKey("fddb:sessionV1")] = crypto.encrypt(payload.encodeToByteArray()) }
            val storage = FddbCookieStorage(data, crypto)
            assertEquals(FddbSessionAvailability.Missing, storage.restoreFor("account"))
            assertNull(data.state.value[byteArrayPreferencesKey("fddb:sessionV1")])
        }
    }

    @Test
    fun legacyCredentialsRemainReadableAndEveryStoreCreatesNewRevision() = runTest {
        val data = SessionTestDataStore()
        val crypto = SessionTestCrypto()
        data.edit {
            it[byteArrayPreferencesKey("fddb:login")] = crypto.encrypt("user".encodeToByteArray())
            it[byteArrayPreferencesKey("fddb:password")] = crypto.encrypt("password".encodeToByteArray())
        }
        val credentials = FddbCredentialStore(data, crypto)
        assertEquals("legacy", credentials.load()!!.revision)
        assertEquals("user", credentials.load()!!.login)
        val revision = credentials.store("user", "password")
        assertNotEquals("legacy", revision)
        assertNotEquals(revision, credentials.store("user", "password"))
        credentials.clear()
        assertNull(credentials.load())
    }
}

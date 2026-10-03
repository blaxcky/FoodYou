package com.maksimowiczm.foodyou.food.infrastructure.fddb

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.edit
import com.maksimowiczm.foodyou.common.crypto.MasterCrypto
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.cookies.fillDefaults
import io.ktor.client.plugins.cookies.matches
import io.ktor.http.Cookie
import io.ktor.http.CookieEncoding
import io.ktor.http.Url
import io.ktor.util.date.GMTDate
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal enum class FddbSessionAvailability { Missing, Reused, Restored }

/** HTTP callbacks only change memory. The manager flushes once after verifying the response. */
internal class FddbCookieStorage(
    private val dataStore: DataStore<Preferences>,
    private val crypto: MasterCrypto,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : CookiesStorage {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private var loaded = false
    private var revision: String? = null
    private var cookies = emptyList<StoredFddbCookie>()
    private var persisted: FddbCookieSnapshot? = null

    suspend fun restoreFor(accountRevision: String): FddbSessionAvailability = mutex.withLock {
        var restored = false
        if (!loaded) {
            loaded = true
            try {
                dataStore.data.first()[key]?.let {
                    val snapshot = json.decodeFromString<FddbCookieSnapshot>(crypto.decrypt(it).decodeToString())
                    require(snapshot.version == 1)
                    snapshot.cookies.forEach { cookie -> cookie.toCookie() }
                    persisted = snapshot
                    revision = snapshot.revision
                    cookies = snapshot.cookies.filter { it.isFddbCookie() }
                    restored = true
                }
            } catch (cancel: CancellationException) {
                loaded = false
                throw cancel
            } catch (_: Exception) {
                clearMemory()
                removePersisted()
            }
        }
        if (revision != accountRevision) {
            clearMemory()
            removePersisted()
        }
        cookies = cookies.filterNot { it.expired(clock()) }
        if (cookies.none { it.toCookie().matches(DiaryUrl) }) FddbSessionAvailability.Missing
        else if (restored) FddbSessionAvailability.Restored else FddbSessionAvailability.Reused
    }

    override suspend fun get(requestUrl: Url): List<Cookie> = mutex.withLock {
        cookies = cookies.filterNot { it.expired(clock()) }
        cookies.map { it.toCookie() }.filter { it.matches(requestUrl) }
    }

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) {
        if (cookie.name.isBlank() || requestUrl.host != "fddb.info") return
        val normalized = cookie.fillDefaults(requestUrl)
        val expiry = normalized.maxAge?.let { clock() + it.toLong() * 1_000L }
            ?: normalized.expires?.timestamp
        val stored = StoredFddbCookie(
            normalized.name, normalized.value, normalized.encoding.name,
            normalized.domain!!, normalized.path!!, normalized.secure, normalized.httpOnly,
            expiry, normalized.extensions,
        )
        if (!stored.isFddbCookie()) return
        mutex.withLock {
            cookies = cookies.filterNot { it.name == stored.name && it.domain == stored.domain && it.path == stored.path }
            if (!stored.expired(clock())) cookies = cookies + stored
        }
    }

    suspend fun discard() = mutex.withLock {
        loaded = true
        clearMemory()
        removePersisted()
    }

    suspend fun persistFor(accountRevision: String): Boolean = mutex.withLock {
        loaded = true
        revision = accountRevision
        cookies = cookies.filterNot { it.expired(clock()) }
        // Set-Cookie may repeat unchanged cookies in another order; that must not trigger a write.
        val snapshot = FddbCookieSnapshot(
            revision = accountRevision,
            cookies = cookies.sortedWith(compareBy({ it.domain }, { it.path }, { it.name })),
        )
        if (snapshot == persisted) return@withLock true
        try {
            val encrypted = crypto.encrypt(json.encodeToString(snapshot).encodeToByteArray())
            dataStore.edit { it[key] = encrypted }
            persisted = snapshot
            true
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            false // A valid in-memory session still avoids logging in again.
        }
    }

    private fun clearMemory() {
        revision = null
        cookies = emptyList()
        persisted = null
    }

    private suspend fun removePersisted() {
        try { dataStore.edit { it.remove(key) } }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { /* The revision prevents an old snapshot from being reused. */ }
    }

    override fun close() = Unit

    private companion object {
        val key = byteArrayPreferencesKey("fddb:sessionV1")
        val DiaryUrl = Url("https://fddb.info/db/i18n/notepad/")
    }
}

@Serializable
private data class FddbCookieSnapshot(val version: Int = 1, val revision: String, val cookies: List<StoredFddbCookie>)

@Serializable
private data class StoredFddbCookie(
    val name: String,
    val value: String,
    val encoding: String,
    val domain: String,
    val path: String,
    val secure: Boolean,
    val httpOnly: Boolean,
    val expiresAtMillis: Long?,
    val extensions: Map<String, String?>,
) {
    fun expired(now: Long) = expiresAtMillis?.let { it <= now } ?: false
    fun isFddbCookie() = domain.trimStart('.').lowercase() == "fddb.info"
    fun toCookie() = Cookie(
        name = name, value = value, encoding = CookieEncoding.valueOf(encoding),
        expires = expiresAtMillis?.let { GMTDate(it) }, domain = domain, path = path,
        secure = secure, httpOnly = httpOnly, extensions = extensions,
    )
}

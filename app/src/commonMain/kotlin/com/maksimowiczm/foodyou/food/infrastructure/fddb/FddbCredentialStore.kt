package com.maksimowiczm.foodyou.food.infrastructure.fddb

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.maksimowiczm.foodyou.common.crypto.MasterCrypto
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Raw persistence; all mutations are serialized by FddbSessionManager. Legacy keys stay readable. */
@OptIn(ExperimentalUuidApi::class)
internal class FddbCredentialStore(
    private val dataStore: DataStore<Preferences>,
    private val crypto: MasterCrypto,
) {
    val hasCredentials = dataStore.data.map { loginKey in it && passwordKey in it }

    suspend fun load(): FddbStoredCredentials? {
        val preferences = dataStore.data.first()
        val login = preferences[loginKey] ?: return null
        val password = preferences[passwordKey] ?: return null
        return FddbStoredCredentials(
            crypto.decrypt(login).decodeToString(),
            crypto.decrypt(password).decodeToString(),
            preferences[revisionKey] ?: "legacy",
        )
    }

    suspend fun store(login: String, password: String): String {
        val encryptedLogin = crypto.encrypt(login.encodeToByteArray())
        val encryptedPassword = crypto.encrypt(password.encodeToByteArray())
        val saved = dataStore.edit {
            it[loginKey] = encryptedLogin
            it[passwordKey] = encryptedPassword
            it[revisionKey] = Uuid.random().toString()
        }
        return saved[revisionKey]!!
    }

    suspend fun clear() {
        dataStore.edit {
            it.remove(loginKey)
            it.remove(passwordKey)
            it[revisionKey] = Uuid.random().toString()
        }
    }

    private companion object {
        val loginKey = byteArrayPreferencesKey("fddb:login")
        val passwordKey = byteArrayPreferencesKey("fddb:password")
        val revisionKey = stringPreferencesKey("fddb:credentialRevision")
    }
}

// Deliberately not a data class: its generated toString must never expose credentials.
internal class FddbStoredCredentials(val login: String, val password: String, val revision: String)

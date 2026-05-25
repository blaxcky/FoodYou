package com.maksimowiczm.foodyou.food.infrastructure.fddb

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.edit
import com.maksimowiczm.foodyou.common.crypto.MasterCrypto
import com.maksimowiczm.foodyou.food.domain.repository.FddbCredentialsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal class FddbCredentialsRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
    private val masterCrypto: MasterCrypto,
) : FddbCredentialsRepository {
    override suspend fun store(login: String, password: String) {
        dataStore.edit {
            it[loginKey] = masterCrypto.encrypt(login.encodeToByteArray())
            it[passwordKey] = masterCrypto.encrypt(password.encodeToByteArray())
        }
    }

    override suspend fun clear() {
        dataStore.edit {
            it.remove(loginKey)
            it.remove(passwordKey)
        }
    }

    override fun hasCredentials(): Flow<Boolean> =
        dataStore.data.map { loginKey in it && passwordKey in it }

    override suspend fun loadCredentials(): Pair<String, String>? {
        val preferences = dataStore.data.first()
        val encryptedLogin = preferences[loginKey] ?: return null
        val encryptedPassword = preferences[passwordKey] ?: return null

        return masterCrypto.decrypt(encryptedLogin).decodeToString() to
            masterCrypto.decrypt(encryptedPassword).decodeToString()
    }

    private companion object {
        private val loginKey = byteArrayPreferencesKey("fddb:login")
        private val passwordKey = byteArrayPreferencesKey("fddb:password")
    }
}

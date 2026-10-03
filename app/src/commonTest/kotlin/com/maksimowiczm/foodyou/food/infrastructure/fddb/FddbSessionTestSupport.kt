package com.maksimowiczm.foodyou.food.infrastructure.fddb

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.maksimowiczm.foodyou.common.crypto.MasterCrypto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class SessionTestDataStore : DataStore<Preferences> {
    val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state
    var writes = 0
    var failWrites = false
    private val mutex = Mutex()
    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = mutex.withLock {
        if (failWrites) error("Cache unavailable")
        val updated = transform(state.value)
        if (updated != state.value) writes++
        state.value = updated
        updated
    }
}

/** Deterministic test encryption; production exclusively uses the existing Android MasterCrypto. */
internal class SessionTestCrypto : MasterCrypto {
    override val isSupported = flowOf(true)
    override suspend fun encrypt(data: ByteArray): ByteArray =
        byteArrayOf(42) + data.map { (it.toInt() xor 90).toByte() }.toByteArray()
    override suspend fun decrypt(encryptedData: ByteArray): ByteArray {
        require(encryptedData.first() == 42.toByte())
        return encryptedData.drop(1).map { (it.toInt() xor 90).toByte() }.toByteArray()
    }
}

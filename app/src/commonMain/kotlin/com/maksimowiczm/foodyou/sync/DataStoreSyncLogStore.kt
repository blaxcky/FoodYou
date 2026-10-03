package com.maksimowiczm.foodyou.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

internal class DataStoreSyncLogStore(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
) : SyncLogStore {
    private val key = stringPreferencesKey("diagnostics:syncLogV1")
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val state = MutableStateFlow(emptyList<SyncLogRun>())
    private var loaded = false
    private var pendingWrite: Job? = null

    override val runs = flow {
        mutex.withLock { load() }
        emitAll(state)
    }.catch { emit(emptyList()) }

    override suspend fun update(transform: (List<SyncLogRun>) -> List<SyncLogRun>) {
        mutex.withLock {
            load()
            val previous = state.value
            val updated = transform(previous)
            if (updated == previous) return@withLock
            state.value = updated
            // Persist run boundaries immediately; coalesce rapid step updates to avoid measuring
            // a disk write for every detail. Slow, still-active steps are persisted after 200 ms.
            if (previous.map { it.id to it.status } != updated.map { it.id to it.status }) {
                pendingWrite?.cancel()
                pendingWrite = null
                persist(updated)
            } else if (pendingWrite?.isActive != true) {
                pendingWrite = scope.launch {
                    delay(200)
                    try { mutex.withLock { persist(state.value) } }
                    catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { /* The next run boundary retries persistence. */ }
                }
            }
        }
    }

    private suspend fun load() {
        if (!loaded) {
            state.value = decode(dataStore.data.first()[key])
            loaded = true
        }
    }

    private suspend fun persist(runs: List<SyncLogRun>) {
        dataStore.edit { preferences ->
            preferences[key] = json.encodeToString(runs)
        }
    }

    private fun decode(value: String?): List<SyncLogRun> =
        try { value?.let { json.decodeFromString<List<SyncLogRun>>(it) } ?: emptyList() }
        catch (_: Exception) { emptyList() }
}

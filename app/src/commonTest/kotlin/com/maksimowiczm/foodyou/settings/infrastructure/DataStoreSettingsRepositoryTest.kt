package com.maksimowiczm.foodyou.settings.infrastructure

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.maksimowiczm.foodyou.settings.domain.entity.PendingProductPhotoQuality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class DataStoreSettingsRepositoryTest {
    @Test
    fun pendingProductPhotoQualityDefaultsToBalanced() = runTest {
        val repository = DataStoreSettingsRepository(InMemoryPreferencesDataStore())

        val settings = repository.observe().first()

        assertEquals(PendingProductPhotoQuality.Balanced, settings.pendingProductPhotoQuality)
    }

    @Test
    fun pendingProductPhotoQualityRoundTripsThroughDataStore() = runTest {
        val dataStore = InMemoryPreferencesDataStore()
        val repository = DataStoreSettingsRepository(dataStore)

        PendingProductPhotoQuality.entries.forEach { quality ->
            repository.update { copy(pendingProductPhotoQuality = quality) }

            assertEquals(quality, repository.observe().first().pendingProductPhotoQuality)
        }
    }
}

private class InMemoryPreferencesDataStore(
    initialValue: Preferences = emptyPreferences()
) : DataStore<Preferences> {
    private val state = MutableStateFlow(initialValue)

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

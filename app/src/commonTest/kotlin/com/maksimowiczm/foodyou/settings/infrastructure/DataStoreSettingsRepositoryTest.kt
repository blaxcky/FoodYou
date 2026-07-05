package com.maksimowiczm.foodyou.settings.infrastructure

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.maksimowiczm.foodyou.settings.domain.entity.DietEnergyDeficitOverride
import com.maksimowiczm.foodyou.settings.domain.entity.PendingProductPhotoQuality
import com.maksimowiczm.foodyou.settings.domain.entity.effectiveDietEnergyDeficitKcal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

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

    @Test
    fun crosstrainerCalorieDiscountPercentDefaultsToZero() = runTest {
        val repository = DataStoreSettingsRepository(InMemoryPreferencesDataStore())

        val settings = repository.observe().first()

        assertEquals(0.0, settings.crosstrainerCalorieDiscountPercent)
    }

    @Test
    fun crosstrainerCalorieDiscountPercentRoundTripsThroughDataStore() = runTest {
        val repository = DataStoreSettingsRepository(InMemoryPreferencesDataStore())

        repository.update { copy(crosstrainerCalorieDiscountPercent = 12.5) }

        assertEquals(12.5, repository.observe().first().crosstrainerCalorieDiscountPercent)
    }

    @Test
    fun dietEnergyDeficitOverrideDefaultsToNull() = runTest {
        val repository = DataStoreSettingsRepository(InMemoryPreferencesDataStore())

        val settings = repository.observe().first()

        assertNull(settings.dietEnergyDeficitOverride)
    }

    @Test
    fun dietEnergyDeficitOverrideRoundTripsThroughDataStore() = runTest {
        val repository = DataStoreSettingsRepository(InMemoryPreferencesDataStore())
        val override =
            DietEnergyDeficitOverride(
                energyDeficitKcal = 350.0,
                startDate = LocalDate(2026, 7, 6),
                endDate = LocalDate(2026, 7, 12),
            )

        repository.update { copy(dietEnergyDeficitOverride = override) }

        assertEquals(override, repository.observe().first().dietEnergyDeficitOverride)
    }

    @Test
    fun invalidDietEnergyDeficitOverrideIsIgnored() = runTest {
        val repository = DataStoreSettingsRepository(InMemoryPreferencesDataStore())

        repository.update {
            copy(
                dietEnergyDeficitOverride =
                    DietEnergyDeficitOverride(
                        energyDeficitKcal = 350.0,
                        startDate = LocalDate(2026, 7, 12),
                        endDate = LocalDate(2026, 7, 6),
                    )
            )
        }

        assertNull(repository.observe().first().dietEnergyDeficitOverride)
    }

    @Test
    fun partialStoredDietEnergyDeficitOverrideIsIgnored() = runTest {
        val preferences =
            mutablePreferencesOf(
                doublePreferencesKey("settings:dietEnergyDeficitOverrideKcal") to 350.0,
                longPreferencesKey("settings:dietEnergyDeficitOverrideStartEpochDay") to
                    LocalDate(2026, 7, 6).toEpochDays(),
            )
        val repository = DataStoreSettingsRepository(InMemoryPreferencesDataStore(preferences))

        assertNull(repository.observe().first().dietEnergyDeficitOverride)
    }

    @Test
    fun effectiveDietEnergyDeficitUsesOverrideInclusively() = runTest {
        val repository = DataStoreSettingsRepository(InMemoryPreferencesDataStore())
        val settings =
            repository
                .observe()
                .first()
                .copy(
                    dietEnergyDeficitKcal = 500.0,
                    dietEnergyDeficitOverride =
                        DietEnergyDeficitOverride(
                            energyDeficitKcal = 300.0,
                            startDate = LocalDate(2026, 7, 6),
                            endDate = LocalDate(2026, 7, 8),
                        ),
                )

        assertEquals(500.0, settings.effectiveDietEnergyDeficitKcal(LocalDate(2026, 7, 5)))
        assertEquals(300.0, settings.effectiveDietEnergyDeficitKcal(LocalDate(2026, 7, 6)))
        assertEquals(300.0, settings.effectiveDietEnergyDeficitKcal(LocalDate(2026, 7, 7)))
        assertEquals(300.0, settings.effectiveDietEnergyDeficitKcal(LocalDate(2026, 7, 8)))
        assertEquals(500.0, settings.effectiveDietEnergyDeficitKcal(LocalDate(2026, 7, 9)))
    }

    @Test
    fun effectiveDietEnergyDeficitSupportsTemporaryOnlyDiet() = runTest {
        val repository = DataStoreSettingsRepository(InMemoryPreferencesDataStore())
        val settings =
            repository
                .observe()
                .first()
                .copy(
                    dietEnergyDeficitKcal = null,
                    dietEnergyDeficitOverride =
                        DietEnergyDeficitOverride(
                            energyDeficitKcal = 300.0,
                            startDate = LocalDate(2026, 7, 6),
                            endDate = LocalDate(2026, 7, 8),
                        ),
                )

        assertNull(settings.effectiveDietEnergyDeficitKcal(LocalDate(2026, 7, 5)))
        assertEquals(300.0, settings.effectiveDietEnergyDeficitKcal(LocalDate(2026, 7, 6)))
        assertNull(settings.effectiveDietEnergyDeficitKcal(LocalDate(2026, 7, 9)))
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

package com.maksimowiczm.foodyou.settings.infrastructure

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.maksimowiczm.foodyou.settings.domain.entity.DietEnergyDeficitOverride
import com.maksimowiczm.foodyou.settings.domain.entity.LockedDaySurplus
import com.maksimowiczm.foodyou.settings.domain.entity.PendingProductPhotoQuality
import com.maksimowiczm.foodyou.settings.domain.entity.TodayEnergyGoalAdjustment
import com.maksimowiczm.foodyou.settings.domain.entity.effectiveDietEnergyDeficitKcal
import com.maksimowiczm.foodyou.settings.domain.entity.effectiveTodayEnergyGoalAdjustment
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
    fun lockedDaysHaveSafeDefaults() = runTest {
        val settings =
            DataStoreSettingsRepository(InMemoryPreferencesDataStore()).observe().first()

        assertEquals(500.0, settings.defaultLockedDaySurplusKcal)
        assertEquals(emptyList(), settings.lockedDaySurpluses)
    }

    @Test
    fun multipleLockedDaysIncludingZeroRoundTripAndCanBeReplacedOrUnlocked() = runTest {
        val repository = DataStoreSettingsRepository(InMemoryPreferencesDataStore())
        val monday = LocalDate(2026, 7, 27)
        val friday = LocalDate(2026, 7, 31)

        repository.update {
            copy(
                defaultLockedDaySurplusKcal = 650.0,
                lockedDaySurpluses =
                    listOf(LockedDaySurplus(monday, 0.0), LockedDaySurplus(friday, 500.0)),
            )
        }
        assertEquals(650.0, repository.observe().first().defaultLockedDaySurplusKcal)
        assertEquals(
            listOf(LockedDaySurplus(monday, 0.0), LockedDaySurplus(friday, 500.0)),
            repository.observe().first().lockedDaySurpluses,
        )

        repository.update {
            copy(
                lockedDaySurpluses =
                    lockedDaySurpluses
                        .filterNot { it.date == monday }
                        .plus(LockedDaySurplus(friday, 250.0))
            )
        }

        assertEquals(
            listOf(LockedDaySurplus(friday, 250.0)),
            repository.observe().first().lockedDaySurpluses,
        )
    }

    @Test
    fun malformedDuplicateAndInvalidLockedDaysAreSanitized() = runTest {
        val monday = LocalDate(2026, 7, 27)
        val tuesday = LocalDate(2026, 7, 28)
        val preferences =
            mutablePreferencesOf(
                stringPreferencesKey("settings:lockedDaySurpluses") to
                    "broken,${monday.toEpochDays()}:100,${monday.toEpochDays()}:250," +
                        "${tuesday.toEpochDays()}:-1,${tuesday.toEpochDays()}:NaN"
            )
        val settings =
            DataStoreSettingsRepository(InMemoryPreferencesDataStore(preferences)).observe().first()

        assertEquals(listOf(LockedDaySurplus(monday, 250.0)), settings.lockedDaySurpluses)
    }

    @Test
    fun invalidLockedDayValuesAreNotPersisted() = runTest {
        val repository = DataStoreSettingsRepository(InMemoryPreferencesDataStore())

        repository.update {
            copy(
                defaultLockedDaySurplusKcal = Double.NaN,
                lockedDaySurpluses =
                    listOf(
                        LockedDaySurplus(LocalDate(2026, 7, 27), -1.0),
                        LockedDaySurplus(LocalDate(2026, 7, 28), Double.POSITIVE_INFINITY),
                    ),
            )
        }

        val settings = repository.observe().first()
        assertEquals(500.0, settings.defaultLockedDaySurplusKcal)
        assertEquals(emptyList(), settings.lockedDaySurpluses)
    }

    @Test
    fun todayEnergyGoalAdjustmentRoundTripsThroughDataStore() = runTest {
        val repository = DataStoreSettingsRepository(InMemoryPreferencesDataStore())
        val adjustment =
            TodayEnergyGoalAdjustment(LocalDate(2026, 7, 17), reductionKcal = 500.0)

        repository.update { copy(todayEnergyGoalAdjustment = adjustment) }

        assertEquals(adjustment, repository.observe().first().todayEnergyGoalAdjustment)
    }

    @Test
    fun invalidOrPartialTodayEnergyGoalAdjustmentIsIgnored() = runTest {
        val invalidRepository = DataStoreSettingsRepository(InMemoryPreferencesDataStore())
        invalidRepository.update {
            copy(
                todayEnergyGoalAdjustment =
                    TodayEnergyGoalAdjustment(LocalDate(2026, 7, 17), reductionKcal = 0.0)
            )
        }
        assertNull(invalidRepository.observe().first().todayEnergyGoalAdjustment)

        val partialPreferences =
            mutablePreferencesOf(
                longPreferencesKey("settings:todayEnergyGoalAdjustmentEpochDay") to
                    LocalDate(2026, 7, 17).toEpochDays()
            )
        val partialRepository =
            DataStoreSettingsRepository(InMemoryPreferencesDataStore(partialPreferences))
        assertNull(partialRepository.observe().first().todayEnergyGoalAdjustment)
    }

    @Test
    fun todayEnergyGoalAdjustmentOnlyAppliesToActualToday() = runTest {
        val repository = DataStoreSettingsRepository(InMemoryPreferencesDataStore())
        val today = LocalDate(2026, 7, 17)
        val settings =
            repository.observe().first().copy(
                todayEnergyGoalAdjustment = TodayEnergyGoalAdjustment(today, 500.0)
            )

        assertEquals(
            500.0,
            settings.effectiveTodayEnergyGoalAdjustment(today, today)?.reductionKcal,
        )
        assertNull(
            settings.effectiveTodayEnergyGoalAdjustment(LocalDate(2026, 7, 16), today)
        )
        assertNull(
            settings.effectiveTodayEnergyGoalAdjustment(
                LocalDate(2026, 7, 18),
                LocalDate(2026, 7, 18),
            )
        )
    }

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

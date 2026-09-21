package com.maksimowiczm.foodyou.weight.infrastructure.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.maksimowiczm.foodyou.goals.domain.entity.BasalMetabolicRateProfile
import com.maksimowiczm.foodyou.goals.domain.repository.BasalMetabolicRateProfileRepository
import com.maksimowiczm.foodyou.weight.domain.entity.DailyWeightEntry
import com.maksimowiczm.foodyou.weight.infrastructure.room.DailyWeightEntryDao
import com.maksimowiczm.foodyou.weight.infrastructure.room.DailyWeightEntryEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.LocalDate

class RoomWeightRepositoryTest {
    @Test
    fun todayWeightIsUpsertedByDateAndUpdatesBmrWeight() = runTest {
        val dao = InMemoryDailyWeightEntryDao()
        val bmrRepository = FakeBmrRepository()
        val repository = RoomWeightRepository(dao, InMemoryPreferencesDataStore(), bmrRepository)

        repository.upsertToday(80.0)
        repository.upsertToday(79.8)

        assertEquals(1, dao.entries.value.size)
        assertEquals(79.8, dao.entries.value.values.single().weightKg)
        assertEquals(79.8, bmrRepository.profile.value.weightKg)
    }

    @Test
    fun foreignHealthConnectRecordIdIsNotPreservedWhenWritingToday() = runTest {
        val dao = InMemoryDailyWeightEntryDao()
        val repository =
            RoomWeightRepository(dao, InMemoryPreferencesDataStore(), FakeBmrRepository())
        dao.upsert(
            DailyWeightEntryEntity(
                id = "hc:foreign",
                dateEpochDay = todayEpochDay(),
                measuredEpochSeconds = 1,
                weightKg = 80.0,
                healthConnectRecordId = "foreign",
                isFoodYouRecord = false,
                sourcePackageName = "com.fitbit.FitbitMobile",
                sourceDeviceType = 3,
                isHidden = false,
            )
        )

        repository.upsertToday(79.8)

        val saved = dao.entries.value.getValue("local:${todayEpochDay()}")
        assertNull(saved.healthConnectRecordId)
        assertTrue(saved.isFoodYouRecord)
    }

    @Test
    fun hidingNewestMeasurementFallsBackAndSurvivesReimport() = runTest {
        val dao = InMemoryDailyWeightEntryDao()
        val bmrRepository = FakeBmrRepository()
        val repository = RoomWeightRepository(dao, InMemoryPreferencesDataStore(), bmrRepository)
        val date = LocalDate(2026, 6, 7)
        val earlier = measurement(date, "first", 80.0, "2026-06-07T06:00:00Z")
        val later = measurement(date, "second", 79.5, "2026-06-07T08:00:00Z")

        repository.upsertAll(listOf(earlier, later))
        assertEquals(79.5, repository.observeEntries().first().single().weightKg)
        assertEquals(79.5, bmrRepository.profile.value.weightKg)

        repository.setHidden(later.id, true)
        repository.upsertAll(listOf(later.copy(weightKg = 79.4)))
        assertEquals(80.0, repository.observeEntries().first().single().weightKg)
        assertEquals(80.0, bmrRepository.profile.value.weightKg)
        assertTrue(repository.observeMeasurements().first().first { it.id == later.id }.isHidden)

        repository.setHidden(later.id, false)
        assertEquals(79.4, repository.observeEntries().first().single().weightKg)
    }

    @Test
    fun manualProfileChangeIsNotOverwrittenByImport() = runTest {
        val dao = InMemoryDailyWeightEntryDao()
        val bmrRepository = FakeBmrRepository()
        val repository = RoomWeightRepository(dao, InMemoryPreferencesDataStore(), bmrRepository)
        val date = LocalDate(2026, 6, 7)
        repository.upsert(measurement(date, "first", 80.0, "2026-06-07T06:00:00Z"))
        bmrRepository.updateProfile(bmrRepository.profile.value.copy(weightKg = 88.0))

        repository.upsert(measurement(date, "second", 79.0, "2026-06-07T08:00:00Z"))

        assertEquals(88.0, bmrRepository.profile.value.weightKg)
    }

    @Test
    fun hidingOnlyMeasurementClearsAutoManagedProfileWeight() = runTest {
        val bmrRepository = FakeBmrRepository()
        val repository = RoomWeightRepository(
            InMemoryDailyWeightEntryDao(), InMemoryPreferencesDataStore(), bmrRepository
        )
        val entry = measurement(LocalDate(2026, 6, 7), "first", 80.0, "2026-06-07T06:00:00Z")
        repository.upsert(entry)

        repository.setHidden(entry.id, true)

        assertNull(bmrRepository.profile.value.weightKg)
        assertTrue(repository.observeEntries().first().isEmpty())
    }
}

private fun measurement(date: LocalDate, id: String, kg: Double, time: String) =
    DailyWeightEntry(
        date = date,
        weightKg = kg,
        measuredAt = Instant.parse(time),
        healthConnectRecordId = id,
        isFoodYouRecord = false,
        id = "hc:$id",
        sourcePackageName = "com.fitbit.FitbitMobile",
        sourceDeviceType = 3,
    )

private class InMemoryDailyWeightEntryDao : DailyWeightEntryDao {
    val entries = MutableStateFlow<Map<String, DailyWeightEntryEntity>>(emptyMap())

    override fun observeAll(): Flow<List<DailyWeightEntryEntity>> =
        entries.map { it.values.sortedByDescending(DailyWeightEntryEntity::dateEpochDay) }

    override suspend fun find(id: String): DailyWeightEntryEntity? = entries.value[id]

    override suspend fun setHidden(id: String, hidden: Boolean) {
        entries.value = entries.value + (id to entries.value.getValue(id).copy(isHidden = hidden))
    }

    override suspend fun upsert(entry: DailyWeightEntryEntity) {
        entries.value = entries.value + (entry.id to entry)
    }

    override suspend fun upsertAll(entries: List<DailyWeightEntryEntity>) {
        this.entries.value = this.entries.value + entries.associateBy { it.id }
    }
}

private class FakeBmrRepository : BasalMetabolicRateProfileRepository {
    val profile = MutableStateFlow(BasalMetabolicRateProfile.Empty)

    override suspend fun updateProfile(profile: BasalMetabolicRateProfile) {
        this.profile.value = profile
    }

    override fun observeProfile(): Flow<BasalMetabolicRateProfile> = profile
}

private class InMemoryPreferencesDataStore(
    initialPreferences: Preferences = emptyPreferences()
) : DataStore<Preferences> {
    private val flow = MutableStateFlow(initialPreferences)

    override val data: Flow<Preferences> = flow

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val updated = transform(flow.value)
        flow.value = updated
        return updated
    }
}

private fun todayEpochDay(): Long {
    val now = kotlin.time.Clock.System.now()
    return now.toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date.toEpochDays()
}

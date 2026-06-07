package com.maksimowiczm.foodyou.weight.infrastructure.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.maksimowiczm.foodyou.goals.domain.entity.BasalMetabolicRateProfile
import com.maksimowiczm.foodyou.goals.domain.repository.BasalMetabolicRateProfileRepository
import com.maksimowiczm.foodyou.weight.infrastructure.room.DailyWeightEntryDao
import com.maksimowiczm.foodyou.weight.infrastructure.room.DailyWeightEntryEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.toLocalDateTime

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
                dateEpochDay = todayEpochDay(),
                measuredEpochSeconds = 1,
                weightKg = 80.0,
                healthConnectRecordId = "foreign",
                isFoodYouRecord = false,
            )
        )

        repository.upsertToday(79.8)

        val saved = dao.entries.value.values.single()
        assertNull(saved.healthConnectRecordId)
        assertTrue(saved.isFoodYouRecord)
    }
}

private class InMemoryDailyWeightEntryDao : DailyWeightEntryDao {
    val entries = MutableStateFlow<Map<Long, DailyWeightEntryEntity>>(emptyMap())

    override fun observeAll(): Flow<List<DailyWeightEntryEntity>> =
        entries.map { it.values.sortedByDescending(DailyWeightEntryEntity::dateEpochDay) }

    override fun observe(dateEpochDay: Long): Flow<DailyWeightEntryEntity?> =
        entries.map { it[dateEpochDay] }

    override suspend fun find(dateEpochDay: Long): DailyWeightEntryEntity? =
        entries.value[dateEpochDay]

    override suspend fun upsert(entry: DailyWeightEntryEntity) {
        entries.value = entries.value + (entry.dateEpochDay to entry)
    }

    override suspend fun upsertAll(entries: List<DailyWeightEntryEntity>) {
        this.entries.value = this.entries.value + entries.associateBy { it.dateEpochDay }
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

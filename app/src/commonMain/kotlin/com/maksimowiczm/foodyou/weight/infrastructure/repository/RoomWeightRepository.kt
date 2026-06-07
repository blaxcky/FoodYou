package com.maksimowiczm.foodyou.weight.infrastructure.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import com.maksimowiczm.foodyou.goals.domain.repository.BasalMetabolicRateProfileRepository
import com.maksimowiczm.foodyou.weight.domain.entity.DailyWeightEntry
import com.maksimowiczm.foodyou.weight.domain.entity.WeightGoal
import com.maksimowiczm.foodyou.weight.domain.repository.WeightRepository
import com.maksimowiczm.foodyou.weight.infrastructure.room.DailyWeightEntryDao
import com.maksimowiczm.foodyou.weight.infrastructure.room.DailyWeightEntryEntity
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

internal class RoomWeightRepository(
    private val dao: DailyWeightEntryDao,
    private val dataStore: DataStore<Preferences>,
    private val basalMetabolicRateProfileRepository: BasalMetabolicRateProfileRepository,
) : WeightRepository {
    override fun observeEntries(): Flow<List<DailyWeightEntry>> =
        dao.observeAll().map { entries -> entries.map { it.toModel() } }

    override fun observeToday(): Flow<DailyWeightEntry?> =
        dao.observe(today().toEpochDays()).map { it?.toModel() }

    override fun observeGoal(): Flow<WeightGoal> =
        dataStore.data.map { preferences ->
            WeightGoal(targetWeightKg = preferences[WeightPreferencesKeys.targetWeightKg])
        }

    override suspend fun upsertToday(weightKg: Double) {
        val now = Clock.System.now()
        val entry =
            DailyWeightEntry(
                date = today(),
                weightKg = weightKg,
                measuredAt = now,
                healthConnectRecordId =
                    entry(today())?.takeIf { it.isFoodYouRecord }?.healthConnectRecordId,
                isFoodYouRecord = true,
            )
        upsert(entry)
        val profile = basalMetabolicRateProfileRepository.observeProfile().first()
        basalMetabolicRateProfileRepository.updateProfile(profile.copy(weightKg = weightKg))
    }

    override suspend fun upsert(entry: DailyWeightEntry) {
        dao.upsert(entry.toEntity())
    }

    override suspend fun upsertAll(entries: List<DailyWeightEntry>) {
        dao.upsertAll(entries.map { it.toEntity() })
    }

    override suspend fun entry(date: LocalDate): DailyWeightEntry? =
        dao.find(date.toEpochDays())?.toModel()

    override suspend fun updateGoal(goal: WeightGoal) {
        dataStore.updateData {
            it.toMutablePreferences().apply {
                val value = goal.targetWeightKg
                if (value != null && value > 0.0) {
                    this[WeightPreferencesKeys.targetWeightKg] = value
                } else {
                    remove(WeightPreferencesKeys.targetWeightKg)
                }
            }
        }
    }

    private fun today(): LocalDate =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
}

private object WeightPreferencesKeys {
    val targetWeightKg = doublePreferencesKey("weight:targetWeightKg")
}

private fun DailyWeightEntryEntity.toModel(): DailyWeightEntry =
    DailyWeightEntry(
        date = LocalDate.fromEpochDays(dateEpochDay),
        weightKg = weightKg,
        measuredAt = Instant.fromEpochSeconds(measuredEpochSeconds),
        healthConnectRecordId = healthConnectRecordId,
        isFoodYouRecord = isFoodYouRecord,
    )

private fun DailyWeightEntry.toEntity(): DailyWeightEntryEntity =
    DailyWeightEntryEntity(
        dateEpochDay = date.toEpochDays(),
        measuredEpochSeconds = measuredAt.epochSeconds,
        weightKg = weightKg,
        healthConnectRecordId = healthConnectRecordId,
        isFoodYouRecord = isFoodYouRecord,
    )

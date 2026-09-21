package com.maksimowiczm.foodyou.weight.infrastructure.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import com.maksimowiczm.foodyou.goals.domain.repository.BasalMetabolicRateProfileRepository
import com.maksimowiczm.foodyou.weight.domain.entity.DailyWeightEntry
import com.maksimowiczm.foodyou.weight.domain.entity.WeightGoal
import com.maksimowiczm.foodyou.weight.domain.repository.WeightRepository
import com.maksimowiczm.foodyou.weight.domain.usecase.latestWeightEntryPerDay
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
        observeMeasurements().map { measurements ->
            latestWeightEntryPerDay(measurements).sortedByDescending { it.date }
        }

    override fun observeMeasurements(): Flow<List<DailyWeightEntry>> =
        dao.observeAll().map { entries -> entries.map { it.toModel() } }

    override fun observeToday(): Flow<DailyWeightEntry?> =
        observeEntries().map { entries -> entries.firstOrNull { it.date == today() } }

    override fun observeGoal(): Flow<WeightGoal> =
        dataStore.data.map { preferences ->
            WeightGoal(targetWeightKg = preferences[WeightPreferencesKeys.targetWeightKg])
        }

    override suspend fun upsertToday(weightKg: Double) {
        val now = Clock.System.now()
        val date = today()
        val entry =
            DailyWeightEntry(
                date = date,
                weightKg = weightKg,
                measuredAt = now,
                healthConnectRecordId = entry(date)?.healthConnectRecordId,
                isFoodYouRecord = true,
            )
        val previous = latestVisibleWeight()
        dao.upsert(entry.toEntity())
        updateProfileWeight(previous, force = true)
    }

    override suspend fun upsert(entry: DailyWeightEntry) {
        upsertAll(listOf(entry))
    }

    override suspend fun upsertAll(entries: List<DailyWeightEntry>) {
        if (entries.isEmpty()) return
        val previous = latestVisibleWeight()
        dao.upsertImportedPreservingHidden(entries.map { it.toEntity() })
        updateProfileWeight(previous)
    }

    override suspend fun entry(date: LocalDate): DailyWeightEntry? =
        dao.find("local:${date.toEpochDays()}")?.toModel()

    override suspend fun setHidden(id: String, hidden: Boolean) {
        val previous = latestVisibleWeight()
        dao.setHidden(id, hidden)
        updateProfileWeight(previous)
    }

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

    private suspend fun latestVisibleWeight(): Double? =
        latestWeightEntryPerDay(dao.observeAll().first().map { it.toModel() })
            .maxByOrNull { it.measuredAt }
            ?.weightKg

    private suspend fun updateProfileWeight(previous: Double?, force: Boolean = false) {
        val next = latestVisibleWeight()
        val profile = basalMetabolicRateProfileRepository.observeProfile().first()
        val lastAutoApplied = dataStore.data.first()[WeightPreferencesKeys.autoAppliedWeightKg]
        val owned =
            force ||
                (lastAutoApplied != null && profile.weightKg == lastAutoApplied) ||
                (lastAutoApplied == null && previous != null && profile.weightKg == previous) ||
                (lastAutoApplied == null && profile.weightKg == null)
        if (!owned || next == profile.weightKg) return
        basalMetabolicRateProfileRepository.updateProfile(profile.copy(weightKg = next))
        dataStore.updateData { preferences ->
            preferences.toMutablePreferences().apply {
                if (next == null) remove(WeightPreferencesKeys.autoAppliedWeightKg)
                else this[WeightPreferencesKeys.autoAppliedWeightKg] = next
            }
        }
    }
}

private object WeightPreferencesKeys {
    val targetWeightKg = doublePreferencesKey("weight:targetWeightKg")
    val autoAppliedWeightKg = doublePreferencesKey("weight:autoAppliedWeightKg")
}

private fun DailyWeightEntryEntity.toModel(): DailyWeightEntry =
    DailyWeightEntry(
        id = id,
        date = LocalDate.fromEpochDays(dateEpochDay),
        weightKg = weightKg,
        measuredAt = Instant.fromEpochSeconds(measuredEpochSeconds),
        healthConnectRecordId = healthConnectRecordId,
        isFoodYouRecord = isFoodYouRecord,
        sourcePackageName = sourcePackageName,
        sourceDeviceType = sourceDeviceType,
        isHidden = isHidden,
    )

private fun DailyWeightEntry.toEntity(): DailyWeightEntryEntity =
    DailyWeightEntryEntity(
        id = id,
        dateEpochDay = date.toEpochDays(),
        measuredEpochSeconds = measuredAt.epochSeconds,
        weightKg = weightKg,
        healthConnectRecordId = healthConnectRecordId,
        isFoodYouRecord = isFoodYouRecord,
        sourcePackageName = sourcePackageName,
        sourceDeviceType = sourceDeviceType,
        isHidden = isHidden,
    )

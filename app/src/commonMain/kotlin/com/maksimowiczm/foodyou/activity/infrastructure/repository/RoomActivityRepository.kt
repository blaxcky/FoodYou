package com.maksimowiczm.foodyou.activity.infrastructure.repository

import com.maksimowiczm.foodyou.activity.domain.entity.DailyActivitySummary
import com.maksimowiczm.foodyou.activity.domain.entity.DailyStepSummary
import com.maksimowiczm.foodyou.activity.domain.entity.ManualActivityEntry
import com.maksimowiczm.foodyou.activity.domain.entity.ManualActivityEntryId
import com.maksimowiczm.foodyou.activity.domain.entity.StepExclusionPeriod
import com.maksimowiczm.foodyou.activity.domain.repository.ActivityRepository
import com.maksimowiczm.foodyou.activity.domain.usecase.calculateStepEnergyKcal
import com.maksimowiczm.foodyou.activity.infrastructure.room.DailyStepSummaryDao
import com.maksimowiczm.foodyou.activity.infrastructure.room.DailyStepSummaryEntity
import com.maksimowiczm.foodyou.activity.infrastructure.room.ManualActivityEntryDao
import com.maksimowiczm.foodyou.activity.infrastructure.room.ManualActivityEntryEntity
import com.maksimowiczm.foodyou.activity.infrastructure.room.StepExclusionPeriodDao
import com.maksimowiczm.foodyou.activity.infrastructure.room.StepExclusionPeriodEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

internal class RoomActivityRepository(
    private val manualDao: ManualActivityEntryDao,
    private val stepDao: DailyStepSummaryDao,
    private val exclusionDao: StepExclusionPeriodDao,
) : ActivityRepository {
    override fun observeManualEntry(id: ManualActivityEntryId): Flow<ManualActivityEntry?> =
        manualDao.observe(id.value).map { it?.toModel() }

    override fun observeManualEntries(date: LocalDate): Flow<List<ManualActivityEntry>> =
        manualDao.observeAll(date.toEpochDays()).map { entries -> entries.map { it.toModel() } }

    override fun observeDailySummary(
        date: LocalDate,
        kcalPerStep: Double?,
    ): Flow<DailyActivitySummary> =
        combine(
            stepDao.observe(date.toEpochDays()),
            manualDao.observeEnergySum(date.toEpochDays()),
        ) { steps, manualEnergy ->
            val rawSteps = steps?.rawSteps ?: 0
            val excludedSteps = steps?.excludedSteps ?: 0
            val countedSteps = (rawSteps - excludedSteps).coerceAtLeast(0)
            val stepEnergy = calculateStepEnergyKcal(countedSteps, kcalPerStep)
            DailyActivitySummary(
                rawSteps = rawSteps,
                excludedSteps = excludedSteps,
                countedSteps = countedSteps,
                stepEnergyKcal = stepEnergy,
                manualEnergyKcal = manualEnergy,
                totalEnergyKcal = stepEnergy + manualEnergy,
            )
        }

    override fun observeStepExclusionPeriods(date: LocalDate): Flow<List<StepExclusionPeriod>> =
        exclusionDao.observeAll(date.toEpochDays()).map { periods -> periods.map { it.toModel() } }

    override suspend fun createManualEntry(entry: ManualActivityEntry): ManualActivityEntryId =
        ManualActivityEntryId(manualDao.insert(entry.toEntity()))

    override suspend fun updateManualEntry(entry: ManualActivityEntry) {
        manualDao.update(entry.toEntity())
    }

    override suspend fun deleteManualEntry(id: ManualActivityEntryId) {
        manualDao.delete(id.value)
    }

    override suspend fun upsertStepSummary(summary: DailyStepSummary) {
        stepDao.upsert(summary.toEntity())
    }

    override suspend fun replaceStepExclusionPeriods(
        date: LocalDate,
        periods: List<StepExclusionPeriod>,
    ) {
        require(periods.all { it.date == date }) { "Step exclusion periods must share the replaced date" }
        exclusionDao.replaceAll(date.toEpochDays(), periods.map { it.toEntity() })
    }
}

private fun ManualActivityEntryEntity.toModel(): ManualActivityEntry {
    val timeZone = TimeZone.currentSystemDefault()
    return ManualActivityEntry(
        id = ManualActivityEntryId(id),
        date = LocalDate.fromEpochDays(dateEpochDay),
        name = name,
        energyKcal = energyKcal,
        createdAt = Instant.fromEpochSeconds(createdEpochSeconds).toLocalDateTime(timeZone),
        updatedAt = Instant.fromEpochSeconds(updatedEpochSeconds).toLocalDateTime(timeZone),
    )
}

private fun ManualActivityEntry.toEntity(): ManualActivityEntryEntity {
    val timeZone = TimeZone.currentSystemDefault()
    return ManualActivityEntryEntity(
        id = id.value,
        dateEpochDay = date.toEpochDays(),
        name = name,
        energyKcal = energyKcal,
        createdEpochSeconds = createdAt.toInstant(timeZone).epochSeconds,
        updatedEpochSeconds = updatedAt.toInstant(timeZone).epochSeconds,
    )
}

private fun DailyStepSummary.toEntity(): DailyStepSummaryEntity =
    DailyStepSummaryEntity(
        dateEpochDay = date.toEpochDays(),
        rawSteps = rawSteps,
        excludedSteps = excludedSteps,
        syncedEpochSeconds = syncedAt.epochSeconds,
    )

private fun StepExclusionPeriodEntity.toModel(): StepExclusionPeriod =
    StepExclusionPeriod(
        date = LocalDate.fromEpochDays(dateEpochDay),
        startMinute = startMinute,
        endMinute = endMinute,
    )

private fun StepExclusionPeriod.toEntity(): StepExclusionPeriodEntity =
    StepExclusionPeriodEntity(
        dateEpochDay = date.toEpochDays(),
        startMinute = startMinute,
        endMinute = endMinute,
    )

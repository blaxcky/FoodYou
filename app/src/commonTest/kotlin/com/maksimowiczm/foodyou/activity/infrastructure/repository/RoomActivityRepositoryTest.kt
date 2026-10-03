package com.maksimowiczm.foodyou.activity.infrastructure.repository

import com.maksimowiczm.foodyou.activity.domain.entity.DailyStepSummary
import com.maksimowiczm.foodyou.activity.domain.entity.StepExclusionPeriod
import com.maksimowiczm.foodyou.activity.infrastructure.room.DailyStepSummaryDao
import com.maksimowiczm.foodyou.activity.infrastructure.room.DailyStepSummaryEntity
import com.maksimowiczm.foodyou.activity.infrastructure.room.ManualActivityEntryDao
import com.maksimowiczm.foodyou.activity.infrastructure.room.ManualActivityEntryEntity
import com.maksimowiczm.foodyou.activity.infrastructure.room.StepExclusionPeriodDao
import com.maksimowiczm.foodyou.activity.infrastructure.room.StepExclusionPeriodEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

class RoomActivityRepositoryTest {
    @Test
    fun bulkOperationsUseOneDaoCallAndKeepDayValuesAndExclusionsSeparate() = runTest {
        val stepDao = InMemoryStepSummaryDao()
        val exclusionDao = InMemoryExclusionDao()
        val repository = RoomActivityRepository(InMemoryManualDao(), stepDao, exclusionDao)
        val yesterday = LocalDate(2026, 8, 27)
        val periods = listOf(StepExclusionPeriod(yesterday, 480, 540), StepExclusionPeriod(date, 600, 660))
        repository.replaceStepExclusionPeriods(yesterday, periods.filter { it.date == yesterday })
        repository.replaceStepExclusionPeriods(date, periods.filter { it.date == date })
        repository.replaceStepExclusionPeriods(LocalDate(2026, 1, 1), listOf(StepExclusionPeriod(LocalDate(2026, 1, 1), 60, 120)))
        assertEquals(periods, repository.readStepExclusionPeriods(listOf(yesterday, date, date)))
        assertEquals(1, exclusionDao.bulkReads)
        assertEquals(listOf(yesterday.toEpochDays(), date.toEpochDays()), exclusionDao.lastRequestedDates)
        repository.upsertStepSummaries(listOf(
            DailyStepSummary(yesterday, 1_000, 250, Instant.DISTANT_PAST),
            DailyStepSummary(date, 2_000, 0, Instant.DISTANT_PAST),
        ))
        assertEquals(1, stepDao.bulkWrites)
        assertEquals(0, stepDao.singleWrites)
        assertEquals(7.5, repository.observeDailySummary(yesterday, 0.01).first().stepEnergyKcal)
        assertEquals(20.0, repository.observeDailySummary(date, 0.01).first().stepEnergyKcal)
    }

    private val date = LocalDate(2026, 8, 28)

    @Test
    fun countedStepsDriveStepCaloriesAndReactToSummaryUpdates() = runTest {
        val stepDao = InMemoryStepSummaryDao()
        val repository =
            RoomActivityRepository(InMemoryManualDao(), stepDao, InMemoryExclusionDao())
        val summaries = repository.observeDailySummary(date, kcalPerStep = 0.01)

        repository.upsertStepSummary(
            DailyStepSummary(date, rawSteps = 10_000, excludedSteps = 2_500, Instant.DISTANT_PAST)
        )

        val summary = summaries.first { it.rawSteps == 10_000L }
        assertEquals(10_000, summary.rawSteps)
        assertEquals(2_500, summary.excludedSteps)
        assertEquals(7_500, summary.countedSteps)
        assertEquals(75.0, summary.stepEnergyKcal)
        assertEquals(75.0, summary.totalEnergyKcal)
    }

    @Test
    fun replacesSeveralPeriodsAndCanDeleteAllReactively() = runTest {
        val repository =
            RoomActivityRepository(
                InMemoryManualDao(),
                InMemoryStepSummaryDao(),
                InMemoryExclusionDao(),
            )
        val observed = repository.observeStepExclusionPeriods(date)
        val periods =
            listOf(
                StepExclusionPeriod(date, 480, 540),
                StepExclusionPeriod(date, 600, 660),
            )

        repository.replaceStepExclusionPeriods(date, periods)
        assertEquals(periods, observed.first { it.size == 2 })

        repository.replaceStepExclusionPeriods(date, emptyList())
        assertEquals(emptyList(), observed.first { it.isEmpty() })
    }
}

private class InMemoryStepSummaryDao : DailyStepSummaryDao {
    var bulkWrites = 0
    var singleWrites = 0
    private val entries = MutableStateFlow<Map<Long, DailyStepSummaryEntity>>(emptyMap())

    override fun observe(dateEpochDay: Long): Flow<DailyStepSummaryEntity?> =
        entries.map { it[dateEpochDay] }

    override suspend fun upsertAll(summaries: List<DailyStepSummaryEntity>) {
        bulkWrites++
        entries.value = entries.value + summaries.associateBy { it.dateEpochDay }
    }

    override suspend fun upsert(summary: DailyStepSummaryEntity) {
        singleWrites++
        entries.value = entries.value + (summary.dateEpochDay to summary)
    }
}

private class InMemoryExclusionDao : StepExclusionPeriodDao {
    var bulkReads = 0
    var lastRequestedDates = emptyList<Long>()
    private val entries = MutableStateFlow<List<StepExclusionPeriodEntity>>(emptyList())

    override fun observeAll(dateEpochDay: Long): Flow<List<StepExclusionPeriodEntity>> =
        entries.map { list -> list.filter { it.dateEpochDay == dateEpochDay } }

    override suspend fun readAll(dateEpochDays: List<Long>): List<StepExclusionPeriodEntity> {
        bulkReads++
        lastRequestedDates = dateEpochDays
        return entries.value.filter { it.dateEpochDay in dateEpochDays }
    }

    override suspend fun deleteAll(dateEpochDay: Long) {
        entries.value = entries.value.filterNot { it.dateEpochDay == dateEpochDay }
    }

    override suspend fun insertAll(periods: List<StepExclusionPeriodEntity>) {
        entries.value = entries.value + periods
    }
}

private class InMemoryManualDao : ManualActivityEntryDao {
    override fun observe(id: Long): Flow<ManualActivityEntryEntity?> =
        MutableStateFlow(null)

    override fun observeAll(dateEpochDay: Long): Flow<List<ManualActivityEntryEntity>> =
        MutableStateFlow(emptyList())

    override fun observeEnergySum(dateEpochDay: Long): Flow<Double> = MutableStateFlow(0.0)

    override suspend fun insert(entry: ManualActivityEntryEntity): Long = error("Not used")
    override suspend fun update(entry: ManualActivityEntryEntity) = error("Not used")
    override suspend fun delete(id: Long) = error("Not used")
}

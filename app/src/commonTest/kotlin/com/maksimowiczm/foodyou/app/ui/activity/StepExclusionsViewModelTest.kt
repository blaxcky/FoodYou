package com.maksimowiczm.foodyou.app.ui.activity

import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.activity.HealthConnectActivitySync
import com.maksimowiczm.foodyou.activity.HealthConnectAvailability
import com.maksimowiczm.foodyou.activity.HealthConnectSyncResult
import com.maksimowiczm.foodyou.activity.domain.entity.DailyActivitySummary
import com.maksimowiczm.foodyou.activity.domain.entity.DailyStepSummary
import com.maksimowiczm.foodyou.activity.domain.entity.ManualActivityEntry
import com.maksimowiczm.foodyou.activity.domain.entity.ManualActivityEntryId
import com.maksimowiczm.foodyou.activity.domain.entity.StepExclusionPeriod
import com.maksimowiczm.foodyou.activity.domain.repository.ActivityRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate

class StepExclusionsViewModelTest {
    private val date = LocalDate(2026, 8, 28)
    private val viewModels = mutableListOf<StepExclusionsViewModel>()

    @Test
    fun loadsSummaryAndSavedPeriods() = runViewModelTest {
        val saved = listOf(StepExclusionPeriod(date, 480, 540))
        val viewModel = createViewModel(FakeRepository(saved), FakeSync())

        viewModel.load(date)
        advanceUntilIdle()

        assertEquals(10_000, viewModel.state.value.rawSteps)
        assertEquals(1_500, viewModel.state.value.excludedSteps)
        assertEquals(8_500, viewModel.state.value.countedSteps)
        assertEquals(saved, viewModel.state.value.periods)
        assertFalse(viewModel.state.value.hasUnsavedChanges)
    }

    @Test
    fun savesPeriodsSyncsAndCompletes() = runViewModelTest {
        val repository = FakeRepository()
        val sync = FakeSync(HealthConnectSyncResult.Synced)
        val viewModel = createViewModel(repository, sync)
        var completed = false

        viewModel.load(date)
        viewModel.addPeriod()
        viewModel.save { completed = true }
        advanceUntilIdle()

        assertEquals(listOf(StepExclusionPeriod(date, 540, 600)), repository.periods.value)
        assertEquals(listOf(date), sync.syncedDates.single())
        assertTrue(completed)
        assertFalse(viewModel.state.value.hasUnsavedChanges)
    }

    @Test
    fun failedSyncKeepsSavedPeriodsAndRetryCompletes() = runViewModelTest {
        val repository = FakeRepository()
        val sync = FakeSync(HealthConnectSyncResult.Failed, HealthConnectSyncResult.Synced)
        val viewModel = createViewModel(repository, sync)
        var completed = false

        viewModel.load(date)
        viewModel.addPeriod()
        viewModel.save { completed = true }
        advanceUntilIdle()

        assertEquals(1, repository.periods.value.size)
        assertTrue(viewModel.state.value.syncFailed)
        assertFalse(viewModel.state.value.hasUnsavedChanges)
        assertFalse(completed)

        viewModel.retrySync { completed = true }
        advanceUntilIdle()

        assertTrue(completed)
        assertFalse(viewModel.state.value.syncFailed)
        assertEquals(2, sync.syncedDates.size)
    }

    private fun runViewModelTest(block: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            block()
        } finally {
            viewModels.forEach { it.viewModelScope.cancel() }
            viewModels.clear()
            advanceUntilIdle()
            Dispatchers.resetMain()
        }
    }

    private fun createViewModel(repository: FakeRepository, sync: FakeSync) =
        StepExclusionsViewModel(repository, sync).also(viewModels::add)

    private inner class FakeRepository(initialPeriods: List<StepExclusionPeriod> = emptyList()) :
        ActivityRepository {
        val periods = MutableStateFlow(initialPeriods)

        override fun observeDailySummary(
            date: LocalDate,
            kcalPerStep: Double?,
        ): Flow<DailyActivitySummary> =
            flowOf(
                DailyActivitySummary(
                    rawSteps = 10_000,
                    excludedSteps = 1_500,
                    countedSteps = 8_500,
                    stepEnergyKcal = 0.0,
                    manualEnergyKcal = 0.0,
                    totalEnergyKcal = 0.0,
                )
            )

        override fun observeStepExclusionPeriods(date: LocalDate) = periods

        override suspend fun replaceStepExclusionPeriods(
            date: LocalDate,
            periods: List<StepExclusionPeriod>,
        ) {
            this.periods.value = periods
        }

        override fun observeManualEntry(id: ManualActivityEntryId): Flow<ManualActivityEntry?> =
            error("Not used")
        override fun observeManualEntries(date: LocalDate): Flow<List<ManualActivityEntry>> =
            error("Not used")
        override suspend fun createManualEntry(entry: ManualActivityEntry): ManualActivityEntryId =
            error("Not used")
        override suspend fun updateManualEntry(entry: ManualActivityEntry) = error("Not used")
        override suspend fun deleteManualEntry(id: ManualActivityEntryId) = error("Not used")
        override suspend fun upsertStepSummary(summary: DailyStepSummary) = error("Not used")
    }

    private class FakeSync(vararg results: HealthConnectSyncResult) : HealthConnectActivitySync {
        private val results = results.toMutableList()
        val syncedDates = mutableListOf<List<LocalDate>>()

        override suspend fun availability() = HealthConnectAvailability.Available
        override suspend fun hasReadStepsPermission() = true
        override suspend fun syncSteps(dates: List<LocalDate>): HealthConnectSyncResult {
            syncedDates += dates
            return results.removeFirstOrNull() ?: HealthConnectSyncResult.Synced
        }
        override fun cancelPeriodicSync() = Unit
    }
}

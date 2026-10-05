package com.maksimowiczm.foodyou.app.ui.home.master

import com.maksimowiczm.foodyou.activity.HealthConnectActivitySync
import com.maksimowiczm.foodyou.activity.HealthConnectAvailability
import com.maksimowiczm.foodyou.activity.HealthConnectSyncResult
import com.maksimowiczm.foodyou.activity.domain.entity.DailyActivitySummary
import com.maksimowiczm.foodyou.activity.domain.entity.DailyStepSummary
import com.maksimowiczm.foodyou.activity.domain.entity.ManualActivityEntry
import com.maksimowiczm.foodyou.activity.domain.entity.ManualActivityEntryId
import com.maksimowiczm.foodyou.activity.domain.entity.StepExclusionPeriod
import com.maksimowiczm.foodyou.activity.domain.repository.ActivityRepository
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.common.result.Err
import com.maksimowiczm.foodyou.common.result.Ok
import com.maksimowiczm.foodyou.food.domain.usecase.FddbDiarySyncResult
import com.maksimowiczm.foodyou.food.domain.usecase.recordFddbDiarySyncFailure
import com.maksimowiczm.foodyou.food.domain.usecase.recordFddbDiarySyncResult
import com.maksimowiczm.foodyou.settings.domain.entity.AppLaunchInfo
import com.maksimowiczm.foodyou.settings.domain.entity.EnergyFormat
import com.maksimowiczm.foodyou.settings.domain.entity.GoalDisplayMode
import com.maksimowiczm.foodyou.settings.domain.entity.HomeCard
import com.maksimowiczm.foodyou.settings.domain.entity.NutrientsOrder
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.minus
import kotlinx.datetime.LocalDate

class HomeViewModelTest {
    @Test
    fun activityCompletionWaitsForStepsButNotDiaryOrWeight() = runTest {
        val settings = FakeSettingsRepository(defaultSettings().copy(
            homeSyncHealthConnectEnabled = true, homeSyncFddbDiaryEnabled = true,
            healthConnectWeightEnabled = true,
        ))
        val stepsRelease = CompletableDeferred<Unit>()
        val diaryRelease = CompletableDeferred<Unit>()
        val weightRelease = CompletableDeferred<Unit>()
        var notifications = 0
        val sync = async {
            syncConfiguredHomeSync(
                LocalDate(2026, 5, 18), settings.value, settings,
                syncHealthConnect = { stepsRelease.await() },
                syncWeight = { weightRelease.await() }, hasFddbCredentials = { true },
                syncFddbDiary = { diaryRelease.await(); Ok(FddbDiarySyncResult(0, 0, 0)) },
                onActivitiesSynced = { notifications++ },
            )
        }
        runCurrent()
        assertEquals(0, notifications)
        stepsRelease.complete(Unit)
        runCurrent()
        assertEquals(1, notifications)
        assertFalse(sync.isCompleted)
        diaryRelease.complete(Unit)
        runCurrent()
        assertFalse(sync.isCompleted)
        weightRelease.complete(Unit)
        assertTrue(sync.await().healthConnectSynced)
        assertEquals(1, notifications)
    }

    @Test
    fun activityPublicationFailureDoesNotCancelRemainingSyncSources() = runTest {
        val settings = FakeSettingsRepository(defaultSettings().copy(
            homeSyncHealthConnectEnabled = true, homeSyncFddbDiaryEnabled = true,
            healthConnectWeightEnabled = true,
        ))
        val release = CompletableDeferred<Unit>()
        var diaryFinished = false
        var weightFinished = false
        val sync = async {
            syncConfiguredHomeSync(
                LocalDate(2026, 5, 18), settings.value, settings,
                syncHealthConnect = {}, syncWeight = { release.await(); weightFinished = true },
                hasFddbCredentials = { true },
                syncFddbDiary = { release.await(); diaryFinished = true; Ok(FddbDiarySyncResult(0, 0, 0)) },
                onActivitiesSynced = { error("Snapshot read failed") },
            )
        }
        runCurrent()
        assertFalse(sync.isCompleted)
        release.complete(Unit)
        assertTrue(sync.await().fddbDiarySynced)
        assertTrue(diaryFinished)
        assertTrue(weightFinished)
    }


    @Test
    fun combinedSyncReadsEachDayOnlyBeforeAndAfterAllBranches() = runTest {
        val date = LocalDate(2026, 5, 18)
        val dates = listOf(date, date.minus(1, kotlinx.datetime.DateTimeUnit.DAY))
        val settings = FakeSettingsRepository(defaultSettings().copy(
            homeSyncHealthConnectEnabled = true, homeSyncFddbDiaryEnabled = true,
            healthConnectWeightEnabled = true,
        ))
        val repository = FakeActivityRepository(listOf(100.0, 200.0, 130.0, 240.0))
        val steps = FakeHealthConnectActivitySync(HealthConnectSyncResult.Synced)
        val release = CompletableDeferred<Unit>()
        val sync = async {
            val before = readBurnedEnergySnapshot(dates, settings, repository)
            syncConfiguredHomeSync(
                date, settings.value, settings,
                syncHealthConnect = { syncHomeSteps(date, settings, steps) },
                syncWeight = { release.await() },
                hasFddbCredentials = { true },
                syncFddbDiary = { release.await(); Ok(FddbDiarySyncResult(0, 0, 0)) },
            )
            val after = readBurnedEnergySnapshot(dates, settings, repository)
            dates.associateWith { burnedEnergySyncDeltaKcal(before.getValue(it), after.getValue(it)) }
        }
        runCurrent()
        assertEquals(dates, repository.observedDates)
        assertFalse(sync.isCompleted)
        release.complete(Unit)
        assertEquals(mapOf(dates[0] to 30, dates[1] to 40), sync.await())
        assertEquals(dates + dates, repository.observedDates)
        assertEquals(2, settings.observations)
        assertEquals(listOf(listOf(date)), steps.syncedDates)
    }

    @Test
    fun rawStepSyncHandlesAccessFailuresWithoutReadingCalories() = runTest {
        for (result in listOf(HealthConnectSyncResult.MissingPermission,
            HealthConnectSyncResult.Unavailable, HealthConnectSyncResult.UpdateRequired)) {
            val settings = FakeSettingsRepository(defaultSettings().copy(healthConnectStepsEnabled = true))
            assertEquals(result, syncHomeSteps(LocalDate(2026, 5, 18), settings,
                FakeHealthConnectActivitySync(result)))
            assertFalse(settings.value.healthConnectStepsEnabled)
            assertEquals(0, settings.observations)
        }
    }

    @Test
    fun manualWeightSyncRunsOnlyWhenEnabled() = runTest {
        for (enabled in listOf(false, true)) {
            var weightCalls = 0
            val settings = FakeSettingsRepository(defaultSettings().copy(
                homeSyncHealthConnectEnabled = false, homeSyncFddbDiaryEnabled = false,
                healthConnectWeightEnabled = enabled,
            ))
            syncConfiguredHomeSync(
                LocalDate(2026, 5, 18), settings.value, settings,
                syncHealthConnect = { error("Steps disabled") },
                hasFddbCredentials = { error("Diary disabled") },
                syncFddbDiary = { error("Diary disabled") },
                syncWeight = { weightCalls++ },
            )
            assertEquals(if (enabled) 1 else 0, weightCalls)
        }
    }


    @Test
    fun combinedSyncRejectsRepeatedClicksAndClearsLoadingAfterCompletion() = runTest {
        val runner = HomeSyncRunner(this)
        val release = CompletableDeferred<Unit>()
        var runs = 0
        runner.launch { runs++; release.await() }
        runner.launch { runs++ }
        assertTrue(runner.isSyncing.value)
        runCurrent()
        assertEquals(1, runs)
        release.complete(Unit)
        runCurrent()
        assertFalse(runner.isSyncing.value)
        runner.launch { runs++ }
        runCurrent()
        assertEquals(2, runs)
    }

    @Test
    fun combinedLoadingStateIncludesWeightWhenOtherSourcesAreDisabled() {
        val state = HomeSyncState(
            activitySyncState = HomeActivitySyncState(false, false, emptyMap()),
            fddbSyncState = HomeFddbSyncState.Idle(false),
            healthConnectEnabled = false, fddbDiaryEnabled = false,
            configuredSyncInProgress = true,
        )
        assertTrue(state.isSyncing)
    }

    @Test
    fun configuredSyncDoesNoWorkWhenAllSourcesAreDisabled() = runTest {
        val settings = FakeSettingsRepository().value.copy(
            homeSyncHealthConnectEnabled = false, homeSyncFddbDiaryEnabled = false,
            healthConnectWeightEnabled = false,
        )
        val result = syncConfiguredHomeSync(
            date = LocalDate(2026, 9, 27), settings = settings,
            settingsRepository = FakeSettingsRepository(),
            syncHealthConnect = { error("Steps disabled") },
            syncWeight = { error("Weight disabled") },
            hasFddbCredentials = { error("Diary disabled") },
            syncFddbDiary = { error("Diary disabled") },
        )
        assertEquals(HomeConfiguredSyncResult(false, false, false), result)
    }

    @Test
    fun configuredSyncStartsAllBranchesAndWaitsForWeight() = runTest {
        val settings = FakeSettingsRepository().value.copy(
            homeSyncHealthConnectEnabled = true,
            homeSyncFddbDiaryEnabled = true,
            healthConnectWeightEnabled = true,
        )
        val started = mutableSetOf<String>()
        val release = CompletableDeferred<Unit>()
        val weightRelease = CompletableDeferred<Unit>()
        val sync = async {
            syncConfiguredHomeSync(
                date = LocalDate(2026, 9, 27), settings = settings,
                settingsRepository = FakeSettingsRepository(),
                syncHealthConnect = { started.add("steps"); release.await() },
                syncWeight = { started.add("weight"); weightRelease.await() },
                hasFddbCredentials = { true },
                syncFddbDiary = {
                    started.add("diary"); release.await()
                    Ok(FddbDiarySyncResult(0, 0, 0))
                },
            )
        }
        runCurrent()
        assertEquals(setOf("steps", "weight", "diary"), started)
        release.complete(Unit)
        runCurrent()
        assertFalse(sync.isCompleted)
        weightRelease.complete(Unit)
        assertTrue(sync.await().fddbDiarySynced)
    }

    @Test
    fun branchFailureDoesNotCancelOtherSyncs() = runTest {
        val settings = FakeSettingsRepository().value.copy(
            homeSyncHealthConnectEnabled = true, homeSyncFddbDiaryEnabled = true,
            healthConnectWeightEnabled = true,
        )
        var weightRan = false
        val result = syncConfiguredHomeSync(
            date = LocalDate(2026, 9, 27), settings = settings,
            settingsRepository = FakeSettingsRepository(),
            syncHealthConnect = { error("Health Connect unavailable") },
            syncWeight = { weightRan = true }, hasFddbCredentials = { true },
            syncFddbDiary = { Ok(FddbDiarySyncResult(0, 0, 0)) },
        )
        assertFalse(result.healthConnectSynced)
        assertTrue(result.fddbDiarySynced)
        assertTrue(weightRan)
    }

    @Test
    fun cancellingCombinedSyncCancelsAllBranches() = runTest {
        val settings = FakeSettingsRepository().value.copy(
            homeSyncHealthConnectEnabled = true, homeSyncFddbDiaryEnabled = true,
            healthConnectWeightEnabled = true,
        )
        val cancelled = mutableSetOf<String>()
        suspend fun wait(name: String) {
            try { awaitCancellation() } finally { cancelled.add(name) }
        }
        val job = async {
            syncConfiguredHomeSync(
                date = LocalDate(2026, 9, 27), settings = settings,
                settingsRepository = FakeSettingsRepository(),
                syncHealthConnect = { wait("steps") }, syncWeight = { wait("weight") },
                hasFddbCredentials = { true },
                syncFddbDiary = { wait("diary"); Ok(FddbDiarySyncResult(0, 0, 0)) },
            )
        }
        runCurrent()
        job.cancelAndJoin()
        assertEquals(setOf("steps", "weight", "diary"), cancelled)
    }

    @Test
    fun burnedEnergySyncDeltaKcalReturnsPositiveRoundedIncrease() {
        assertEquals(5, burnedEnergySyncDeltaKcal(before = 955.0, after = 960.0))
    }

    @Test
    fun burnedEnergySyncDeltaKcalReturnsFullIncreaseFromZero() {
        assertEquals(333, burnedEnergySyncDeltaKcal(before = 0.0, after = 333.0))
    }

    @Test
    fun burnedEnergySyncDeltaKcalReturnsOnlySyncIncrease() {
        assertEquals(33, burnedEnergySyncDeltaKcal(before = 267.0, after = 300.0))
    }

    @Test
    fun burnedEnergySyncDeltaKcalReturnsZeroWhenUnchanged() {
        assertEquals(0, burnedEnergySyncDeltaKcal(before = 960.0, after = 960.0))
    }

    @Test
    fun burnedEnergySyncDeltaKcalReturnsZeroWhenDecreased() {
        assertEquals(0, burnedEnergySyncDeltaKcal(before = 960.0, after = 955.0))
    }

    @Test
    fun syncActivitiesForBurnedEnergyDeltaReadsSelectedTodayAndYesterday() = runBlocking {
        val selectedDate = LocalDate(2026, 5, 16)
        val today = LocalDate(2026, 5, 18)
        val yesterday = LocalDate(2026, 5, 17)
        val settingsRepository = FakeSettingsRepository()
        val activityRepository =
            FakeActivityRepository(
                totalEnergyKcal = listOf(955.0, 100.0, 450.0, 960.0, 110.0, 450.0)
            )
        val activitySync = FakeHealthConnectActivitySync(HealthConnectSyncResult.Synced)

        val deltas =
            syncActivitiesForBurnedEnergyDelta(
                date = selectedDate,
                settingsRepository = settingsRepository,
                healthConnectActivitySync = activitySync,
                activityRepository = activityRepository,
                today = today,
            )

        assertEquals(mapOf(selectedDate to 5, today to 10, yesterday to 0), deltas)
        assertEquals(
            listOf(selectedDate, today, yesterday, selectedDate, today, yesterday),
            activityRepository.observedDates,
        )
        assertTrue(activitySync.syncedDates.single().contains(selectedDate))
    }

    @Test
    fun syncActivitiesForBurnedEnergyDeltaIncludesOldSelectedDateWithTodayAndYesterday() =
        runBlocking {
            val selectedDate = LocalDate(2026, 4, 1)
            val today = LocalDate(2026, 5, 18)
            val yesterday = LocalDate(2026, 5, 17)
            val activityRepository =
                FakeActivityRepository(
                    totalEnergyKcal = listOf(10.0, 100.0, 450.0, 20.0, 110.0, 455.0)
                )

            val deltas =
                syncActivitiesForBurnedEnergyDelta(
                    date = selectedDate,
                    settingsRepository = FakeSettingsRepository(),
                    healthConnectActivitySync =
                        FakeHealthConnectActivitySync(HealthConnectSyncResult.Synced),
                    activityRepository = activityRepository,
                    today = today,
                )

            assertEquals(mapOf(selectedDate to 10, today to 10, yesterday to 5), deltas)
        }

    @Test
    fun syncActivitiesForBurnedEnergyDeltaDoesNotDuplicateTodayWhenSelected() = runBlocking {
        val today = LocalDate(2026, 5, 18)
        val yesterday = LocalDate(2026, 5, 17)
        val activityRepository =
            FakeActivityRepository(totalEnergyKcal = listOf(100.0, 450.0, 110.0, 455.0))

        val deltas =
            syncActivitiesForBurnedEnergyDelta(
                date = today,
                settingsRepository = FakeSettingsRepository(),
                healthConnectActivitySync =
                    FakeHealthConnectActivitySync(HealthConnectSyncResult.Synced),
                activityRepository = activityRepository,
                today = today,
            )

        assertEquals(mapOf(today to 10, yesterday to 5), deltas)
        assertEquals(listOf(today, yesterday, today, yesterday), activityRepository.observedDates)
    }

    @Test
    fun syncActivitiesForBurnedEnergyDeltaReturnsNullForFailedOrDisabledSync() = runBlocking {
        val date = LocalDate(2026, 5, 17)
        listOf(HealthConnectSyncResult.Failed, HealthConnectSyncResult.Disabled).forEach { result ->
            val delta =
                syncActivitiesForBurnedEnergyDelta(
                    date = date,
                    settingsRepository = FakeSettingsRepository(),
                    healthConnectActivitySync = FakeHealthConnectActivitySync(result),
                    activityRepository =
                        FakeActivityRepository(totalEnergyKcal = listOf(960.0, 100.0)),
                    today = date,
                )

            assertNull(delta)
        }
    }

    @Test
    fun syncActivitiesForBurnedEnergyDeltaReturnsNullAndDisablesForPermissionProblem() =
        runBlocking {
            val date = LocalDate(2026, 5, 17)
            val settingsRepository = FakeSettingsRepository()

            val delta =
                syncActivitiesForBurnedEnergyDelta(
                    date = date,
                    settingsRepository = settingsRepository,
                    healthConnectActivitySync =
                        FakeHealthConnectActivitySync(HealthConnectSyncResult.MissingPermission),
                    activityRepository =
                        FakeActivityRepository(totalEnergyKcal = listOf(960.0, 100.0)),
                    today = date,
                )

            assertNull(delta)
            assertFalse(settingsRepository.value.healthConnectStepsEnabled)
        }

    @Test
    fun configuredHomeSyncDefaultsToHealthConnectOnly() = runBlocking {
        val settingsRepository = FakeSettingsRepository()
        var healthSyncs = 0
        var fddbSyncs = 0

        val result =
            syncConfiguredHomeSync(
                date = LocalDate(2026, 5, 17),
                settings = settingsRepository.value,
                settingsRepository = settingsRepository,
                syncHealthConnect = { healthSyncs += 1 },
                hasFddbCredentials = { true },
                syncFddbDiary = {
                    fddbSyncs += 1
                    Ok(FddbDiarySyncResult(imported = 0, skipped = 0, failed = 0))
                },
            )

        assertEquals(HomeConfiguredSyncResult(healthConnectSynced = true, fddbDiarySynced = false, fddbMissingCredentials = false), result)
        assertEquals(1, healthSyncs)
        assertEquals(0, fddbSyncs)
    }

    @Test
    fun configuredHomeSyncRunsHealthConnectAndFddbWhenBothEnabled() = runBlocking {
        val settingsRepository =
            FakeSettingsRepository(defaultSettings().copy(homeSyncFddbDiaryEnabled = true))
        var healthSyncs = 0
        var fddbSyncs = 0

        val result =
            syncConfiguredHomeSync(
                date = LocalDate(2026, 5, 17),
                settings = settingsRepository.value,
                settingsRepository = settingsRepository,
                syncHealthConnect = { healthSyncs += 1 },
                hasFddbCredentials = { true },
                syncFddbDiary = {
                    fddbSyncs += 1
                    val result = FddbDiarySyncResult(imported = 2, skipped = 1, failed = 0)
                    settingsRepository.recordFddbDiarySyncResult(result)
                    Ok(result)
                },
            )

        assertEquals(HomeConfiguredSyncResult(healthConnectSynced = true, fddbDiarySynced = true, fddbMissingCredentials = false), result)
        assertEquals(1, healthSyncs)
        assertEquals(1, fddbSyncs)
        assertEquals(2, settingsRepository.value.fddbDiarySyncLastImported)
        assertEquals(1, settingsRepository.value.fddbDiarySyncLastSkipped)
        assertEquals(0, settingsRepository.value.fddbDiarySyncLastFailed)
        assertNull(settingsRepository.value.fddbDiarySyncLastErrorMessage)
    }

    @Test
    fun configuredHomeSyncStoresFddbFailedCountAsFailure() = runBlocking {
        val settingsRepository =
            FakeSettingsRepository(defaultSettings().copy(homeSyncFddbDiaryEnabled = true))

        syncConfiguredHomeSync(
            date = LocalDate(2026, 5, 17),
            settings = settingsRepository.value,
            settingsRepository = settingsRepository,
            syncHealthConnect = {},
            hasFddbCredentials = { true },
            syncFddbDiary = {
                val result = FddbDiarySyncResult(
                    imported = 0,
                    skipped = 0,
                    failed = 1,
                    errorMessage = "FDDB debug details",
                )
                settingsRepository.recordFddbDiarySyncResult(result)
                Ok(result)
            },
        )

        assertEquals(1, settingsRepository.value.fddbDiarySyncLastFailed)
        assertEquals("FDDB debug details", settingsRepository.value.fddbDiarySyncLastErrorMessage)
    }

    @Test
    fun configuredHomeSyncClearsFddbFailureAfterSuccessfulFddbSync() = runBlocking {
        val settingsRepository =
            FakeSettingsRepository(
                defaultSettings()
                    .copy(
                        homeSyncFddbDiaryEnabled = true,
                        fddbDiarySyncLastImported = 0,
                        fddbDiarySyncLastSkipped = 0,
                        fddbDiarySyncLastFailed = 2,
                        fddbDiarySyncLastErrorMessage = "Failed",
                    )
            )

        syncConfiguredHomeSync(
            date = LocalDate(2026, 5, 17),
            settings = settingsRepository.value,
            settingsRepository = settingsRepository,
            syncHealthConnect = {},
            hasFddbCredentials = { true },
            syncFddbDiary = {
                val result = FddbDiarySyncResult(imported = 1, skipped = 0, failed = 0)
                settingsRepository.recordFddbDiarySyncResult(result)
                Ok(result)
            },
        )

        assertEquals(0, settingsRepository.value.fddbDiarySyncLastFailed)
        assertNull(settingsRepository.value.fddbDiarySyncLastErrorMessage)
    }

    @Test
    fun configuredHomeSyncStoresFddbExceptionAsFailure() = runBlocking {
        val settingsRepository =
            FakeSettingsRepository(defaultSettings().copy(homeSyncFddbDiaryEnabled = true))

        syncConfiguredHomeSync(
            date = LocalDate(2026, 5, 17),
            settings = settingsRepository.value,
            settingsRepository = settingsRepository,
            syncHealthConnect = {},
            hasFddbCredentials = { true },
            syncFddbDiary = {
                val throwable = IllegalStateException("Network down")
                settingsRepository.recordFddbDiarySyncFailure(throwable)
                Err(throwable)
            },
        )

        assertEquals(1, settingsRepository.value.fddbDiarySyncLastFailed)
        val errorMessage = settingsRepository.value.fddbDiarySyncLastErrorMessage ?: error("Expected stacktrace")
        assertContains(errorMessage, "IllegalStateException")
        assertContains(errorMessage, "Network down")
        assertContains(errorMessage, "HomeViewModelTest")
    }

    @Test
    fun configuredHomeSyncMissingFddbCredentialsSkipsOnlyFddb() = runBlocking {
        val settingsRepository =
            FakeSettingsRepository(defaultSettings().copy(homeSyncFddbDiaryEnabled = true))
        var healthSyncs = 0
        var fddbSyncs = 0

        val result =
            syncConfiguredHomeSync(
                date = LocalDate(2026, 5, 17),
                settings = settingsRepository.value,
                settingsRepository = settingsRepository,
                syncHealthConnect = { healthSyncs += 1 },
                hasFddbCredentials = { false },
                syncFddbDiary = {
                    fddbSyncs += 1
                    Ok(FddbDiarySyncResult(imported = 0, skipped = 0, failed = 0))
                },
            )

        assertEquals(HomeConfiguredSyncResult(healthConnectSynced = true, fddbDiarySynced = false, fddbMissingCredentials = true), result)
        assertEquals(1, healthSyncs)
        assertEquals(0, fddbSyncs)
    }

    private class FakeSettingsRepository(
        initialValue: Settings = defaultSettings()
    ) : UserPreferencesRepository<Settings> {
        private val state = MutableStateFlow(initialValue)
        var observations = 0

        val value: Settings
            get() = state.value

        override fun observe(): Flow<Settings> {
            observations++
            return state
        }

        override suspend fun update(transform: Settings.() -> Settings) {
            state.value = state.value.transform()
        }
    }

    private class FakeHealthConnectActivitySync(
        private val result: HealthConnectSyncResult
    ) : HealthConnectActivitySync {
        val syncedDates = mutableListOf<List<LocalDate>>()

        override suspend fun availability(): HealthConnectAvailability =
            HealthConnectAvailability.Available

        override suspend fun hasReadStepsPermission(): Boolean = true

        override suspend fun syncStepsForHome(selectedDate: LocalDate): HealthConnectSyncResult {
            syncedDates += listOf(selectedDate)
            return result
        }

        override suspend fun syncSteps(dates: List<LocalDate>): HealthConnectSyncResult {
            syncedDates += dates
            return result
        }

        override fun cancelPeriodicSync() = Unit
    }

    private class FakeActivityRepository(totalEnergyKcal: List<Double>) : ActivityRepository {
        override suspend fun updateImportedEntry(entry: com.maksimowiczm.foodyou.training.ImportedActivity) = error("Not used")
        override suspend fun deleteImportedEntry(id: com.maksimowiczm.foodyou.training.ImportedActivityId) = error("Not used")

        private val totalEnergyKcal = totalEnergyKcal.toMutableList()
        val observedDates = mutableListOf<LocalDate>()

        override fun observeManualEntry(id: ManualActivityEntryId): Flow<ManualActivityEntry?> =
            flowOf(null)

        override fun observeManualEntries(date: LocalDate): Flow<List<ManualActivityEntry>> =
            flowOf(emptyList())

        override fun observeDailySummary(
            date: LocalDate,
            kcalPerStep: Double?,
        ): Flow<DailyActivitySummary> {
            observedDates += date
            return flowOf(
                DailyActivitySummary(
                    rawSteps = 0,
                    excludedSteps = 0,
                    countedSteps = 0,
                    stepEnergyKcal = 0.0,
                    manualEnergyKcal = 0.0,
                    totalEnergyKcal = totalEnergyKcal.removeAt(0),
                )
            )
        }

        override suspend fun createManualEntry(entry: ManualActivityEntry): ManualActivityEntryId =
            error("Not used")

        override suspend fun updateManualEntry(entry: ManualActivityEntry) = error("Not used")

        override suspend fun deleteManualEntry(id: ManualActivityEntryId) = error("Not used")

        override suspend fun upsertStepSummary(summary: DailyStepSummary) = error("Not used")

        override fun observeStepExclusionPeriods(date: LocalDate): Flow<List<StepExclusionPeriod>> =
            flowOf(emptyList())

        override suspend fun replaceStepExclusionPeriods(
            date: LocalDate,
            periods: List<StepExclusionPeriod>,
        ) = error("Not used")
    }

    private companion object {
        fun defaultSettings(): Settings =
            Settings(
                lastRememberedVersion = null,
                hidePreviewDialog = false,
                showTranslationWarning = false,
                nutrientsOrder = NutrientsOrder.defaultOrder,
                secureScreen = false,
                homeCardOrder = HomeCard.defaultOrder,
                expandGoalCard = false,
                goalDisplayMode = GoalDisplayMode.Normal,
                dietEnergyDeficitKcal = null,
                onboardingFinished = true,
                energyFormat = EnergyFormat.DEFAULT,
                appLaunchInfo =
                    AppLaunchInfo(
                        firstLaunch = null,
                        firstLaunchCurrentVersion = null,
                        launchesCount = 0,
                    ),
                stepsCaloriesPerStepKcal = null,
                healthConnectStepsEnabled = true,
                healthConnectStepsLastSyncedEpochSeconds = null,
            )
    }
}

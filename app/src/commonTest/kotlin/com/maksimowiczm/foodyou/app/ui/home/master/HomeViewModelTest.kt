package com.maksimowiczm.foodyou.app.ui.home.master

import com.maksimowiczm.foodyou.activity.HealthConnectActivitySync
import com.maksimowiczm.foodyou.activity.HealthConnectAvailability
import com.maksimowiczm.foodyou.activity.HealthConnectSyncResult
import com.maksimowiczm.foodyou.activity.domain.entity.DailyActivitySummary
import com.maksimowiczm.foodyou.activity.domain.entity.DailyStepSummary
import com.maksimowiczm.foodyou.activity.domain.entity.ManualActivityEntry
import com.maksimowiczm.foodyou.activity.domain.entity.ManualActivityEntryId
import com.maksimowiczm.foodyou.activity.domain.repository.ActivityRepository
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.food.domain.usecase.FddbDiarySyncResult
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate

class HomeViewModelTest {
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
    fun healthConnectStepsSyncDatesIncludesLookbackWindowThroughToday() {
        val today = LocalDate(2026, 5, 17)

        val dates =
            healthConnectStepsSyncDates(
                selectedDate = today,
                today = today,
                lookbackDays = 3,
            )

        assertEquals(
            listOf(
                LocalDate(2026, 5, 14),
                LocalDate(2026, 5, 15),
                LocalDate(2026, 5, 16),
                LocalDate(2026, 5, 17),
            ),
            dates,
        )
    }

    @Test
    fun healthConnectStepsSyncDatesIncludesSelectedDateOutsideLookbackWindow() {
        val today = LocalDate(2026, 5, 17)
        val selectedDate = LocalDate(2026, 4, 1)

        val dates =
            healthConnectStepsSyncDates(
                selectedDate = selectedDate,
                today = today,
                lookbackDays = 3,
            )

        assertEquals(
            listOf(
                selectedDate,
                LocalDate(2026, 5, 14),
                LocalDate(2026, 5, 15),
                LocalDate(2026, 5, 16),
                LocalDate(2026, 5, 17),
            ),
            dates,
        )
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
                    FddbDiarySyncResult(imported = 0, skipped = 0, failed = 0)
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
                    FddbDiarySyncResult(imported = 2, skipped = 1, failed = 0)
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
                FddbDiarySyncResult(
                    imported = 0,
                    skipped = 0,
                    failed = 1,
                    errorMessage = "FDDB debug details",
                )
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
            syncFddbDiary = { FddbDiarySyncResult(imported = 1, skipped = 0, failed = 0) },
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
            syncFddbDiary = { error("Network down") },
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
                    FddbDiarySyncResult(imported = 0, skipped = 0, failed = 0)
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

        val value: Settings
            get() = state.value

        override fun observe(): Flow<Settings> = state

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

        override suspend fun syncSteps(dates: List<LocalDate>): HealthConnectSyncResult {
            syncedDates += dates
            return result
        }

        override fun cancelPeriodicSync() = Unit
    }

    private class FakeActivityRepository(totalEnergyKcal: List<Double>) : ActivityRepository {
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
                    steps = 0,
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

package com.maksimowiczm.foodyou.activity

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.maksimowiczm.foodyou.activity.domain.entity.*
import com.maksimowiczm.foodyou.activity.domain.repository.ActivityRepository
import com.maksimowiczm.foodyou.app.ui.activity.enableStepSync
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
import com.maksimowiczm.foodyou.settings.infrastructure.DataStoreSettingsRepository
import com.maksimowiczm.foodyou.sync.*
import com.maksimowiczm.foodyou.training.ImportedActivity
import com.maksimowiczm.foodyou.training.ImportedActivityId
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.*

class StepSyncCoordinatorTest {
    private val today = LocalDate(2026, 10, 3)
    private val zone = TimeZone.of("Europe/Vienna")

    @Test
    fun firstSyncBatches31DaysThenRefreshesOnlyRecentAndSelectedDays() = runTest {
        val f = fixture()
        assertEquals(HealthConnectSyncResult.Synced, f.coordinator.syncForHome(today))
        assertEquals(31, f.repository.saved.size)
        assertEquals(1, f.source.groupedCalls.size)
        assertEquals(0, f.source.singleCalls.size)
        assertEquals(1, f.repository.readCalls)
        assertEquals(1, f.repository.writeCalls)
        assertEquals(today.toEpochDays(), f.settings.value.healthConnectStepsLastFullSyncEpochDay)
        assertEquals(zone.id, f.settings.value.healthConnectStepsLastFullSyncTimeZoneId)

        f.source.groupedCalls.clear()
        val selected = LocalDate(2026, 5, 1)
        assertEquals(HealthConnectSyncResult.Synced, f.coordinator.syncForHome(selected))
        assertEquals(listOf(selected, today.minus(1, DateTimeUnit.DAY), today), f.repository.saved.map { it.date })
        assertEquals(1, f.source.groupedCalls.size)
        assertEquals(1, f.source.singleCalls.size)
        assertEquals(2, f.repository.writeCalls)
    }

    @Test
    fun dayAndZoneChangesRequireAnotherFullSync() = runTest {
        val f = fixture()
        f.coordinator.syncForHome(today)
        f.clock.instant += 24.hours
        f.coordinator.syncForHome(today.plus(1, DateTimeUnit.DAY))
        assertEquals(31, f.repository.saved.size)
        assertEquals(today.plus(1, DateTimeUnit.DAY).toEpochDays(), f.settings.value.healthConnectStepsLastFullSyncEpochDay)
        f.zone = TimeZone.UTC
        f.coordinator.syncForHome(today.plus(1, DateTimeUnit.DAY))
        assertEquals(31, f.repository.saved.size)
        assertEquals(f.zone.id, f.settings.value.healthConnectStepsLastFullSyncTimeZoneId)
    }

    @Test
    fun reEnablingInvalidatesCoverageWhileAlreadyEnabledPreservesIt() = runTest {
        val f = fixture()
        f.coordinator.syncForHome(today)
        assertEquals(f.settings.value, f.settings.value.enableStepSync())
        f.settings.update { copy(healthConnectStepsEnabled = false).enableStepSync() }
        assertNull(f.settings.value.healthConnectStepsLastFullSyncEpochDay)
        f.coordinator.syncForHome(today)
        assertEquals(31, f.repository.saved.size)
    }

    @Test
    fun explicitDateSyncDoesNotEstablishOrAdvanceFullCoverage() = runTest {
        val f = fixture()
        val old = LocalDate(2026, 8, 1)
        f.coordinator.syncDates(listOf(old, old))
        assertEquals(listOf(old), f.repository.saved.map { it.date })
        assertNull(f.settings.value.healthConnectStepsLastFullSyncEpochDay)
        assertNotNull(f.settings.value.healthConnectStepsLastSyncedEpochSeconds)
        f.coordinator.syncForHome(today)
        assertEquals(31, f.repository.saved.size)
    }

    @Test
    fun sparseBucketsOverwriteOldCountsWithZeroAndSkipUnneededExclusionReads() = runTest {
        val f = fixture()
        f.source.values[today] = 1_000L
        f.repository.periods = listOf(StepExclusionPeriod(today.minus(1, DateTimeUnit.DAY), 480, 540))
        f.coordinator.syncForHome(today)
        assertEquals(1_000L, f.repository.saved.single { it.date == today }.rawSteps)
        assertTrue(f.repository.saved.filter { it.date != today }.all { it.rawSteps == 0L && it.excludedSteps == 0L })
        assertTrue(f.source.singleCalls.isEmpty())
        f.source.values.clear()
        f.coordinator.syncForHome(today)
        assertEquals(0L, f.repository.saved.single { it.date == today }.rawSteps)
    }

    @Test
    fun exclusionsAreMergedBoundedAndLimitedToFourConcurrentReads() = runTest {
        val f = fixture()
        f.source.values[today] = 1_000L
        f.source.excludedValue = 300L
        f.source.delayMillis = 10
        f.repository.periods = (0..5).map { StepExclusionPeriod(today, it * 120, it * 120 + 60) } +
            StepExclusionPeriod(today, 30, 60)
        f.coordinator.syncDates(listOf(today))
        assertEquals(7, f.source.singleCalls.size) // One total plus six merged exclusions.
        assertEquals(4, f.source.maxConcurrent)
        assertEquals(1_000L, f.repository.saved.single().excludedSteps)
    }

    @Test
    fun changedExclusionsAreRecalculatedByExplicitSync() = runTest {
        val f = fixture()
        f.source.values[today] = 1_000L
        f.coordinator.syncForHome(today)
        f.repository.periods = listOf(StepExclusionPeriod(today, 480, 540))
        f.source.excludedValue = 250L
        f.coordinator.syncDates(listOf(today))
        assertEquals(250L, f.repository.saved.single().excludedSteps)
        assertEquals(today.toEpochDays(), f.settings.value.healthConnectStepsLastFullSyncEpochDay)
    }

    @Test
    fun failuresAndPermissionLossDoNotSaveCoverageAndCanBeRetried() = runTest {
        val f = fixture()
        for (failure in listOf(IllegalStateException("Failed read"), StepSyncPermissionException())) {
            f.source.failure = failure
            assertEquals(if (failure is StepSyncPermissionException) HealthConnectSyncResult.MissingPermission
                else HealthConnectSyncResult.Failed, f.coordinator.syncForHome(today))
            assertNull(f.settings.value.healthConnectStepsLastFullSyncEpochDay)
            assertEquals(0, f.repository.writeCalls)
        }
        f.source.failure = null
        f.repository.failWrite = true
        assertEquals(HealthConnectSyncResult.Failed, f.coordinator.syncForHome(today))
        assertNull(f.settings.value.healthConnectStepsLastFullSyncEpochDay)
        assertNull(f.settings.value.healthConnectStepsLastSyncedEpochSeconds)
        f.repository.failWrite = false
        assertEquals(HealthConnectSyncResult.Synced, f.coordinator.syncForHome(today))
        assertEquals(31, f.repository.saved.size)
    }

    @Test
    fun cancellationDoesNotSaveAndReleasesMutexForRetry() = runTest {
        val f = fixture()
        val started = CompletableDeferred<Unit>()
        f.source.beforeRead = { started.complete(Unit); awaitCancellation() }
        val job = launch { f.coordinator.syncForHome(today) }
        started.await()
        job.cancelAndJoin()
        assertEquals(0, f.repository.writeCalls)
        assertNull(f.settings.value.healthConnectStepsLastFullSyncEpochDay)
        f.source.beforeRead = {}
        assertEquals(HealthConnectSyncResult.Synced, f.coordinator.syncForHome(today))
    }

    @Test
    fun concurrentHomeSyncsWaitAndSecondReplansAfterSuccessfulFullSync() = runTest {
        val f = fixture()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        f.source.beforeRead = { started.complete(Unit); release.await() }
        val first = async { f.coordinator.syncForHome(today) }
        started.await()
        val second = async { f.coordinator.syncForHome(today) }
        runCurrent()
        assertEquals(1, f.source.groupedCalls.size)
        release.complete(Unit)
        assertEquals(HealthConnectSyncResult.Synced, first.await())
        assertEquals(HealthConnectSyncResult.Synced, second.await())
        assertEquals(2, f.repository.saved.size)
        assertEquals(2, f.source.groupedCalls.size)
    }

    @Test
    fun explicitSyncWaitsForHomeAndAppliesExclusionsChangedDuringThatSync() = runTest {
        val f = fixture()
        f.source.values[today] = 1_000L
        f.source.excludedValue = 250L
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        f.source.beforeRead = { started.complete(Unit); release.await() }
        val home = async { f.coordinator.syncForHome(today) }
        started.await()
        f.repository.periods = listOf(StepExclusionPeriod(today, 480, 540))
        val explicit = async { f.coordinator.syncDates(listOf(today)) }
        runCurrent()
        assertEquals(1, f.repository.readCalls)
        assertTrue(f.source.singleCalls.isEmpty())
        release.complete(Unit)
        assertEquals(HealthConnectSyncResult.Synced, home.await())
        assertEquals(HealthConnectSyncResult.Synced, explicit.await())
        assertEquals(250L, f.repository.saved.single().excludedSteps)
        assertEquals(today.toEpochDays(), f.settings.value.healthConnectStepsLastFullSyncEpochDay)
    }

    @Test
    fun fullSyncIncludesOldSelectionWithoutReadingTheGap() = runTest {
        val f = fixture()
        val selected = LocalDate(2026, 5, 1)
        f.coordinator.syncForHome(selected)
        assertEquals(32, f.repository.saved.size)
        assertEquals(selected, f.repository.saved.first().date)
        assertEquals(1, f.source.groupedCalls.size)
        assertEquals(1, f.source.singleCalls.size)
        assertEquals(today.toEpochDays(), f.settings.value.healthConnectStepsLastFullSyncEpochDay)
    }

    @Test
    fun syncCrossingMidnightMarksTheDayActuallyRead() = runTest {
        val f = fixture()
        f.source.beforeRead = { f.clock.instant += 24.hours }
        f.coordinator.syncForHome(today)
        assertEquals(today.toEpochDays(), f.settings.value.healthConnectStepsLastFullSyncEpochDay)
        assertEquals(today, f.repository.saved.last().date)
    }

    @Test
    fun timeChangesKeepExactDayBoundariesAndSeparateHistoricalSelection() {
        for ((date, length) in listOf(LocalDate(2026, 3, 29) to 23.hours, LocalDate(2026, 10, 25) to 25.hours)) {
            val dates = (-2..2).map { date.plus(it, DateTimeUnit.DAY) }
            val batches = stepAggregationBatches(dates + dates.first(), zone)
            assertEquals(3, batches.size)
            assertEquals(dates, batches.flatMap { it.dates })
            assertEquals(length, batches[1].end - batches[1].start)
            assertFalse(batches[1].grouped)
            batches.forEach { batch ->
                assertEquals(batch.dates.first().atStartOfDayIn(zone), batch.start)
                assertEquals(batch.dates.last().plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone), batch.end)
            }
        }
        val old = LocalDate(2026, 1, 1)
        assertEquals(2, stepAggregationBatches(listOf(old, today.minus(1, DateTimeUnit.DAY), today), zone).size)
    }

    @Test
    fun logExplainsPolicyAndRecordsTheSeparateStages() = runTest {
        val f = fixture()
        val store = object : SyncLogStore {
            override val runs = MutableStateFlow<List<SyncLogRun>>(emptyList())
            override suspend fun update(transform: (List<SyncLogRun>) -> List<SyncLogRun>) { runs.value = transform(runs.value) }
        }
        val coordinator = StepSyncCoordinator(f.repository, f.settings, f.source, { null }, SyncLog(store), f.clock, { zone })
        coordinator.syncForHome(today)
        coordinator.syncForHome(today)
        assertTrue(store.runs.value[0].steps.any { it.title == "Vollabgleich · 31 Tage" && it.detail.contains("kein vollständiger") })
        assertTrue(store.runs.value[1].steps.any { it.title == "Kurzabgleich · 2 Tage" && it.detail.contains("heute bereits") })
        assertTrue(store.runs.value.all { run -> run.steps.any { it.title == "Tageswerte aus Health Connect lesen" } &&
            run.steps.any { it.title == "Schrittzahlen gesammelt speichern" } })
    }

    @Test
    fun disabledOrUnavailableSyncDoesNotReadOrAdvanceMarkers() = runTest {
        val f = fixture()
        f.settings.update { copy(healthConnectStepsEnabled = false) }
        assertEquals(HealthConnectSyncResult.Disabled, f.coordinator.syncForHome(today))
        f.settings.update { copy(healthConnectStepsEnabled = true) }
        f.access = HealthConnectSyncResult.Unavailable
        assertEquals(HealthConnectSyncResult.Unavailable, f.coordinator.syncForHome(today))
        assertTrue(f.source.groupedCalls.isEmpty())
        assertNull(f.settings.value.healthConnectStepsLastFullSyncEpochDay)
    }

    private suspend fun fixture(): Fixture {
        val store = object : DataStore<Preferences> {
            private val state = MutableStateFlow(emptyPreferences())
            override val data: Flow<Preferences> = state
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                transform(state.value).also { state.value = it }
        }
        return Fixture(DataStoreSettingsRepository(store).observe().first().copy(healthConnectStepsEnabled = true))
    }

    private inner class Fixture(initial: Settings) {
        val settings = TestSettings(initial)
        val repository = TestActivityRepository()
        val source = TestSource()
        val clock = MutableClock(Instant.parse("2026-10-03T12:00:00Z"))
        var zone = this@StepSyncCoordinatorTest.zone
        var access: HealthConnectSyncResult? = null
        val coordinator = StepSyncCoordinator(repository, settings, source, { access }, clock = clock, timeZone = { zone })
    }
}

private class MutableClock(var instant: Instant) : Clock { override fun now() = instant }

private class TestSettings(initial: Settings) : UserPreferencesRepository<Settings> {
    private val state = MutableStateFlow(initial)
    val value get() = state.value
    override fun observe(): Flow<Settings> = state
    override suspend fun update(transform: Settings.() -> Settings) { state.value = state.value.transform() }
}

private class TestSource : StepAggregationSource {
    val groupedCalls = mutableListOf<Pair<Instant, Instant>>()
    val singleCalls = mutableListOf<Pair<Instant, Instant>>()
    val values = mutableMapOf<LocalDate, Long>()
    var excludedValue = 0L
    var failure: Exception? = null
    var beforeRead: suspend () -> Unit = {}
    var delayMillis = 0L
    var maxConcurrent = 0
    private var concurrent = 0

    override suspend fun aggregateByDay(start: Instant, end: Instant, timeZone: TimeZone): Map<LocalDate, Long> {
        groupedCalls += start to end
        failure?.let { throw it }
        beforeRead()
        return values.filterKeys { it.atStartOfDayIn(timeZone) >= start && it.atStartOfDayIn(timeZone) < end }
    }

    override suspend fun aggregate(start: Instant, end: Instant): Long {
        singleCalls += start to end
        failure?.let { throw it }
        concurrent++
        maxConcurrent = maxOf(maxConcurrent, concurrent)
        try {
            delay(delayMillis)
            return if (end - start >= 23.hours) values[start.toLocalDateTime(TimeZone.of("Europe/Vienna")).date] ?: 0L else excludedValue
        } finally { concurrent-- }
    }
}

private class TestActivityRepository : ActivityRepository {
    var periods = emptyList<StepExclusionPeriod>()
    var saved = emptyList<DailyStepSummary>()
    var readCalls = 0
    var writeCalls = 0
    var failWrite = false
    override suspend fun readStepExclusionPeriods(dates: List<LocalDate>): List<StepExclusionPeriod> {
        readCalls++
        return periods.filter { it.date in dates }
    }
    override suspend fun upsertStepSummaries(summaries: List<DailyStepSummary>) {
        if (failWrite) error("Failed transaction")
        writeCalls++
        saved = summaries
    }
    override fun observeStepExclusionPeriods(date: LocalDate): Flow<List<StepExclusionPeriod>> = error("Must read in bulk")
    override suspend fun upsertStepSummary(summary: DailyStepSummary) = error("Must write in bulk")
    override suspend fun updateImportedEntry(entry: ImportedActivity) = error("Not used")
    override suspend fun deleteImportedEntry(id: ImportedActivityId) = error("Not used")
    override fun observeManualEntry(id: ManualActivityEntryId): Flow<ManualActivityEntry?> = error("Not used")
    override fun observeManualEntries(date: LocalDate): Flow<List<ManualActivityEntry>> = error("Not used")
    override fun observeDailySummary(date: LocalDate, kcalPerStep: Double?): Flow<DailyActivitySummary> = error("Not used")
    override suspend fun createManualEntry(entry: ManualActivityEntry): ManualActivityEntryId = error("Not used")
    override suspend fun updateManualEntry(entry: ManualActivityEntry) = error("Not used")
    override suspend fun deleteManualEntry(id: ManualActivityEntryId) = error("Not used")
    override suspend fun replaceStepExclusionPeriods(date: LocalDate, periods: List<StepExclusionPeriod>) = error("Not used")
}

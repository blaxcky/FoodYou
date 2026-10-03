package com.maksimowiczm.foodyou.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlin.test.assertFailsWith

class SyncLogTest {
    @Test
    fun parallelStepsKeepStartOrderAndIndependentDurations() = runTest {
        val store = TestStore()
        val time = TestTimeSource()
        val log = SyncLog(store, time)
        val release = CompletableDeferred<Unit>()
        val job = async {
            log.run("Startseite") {
                coroutineScope {
                    val slow = async { log.step("FDDB") { release.await() } }
                    runCurrent()
                    time += 50.milliseconds
                    log.step("Schritte") { time += 30.milliseconds }
                    val live = store.runs.value.single()
                    assertEquals(SyncLogStatus.Running, live.status)
                    assertEquals(listOf(SyncLogStatus.Running, SyncLogStatus.Success), live.steps.map { it.status })
                    time += 20.milliseconds
                    release.complete(Unit)
                    slow.await()
                }
            }
        }
        job.await()
        val run = store.runs.value.single()
        assertEquals(listOf("FDDB", "Schritte"), run.steps.map { it.title })
        assertEquals(listOf(1, 2), run.steps.map { it.number })
        assertEquals(listOf(0L, 50L), run.steps.map { it.offsetMillis })
        assertEquals(listOf(100L, 30L), run.steps.map { it.durationMillis })
        assertEquals(100L, run.durationMillis)
        assertEquals(SyncLogStatus.Success, run.status)
    }

    @Test
    fun nestedFailuresAndSkippedReasonsRemainInOneRun() = runTest {
        val store = TestStore()
        val log = SyncLog(store)
        log.run("Startseite") {
            log.step("FDDB") {
                log.step("Server", { SyncLogOutcome.failed("HTTP 503") }) { Unit }
            }
            log.recordSyncSkipped("Gewicht", "Deaktiviert")
        }
        val run = store.runs.value.single()
        assertEquals(SyncLogStatus.Failed, run.status)
        assertEquals(SyncLogStatus.Failed, run.steps.first().status)
        assertEquals(1, run.steps[1].parentNumber)
        assertEquals("HTTP 503", run.steps[1].detail)
        assertEquals(SyncLogStatus.Skipped, run.steps.last().status)
        assertEquals("Deaktiviert", run.steps.last().detail)
    }

    @Test
    fun callerCancellationFinalizesRunAndActiveStep() = runTest {
        val store = TestStore()
        val log = SyncLog(store)
        val job = async { log.run("Startseite") { log.step("Server") { awaitCancellation() } } }
        runCurrent()
        job.cancelAndJoin()
        val run = store.runs.value.single()
        assertEquals(SyncLogStatus.Cancelled, run.status)
        assertEquals(SyncLogStatus.Cancelled, run.steps.single().status)
        assertTrue(run.durationMillis != null)
    }

    @Test
    fun timeoutIsReportedAsFailureRatherThanUserCancellation() = runTest {
        val store = TestStore()
        val log = SyncLog(store)
        assertFailsWith<TimeoutCancellationException> {
            log.step("Server") { withTimeout(10) { awaitCancellation() } }
        }
        assertEquals(SyncLogStatus.Failed, store.runs.value.single().status)
        assertEquals("Zeitlimit überschritten", store.runs.value.single().steps.single().detail)
    }

    @Test
    fun rapidStepUpdatesAreCoalescedButRunBoundariesPersistImmediately() = runTest {
        val dataStore = TestDataStore()
        val log = SyncLog(DataStoreSyncLogStore(dataStore, backgroundScope))
        val release = CompletableDeferred<Unit>()
        val job = async { log.run("Sync") {
            repeat(20) { log.step("Detail") { Unit } }
            release.await()
        } }
        runCurrent()
        assertEquals(1, dataStore.writes)
        advanceTimeBy(201)
        runCurrent()
        assertEquals(2, dataStore.writes)
        val live = DataStoreSyncLogStore(dataStore, backgroundScope).runs.first().single()
        assertEquals(20, live.steps.size)
        release.complete(Unit)
        job.await()
        assertEquals(3, dataStore.writes)
        assertEquals(SyncLogStatus.Success, DataStoreSyncLogStore(dataStore, backgroundScope).runs.first().single().status)
    }

    @Test
    fun persistedLogsSurviveRestartAndUnfinishedRunsAreMarkedInterrupted() = runTest {
        val dataStore = TestDataStore()
        val first = SyncLog(DataStoreSyncLogStore(dataStore, backgroundScope))
        first.step("Schritte") { Unit }
        val store = DataStoreSyncLogStore(dataStore, backgroundScope)
        store.update { runs -> runs + SyncLogRun(2, "FDDB", 123, steps = listOf(SyncLogStep(1, null, "Server", 123, 0))) }
        val reloaded = SyncLog(DataStoreSyncLogStore(dataStore, backgroundScope)).runs.first()
        assertEquals(SyncLogStatus.Success, reloaded.first().status)
        assertEquals(SyncLogStatus.Interrupted, reloaded.last().status)
        assertEquals(SyncLogStatus.Interrupted, reloaded.last().steps.single().status)
        assertEquals(null, reloaded.last().durationMillis)
    }

    @Test
    fun clearingKeepsActiveRunsAndUnrelatedPreferences() = runTest {
        val key = stringPreferencesKey("settings:other")
        val dataStore = TestDataStore(mutablePreferencesOf(key to "keep"))
        val log = SyncLog(DataStoreSyncLogStore(dataStore, backgroundScope))
        log.step("Fertig") { Unit }
        val job = async { log.run("Läuft") { log.step("Server") { awaitCancellation() } } }
        runCurrent()
        log.clearCompleted()
        val runs = log.runs.first()
        assertEquals(1, runs.size)
        assertEquals("Läuft", runs.single().title)
        assertEquals("keep", dataStore.data.first()[key])
        job.cancelAndJoin()
        assertEquals(SyncLogStatus.Cancelled, log.runs.first().single().status)
        log.clearCompleted()
        assertTrue(log.runs.first().isEmpty())
    }

    @Test
    fun historyAndDetailsAreBoundedWithoutLosingActiveRuns() = runTest {
        val store = TestStore()
        val log = SyncLog(store)
        val active = async { log.run("Läuft") { awaitCancellation() } }
        runCurrent()
        repeat(SyncLog.MAX_RUNS + 5) { log.step("Lauf $it") { Unit } }
        assertEquals(SyncLog.MAX_RUNS, store.runs.value.size)
        assertTrue(store.runs.value.any { it.title == "Läuft" })
        log.run("Viele Details") { repeat(SyncLog.MAX_STEPS + 3) { log.step("Detail") { Unit } } }
        assertEquals(SyncLog.MAX_STEPS, store.runs.value.last().steps.size)
        assertEquals(3, store.runs.value.last().omittedSteps)
        active.cancelAndJoin()
    }

    @Test
    fun storageFailureDoesNotStopSyncAndMalformedStoredLogCanBeReplaced() = runTest {
        var called = false
        val broken = object : SyncLogStore {
            override val runs = MutableStateFlow(emptyList<SyncLogRun>())
            override suspend fun update(transform: (List<SyncLogRun>) -> List<SyncLogRun>) { error("Disk unavailable") }
        }
        assertEquals(42, SyncLog(broken).step("Sync") { called = true; 42 })
        assertTrue(called)
        val dataStore = TestDataStore(mutablePreferencesOf(stringPreferencesKey("diagnostics:syncLogV1") to "bad json"))
        val log = SyncLog(DataStoreSyncLogStore(dataStore, backgroundScope))
        assertTrue(log.runs.first().isEmpty())
        log.step("Sync") { Unit }
        assertFalse(log.runs.first().isEmpty())
    }
}

private class TestStore : SyncLogStore {
    override val runs = MutableStateFlow(emptyList<SyncLogRun>())
    private val mutex = Mutex()
    override suspend fun update(transform: (List<SyncLogRun>) -> List<SyncLogRun>) = mutex.withLock {
        runs.value = transform(runs.value)
    }
}

private class TestDataStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    private val mutex = Mutex()
    override val data: Flow<Preferences> = state
    var writes = 0
    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = mutex.withLock {
        writes++
        transform(state.value).also { state.value = it }
    }
}

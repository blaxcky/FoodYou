package com.maksimowiczm.foodyou.training

import com.maksimowiczm.foodyou.sync.SyncLog
import com.maksimowiczm.foodyou.sync.SyncLogRun
import com.maksimowiczm.foodyou.sync.SyncLogStatus
import com.maksimowiczm.foodyou.sync.SyncLogStore
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*

internal open class MemoryTrainingStorage : TrainingSyncStorage {
    val records = mutableMapOf<Pair<String, String>, StoredTrainingSync>()
    var failProgressWrites = false
    private fun key(account: TrainingAccount) = account.project to account.uid
    override suspend fun load(account: TrainingAccount) = records[key(account)] ?: StoredTrainingSync()
    override suspend fun saveReport(account: TrainingAccount, report: TrainingSyncReport) { records[key(account)] = load(account).copy(report = report) }
    override suspend fun saveProgress(account: TrainingAccount, progress: TrainingSyncProgress) {
        if (failProgressWrites) error("disk full")
        records[key(account)] = load(account).copy(progress = progress)
    }
    override suspend fun resetAllProgress() {
        records.keys.toList().forEach { records[it] = records.getValue(it).copy(progress = TrainingSyncProgress(), report = null) }
    }
}

internal class FakeTrainingRemote : TrainingRemote {
    override val configured = true
    override val account = MutableStateFlow<TrainingAccount?>(TrainingAccount(uid = "A", email = "a@example.test"))
    var calls = 0
    val pageRequests = mutableListOf<TrainingCursor?>()
    val retryRequests = mutableListOf<List<String>>()
    var fetch: suspend () -> List<TrainingDocument> = { listOf(trainingDocument()) }
    var page: suspend (TrainingCursor?, Int) -> TrainingPage = { after, limit ->
        TrainingPage(fetch().sortedBy(TrainingCursor::of).filter { after == null || TrainingCursor.of(it) > after }.take(limit))
    }
    var retry: suspend (List<String>) -> List<TrainingDocument> = { ids -> fetch().filter { it.id in ids } }
    override suspend fun signIn(email: String, password: String) { account.value = TrainingAccount(uid = email, email = email) }
    override fun signOut() { account.value = null }
    override suspend fun fetchPage(account: TrainingAccount, after: TrainingCursor?, limit: Int): TrainingPage {
        calls++; pageRequests += after; return page(after, limit)
    }
    override suspend fun fetchByIds(account: TrainingAccount, ids: List<String>): List<TrainingDocument> {
        retryRequests += ids; return retry(ids)
    }
}

private fun session(number: Int) = trainingDocument("00000000-0000-4000-8000-" + number.toString().padStart(12, '0'))

class TrainingSyncCoordinatorTest {
    @Test fun manualButtonWaitsForInitialProgressRead() = runTest {
        val release = CompletableDeferred<Unit>()
        val storage = object : MemoryTrainingStorage() {
            var initializing = true
            override suspend fun load(account: TrainingAccount): StoredTrainingSync {
                if (initializing) {
                    initializing = false
                    release.await()
                }
                return super.load(account)
            }
        }
        val remote = FakeTrainingRemote()
        val coordinator = TrainingSyncCoordinator(remote, storage,
            { _, _, _ -> TrainingImportResult.Imported }, backgroundScope)
        runCurrent()
        coordinator.startManualSync()
        runCurrent()
        assertTrue(coordinator.state.value.busy)
        assertEquals(0, remote.calls)
        release.complete(Unit)
        runCurrent()
        assertEquals(1, remote.calls)
        assertEquals(1, coordinator.state.value.report!!.imported)
        assertFalse(coordinator.state.value.busy)
    }

    @Test fun manualButtonStartsOnceAndSurvivesScreenCancellation() = runTest {
        val release = CompletableDeferred<Unit>()
        val remote = FakeTrainingRemote().apply { fetch = { release.await(); listOf(trainingDocument()) } }
        val storage = MemoryTrainingStorage()
        var widgetUpdates = 0
        val coordinator = TrainingSyncCoordinator(remote, storage,
            { _, _, _ -> TrainingImportResult.Imported }, backgroundScope,
            onImported = { widgetUpdates++ })
        runCurrent()
        assertEquals(0, remote.calls)
        val screen = launch {
            coordinator.startManualSync()
            coordinator.startManualSync()
            awaitCancellation()
        }
        runCurrent()
        assertEquals(1, remote.calls)
        assertTrue(coordinator.state.value.busy)
        screen.cancelAndJoin()
        coordinator.startManualSync()
        runCurrent()
        assertEquals(1, remote.calls)
        release.complete(Unit)
        runCurrent()
        assertFalse(coordinator.state.value.busy)
        val report = coordinator.state.value.report!!
        assertEquals(1, report.imported)
        assertEquals(listOf(ImportedTrainingSession(TEST_SESSION, "2026-09-27", 210, 120)), report.importedSessions)
        assertEquals(report, storage.load(remote.account.value!!).report)
        assertEquals(1, widgetUpdates)
    }

    @Test fun partialImportsAndSuccessfulRetriesIncludeDetailsAndRefreshWidgets() = runTest {
        val documents = listOf(session(1), session(2), session(3), session(4))
        val remote = FakeTrainingRemote().apply { fetch = { documents } }
        val storage = MemoryTrainingStorage()
        var failSecond = true
        var widgetUpdates = 0
        val coordinator = TrainingSyncCoordinator(remote, storage, { _, d, _ ->
            when (d.id) {
                session(2).id -> if (failSecond) error("write failed") else TrainingImportResult.Imported
                session(3).id -> TrainingImportResult.AlreadyImported
                session(4).id -> TrainingImportResult.ZeroCalories
                else -> TrainingImportResult.Imported
            }
        }, backgroundScope, onImported = { widgetUpdates++ })
        runCurrent()
        val partial = coordinator.sync()!!
        assertEquals(1, partial.imported)
        assertEquals(1, partial.failed)
        assertEquals(1, partial.pending)
        assertEquals(1, partial.existing)
        assertEquals(1, partial.zeroCalories)
        assertEquals(listOf(session(1).id), partial.importedSessions.map { it.sessionId })
        assertEquals(1, widgetUpdates)
        failSecond = false
        val retry = coordinator.sync()!!
        assertTrue(retry.successful)
        assertEquals(listOf(session(2).id), retry.importedSessions.map { it.sessionId })
        assertEquals(2, widgetUpdates)
        assertTrue(coordinator.sync()!!.importedSessions.isEmpty())
        assertEquals(2, widgetUpdates)
    }

    @Test fun manualRunIsStoppedAndJoinedOnSignOutOrRestore() = runTest {
        for (restore in listOf(false, true)) {
            var stopped = false
            val remote = FakeTrainingRemote().apply {
                fetch = { try { awaitCancellation() } finally { stopped = true } }
            }
            val storage = MemoryTrainingStorage()
            val coordinator = TrainingSyncCoordinator(remote, storage,
                { _, _, _ -> error("No response yet") }, backgroundScope)
            runCurrent()
            coordinator.startManualSync()
            assertTrue(coordinator.state.value.busy)
            runCurrent()
            assertEquals(1, remote.calls)
            if (restore) coordinator.withPausedSyncForRestore { assertTrue(stopped) }
            else coordinator.signOut()
            runCurrent()
            assertTrue(stopped)
            assertFalse(coordinator.state.value.busy)
            assertNull(coordinator.account.value)
            assertNull(coordinator.state.value.report)
            coordinator.startManualSync()
            runCurrent()
            assertEquals(1, remote.calls)
        }
    }

    @Test fun manualRunIsCancelledOnAccountChangeAndNewAccountCanStart() = runTest {
        val remote = FakeTrainingRemote().apply { fetch = { awaitCancellation() } }
        val writes = mutableListOf<String>()
        val coordinator = TrainingSyncCoordinator(remote, MemoryTrainingStorage(), { a, _, _ ->
            writes += a.uid
            TrainingImportResult.Imported
        }, backgroundScope)
        runCurrent()
        coordinator.startManualSync()
        runCurrent()
        remote.account.value = TrainingAccount(uid = "B", email = null)
        runCurrent()
        assertFalse(coordinator.state.value.busy)
        assertNull(coordinator.state.value.report)
        assertTrue(writes.isEmpty())
        remote.fetch = { listOf(trainingDocument()) }
        coordinator.startManualSync()
        runCurrent()
        assertEquals(listOf("B"), writes)
        assertEquals(1, coordinator.state.value.report!!.imported)
    }

    @Test fun syncLogReportsPartialImportFailureAndSignedOutSkip() = runTest {
        val store = object : SyncLogStore {
            override val runs = MutableStateFlow(emptyList<SyncLogRun>())
            override suspend fun update(transform: (List<SyncLogRun>) -> List<SyncLogRun>) {
                runs.value = transform(runs.value)
            }
        }
        val remote = FakeTrainingRemote().apply { fetch = { listOf(trainingDocument("bad"), trainingDocument()) } }
        val log = SyncLog(store)
        val coordinator = TrainingSyncCoordinator(remote, MemoryTrainingStorage(), { _, document, _ ->
            validateTrainingDocument(document)
            TrainingImportResult.Imported
        }, backgroundScope, syncLog = log)
        runCurrent()
        assertEquals(1, coordinator.sync()!!.failed)
        val run = store.runs.value.single()
        assertEquals(SyncLogStatus.Failed, run.status)
        assertEquals(listOf("Trainings synchronisieren", "Trainings vom Server laden", "Trainings lokal übernehmen"),
            run.steps.map { it.title })
        assertEquals(SyncLogStatus.Failed, run.steps.last().status)
        coordinator.signOut()
        assertNull(coordinator.sync())
        assertEquals(SyncLogStatus.Skipped, store.runs.value.last().steps.single().status)
    }

    @Test fun onlyManualSyncFetchesAndReportsCommittedResults() = runTest {
        val remote = FakeTrainingRemote()
        val coordinator = TrainingSyncCoordinator(remote, MemoryTrainingStorage(), { _, _, _ -> TrainingImportResult.Imported }, backgroundScope)
        runCurrent()
        assertEquals(0, remote.calls)
        val report = coordinator.sync()!!
        assertEquals(1, report.imported)
        assertTrue(report.successful)
        coordinator.signOut()
        assertNull(coordinator.sync())
        assertEquals(1, remote.calls)
    }

    @Test fun switchingAccountsCancelsLateResultsButNotCallerAndHidesOldStatus() = runTest {
        val remote = FakeTrainingRemote()
        remote.fetch = { awaitCancellation() }
        val writes = mutableListOf<String>()
        val coordinator = TrainingSyncCoordinator(remote, MemoryTrainingStorage(), { a, _, _ -> writes += a.uid; TrainingImportResult.Imported }, backgroundScope)
        runCurrent()
        val result = async { coordinator.sync() }
        runCurrent()
        remote.account.value = TrainingAccount(uid = "B", email = null)
        runCurrent()
        assertNull(result.await())
        assertTrue(writes.isEmpty())
        assertEquals("B", coordinator.state.value.account?.uid)
        assertNull(coordinator.state.value.report)
        assertFalse(coordinator.state.value.busy)
        remote.fetch = { emptyList() }
        assertTrue(coordinator.sync()!!.successful)
    }

    @Test fun simultaneousRunsDoNotDuplicateFetchAndCallerCancellationPropagates() = runTest {
        val remote = FakeTrainingRemote().apply { fetch = { awaitCancellation() } }
        val coordinator = TrainingSyncCoordinator(remote, MemoryTrainingStorage(), { _, _, _ -> error("No data yet") }, backgroundScope)
        runCurrent()
        val running = launch { coordinator.sync() }
        runCurrent()
        assertNull(coordinator.sync())
        assertEquals(1, remote.calls)
        running.cancelAndJoin()
        assertFalse(coordinator.state.value.busy)
    }

    @Test fun serverTimeoutReportsFailureAndAllowsRetry() = runTest {
        val remote = FakeTrainingRemote().apply { fetch = { awaitCancellation() } }
        val coordinator = TrainingSyncCoordinator(remote, MemoryTrainingStorage(), { _, _, _ -> TrainingImportResult.Imported }, backgroundScope)
        runCurrent()
        val request = async { coordinator.sync() }
        advanceTimeBy(60_001)
        assertEquals(1, request.await()!!.failed)
        assertFalse(coordinator.state.value.busy)
        remote.fetch = { listOf(trainingDocument()) }
        assertEquals(1, coordinator.sync()!!.imported)
    }

    @Test fun errorsAreVisibleAndValidDocumentsStillCommit() = runTest {
        val remote = FakeTrainingRemote().apply { fetch = { listOf(trainingDocument("bad"), trainingDocument()) } }
        val coordinator = TrainingSyncCoordinator(remote, MemoryTrainingStorage(), { _, d, _ -> validateTrainingDocument(d); TrainingImportResult.Imported }, backgroundScope)
        runCurrent()
        val report = coordinator.sync()!!
        assertEquals(1, report.imported)
        assertEquals(1, report.failed)
        remote.fetch = { error("secret transport exception") }
        val failed = coordinator.sync()!!
        assertEquals(1, failed.failed)
        assertFalse(failed.description().contains("secret"))
    }
    @Test fun fullHistoryIsPagedAndNextRunOnlyReadsNewDocuments() = runTest {
        val documents = (1..401).map(::session).toMutableList()
        val remote = FakeTrainingRemote().apply { fetch = { documents } }
        val storage = MemoryTrainingStorage()
        val receipts = mutableSetOf<String>()
        val coordinator = TrainingSyncCoordinator(remote, storage, { _, d, _ ->
            if (receipts.add(d.id)) TrainingImportResult.Imported else TrainingImportResult.AlreadyImported
        }, backgroundScope)
        runCurrent()
        val firstReport = coordinator.sync()!!
        assertEquals(401, firstReport.imported)
        assertEquals(documents.map { it.id }, firstReport.importedSessions.map { it.sessionId })
        assertEquals(3, remote.calls)
        val saved = storage.load(remote.account.value!!).progress
        assertEquals(TrainingCursor.of(documents.last()), saved.cursor)
        assertTrue(saved.initialImportComplete)
        val emptyReport = coordinator.sync()!!
        assertEquals(0, emptyReport.existing)
        assertTrue(emptyReport.importedSessions.isEmpty())
        assertEquals(4, remote.calls)
        assertEquals(saved.cursor, remote.pageRequests.last())
        // A late upload has an old booking date and a smaller ID, but a newer server timestamp.
        documents += session(0).copy(receivedAt = kotlin.time.Instant.parse("2026-10-02T12:00:00.000000001Z"))
        assertEquals(1, coordinator.sync()!!.imported)
        assertEquals(402, receipts.size)
        assertEquals(1, storage.load(remote.account.value!!).progress.cursor!!.nanoseconds)
    }

    @Test fun fullPageNeedsAnEmptyPageAndEmptyHistoryNeedsOnlyOneRead() = runTest {
        val remote = FakeTrainingRemote().apply { fetch = { (1..200).map(::session) } }
        val coordinator = TrainingSyncCoordinator(remote, MemoryTrainingStorage(), { _, _, _ -> TrainingImportResult.ZeroCalories }, backgroundScope)
        runCurrent()
        assertEquals(200, coordinator.sync()!!.zeroCalories)
        assertEquals(2, remote.calls)
        val empty = FakeTrainingRemote().apply { fetch = { emptyList() } }
        val storage = MemoryTrainingStorage()
        val emptyCoordinator = TrainingSyncCoordinator(empty, storage, { _, _, _ -> error("empty") }, backgroundScope)
        runCurrent()
        assertTrue(emptyCoordinator.sync()!!.successful)
        assertEquals(1, empty.calls)
        assertNull(storage.load(empty.account.value!!).progress.cursor)
        assertTrue(storage.load(empty.account.value!!).progress.initialImportComplete)
    }

    @Test fun initialImportHasNoOverallTimeoutAndResumesAfterPageFailure() = runTest {
        val documents = (1..401).map(::session)
        val remote = FakeTrainingRemote().apply { fetch = { delay(30_000); documents } }
        val storage = MemoryTrainingStorage()
        val coordinator = TrainingSyncCoordinator(remote, storage, { _, _, _ -> TrainingImportResult.Imported }, backgroundScope)
        runCurrent()
        val firstReport = coordinator.sync()!!
        assertEquals(401, firstReport.imported)
        assertEquals(documents.map { it.id }, firstReport.importedSessions.map { it.sessionId })
        assertEquals(90_000, testScheduler.currentTime)
        val second = FakeTrainingRemote().apply {
            page = { after, limit ->
                if (after != null) error("offline")
                TrainingPage(documents.take(limit))
            }
        }
        val secondStorage = MemoryTrainingStorage()
        val secondCoordinator = TrainingSyncCoordinator(second, secondStorage, { _, _, _ -> TrainingImportResult.Imported }, backgroundScope)
        runCurrent()
        assertEquals(200, secondCoordinator.sync()!!.imported)
        assertFalse(secondStorage.load(second.account.value!!).progress.initialImportComplete)
        second.page = { after, limit -> TrainingPage(documents.filter { after == null || TrainingCursor.of(it) > after }.take(limit)) }
        assertEquals(201, secondCoordinator.sync()!!.imported)
    }

    @Test fun oldFailuresAreBoundedAndRotatedAfterNewDocuments() = runTest {
        val documents = (1..25).map(::session).toMutableList()
        val remote = FakeTrainingRemote().apply { fetch = { documents } }
        val storage = MemoryTrainingStorage()
        val order = mutableListOf<String>()
        var failing = true
        val coordinator = TrainingSyncCoordinator(remote, storage, { _, d, _ ->
            order += d.id
            if (failing && d.id != session(26).id) error("temporary local error")
            TrainingImportResult.Imported
        }, backgroundScope)
        runCurrent()
        assertEquals(25, coordinator.sync()!!.pending)
        assertTrue(remote.retryRequests.isEmpty()) // New failures wait until the next manual run.
        documents += session(26)
        order.clear()
        val second = coordinator.sync()!!
        assertEquals(session(26).id, order.first())
        assertEquals(1, second.imported)
        assertEquals(20, remote.retryRequests.flatten().size)
        assertTrue(remote.retryRequests.all { it.size <= 10 })
        assertEquals((21..25).map { session(it).id }, storage.load(remote.account.value!!).progress.pendingIds.take(5))
        failing = false
        remote.retryRequests.clear()
        assertEquals(5, coordinator.sync()!!.pending)
        assertEquals(session(21).id, remote.retryRequests.first().first())
        assertEquals(0, coordinator.sync()!!.pending)
    }

    @Test fun retryTimeoutRotatesIdsAndMissingDocumentsRemainPending() = runTest {
        val remote = FakeTrainingRemote().apply { fetch = { emptyList() }; retry = { awaitCancellation() } }
        val storage = MemoryTrainingStorage()
        val owner = remote.account.value!!
        val ids = (1..15).map { session(it).id }
        storage.saveProgress(owner, TrainingSyncProgress(initialImportComplete = true, pendingIds = ids))
        val coordinator = TrainingSyncCoordinator(remote, storage, { _, _, _ -> TrainingImportResult.Imported }, backgroundScope)
        runCurrent()
        val report = coordinator.sync()!!
        assertEquals(10_000, testScheduler.currentTime)
        assertEquals(15, report.pending)
        assertFalse(report.successful)
        assertEquals(ids.drop(10) + ids.take(10), storage.load(owner).progress.pendingIds)
        remote.retry = { emptyList() }
        assertEquals(15, coordinator.sync()!!.pending)
    }

    @Test fun failedCheckpointReplaysSafelyAndReportsStorageFailure() = runTest {
        val remote = FakeTrainingRemote()
        val storage = MemoryTrainingStorage().apply { failProgressWrites = true }
        val receipts = mutableSetOf<String>()
        val coordinator = TrainingSyncCoordinator(remote, storage, { _, d, _ ->
            if (receipts.add(d.id)) TrainingImportResult.Imported else TrainingImportResult.AlreadyImported
        }, backgroundScope)
        runCurrent()
        val failed = coordinator.sync()!!
        assertEquals(1, failed.imported)
        assertTrue(failed.errors.any { "fortschritt" in it })
        assertNull(storage.load(remote.account.value!!).progress.cursor)
        storage.failProgressWrites = false
        assertEquals(1, coordinator.sync()!!.existing)
        assertEquals(1, receipts.size)
    }

    @Test fun cancellingMidPageLeavesCheckpointAndReplaysCommittedImportsAfterRestart() = runTest {
        val documents = (1..2).map(::session)
        val remote = FakeTrainingRemote().apply { fetch = { documents } }
        val storage = MemoryTrainingStorage()
        val receipts = mutableSetOf<String>()
        var widgetUpdates = 0
        val coordinator = TrainingSyncCoordinator(remote, storage, { _, d, _ ->
            if (d.id == documents.last().id) awaitCancellation()
            receipts += d.id
            TrainingImportResult.Imported
        }, backgroundScope, onImported = { widgetUpdates++ })
        runCurrent()
        val running = launch { coordinator.sync() }
        runCurrent()
        running.cancelAndJoin()
        assertEquals(1, receipts.size)
        assertEquals(1, widgetUpdates)
        assertNull(storage.load(remote.account.value!!).progress.cursor)
        val restarted = TrainingSyncCoordinator(remote, storage, { _, d, _ ->
            if (receipts.add(d.id)) TrainingImportResult.Imported else TrainingImportResult.AlreadyImported
        }, backgroundScope)
        runCurrent()
        val result = restarted.sync()!!
        assertEquals(1, result.imported)
        assertEquals(1, result.existing)
    }

    @Test fun invalidCursorOrServerTimestampNeverAdvancesProgress() = runTest {
        for (documents in listOf(listOf(session(1).copy(receivedAt = null)), listOf(session(2), session(1)))) {
            val remote = FakeTrainingRemote().apply { page = { _, _ -> TrainingPage(documents) } }
            val storage = MemoryTrainingStorage()
            val coordinator = TrainingSyncCoordinator(remote, storage, { _, _, _ -> error("must not import") }, backgroundScope)
            runCurrent()
            assertEquals(1, coordinator.sync()!!.failed)
            assertNull(storage.load(remote.account.value!!).progress.cursor)
        }
    }

    @Test fun restoreJoinsImportsBlocksNewRunsAndResetsAllAccountsEvenOnRollback() = runTest {
        val remote = FakeTrainingRemote()
        val storage = MemoryTrainingStorage()
        val owner = remote.account.value!!
        val other = TrainingAccount(project = "other-project", uid = owner.uid, email = null)
        storage.saveProgress(other, TrainingSyncProgress(TrainingCursor.of(session(5)), true, listOf(session(3).id)))
        var stopped = false
        val coordinator = TrainingSyncCoordinator(remote, storage, { _, _, _ ->
            try { awaitCancellation() } finally { stopped = true }
        }, backgroundScope)
        runCurrent()
        val running = async { coordinator.sync() }
        runCurrent()
        assertFailsWith<IllegalStateException> {
            coordinator.withPausedSyncForRestore {
                assertTrue(stopped)
                assertNull(coordinator.sync())
                coordinator.signIn("B", "password")
                assertNull(remote.account.value)
                assertEquals(TrainingSyncProgress(), storage.load(other).progress)
                error("restore rollback")
            }
        }
        assertNull(running.await())
        assertFalse(coordinator.state.value.busy)
        coordinator.signIn("A", "password")
        runCurrent()
        remote.fetch = { emptyList() }
        assertTrue(coordinator.sync()!!.successful)
    }

    @Test fun unchangedEmptyPageDoesNotRewriteConfirmedProgress() = runTest {
        val remote = FakeTrainingRemote().apply { fetch = { emptyList() } }
        val storage = MemoryTrainingStorage()
        val coordinator = TrainingSyncCoordinator(remote, storage, { _, _, _ -> error("empty") }, backgroundScope)
        runCurrent()
        assertTrue(coordinator.sync()!!.successful)
        storage.failProgressWrites = true
        assertTrue(coordinator.sync()!!.successful)
        assertEquals(2, remote.calls)
    }

    @Test fun signingOutCancelsImportWithoutConfirmingCursor() = runTest {
        val remote = FakeTrainingRemote()
        val storage = MemoryTrainingStorage()
        val owner = remote.account.value!!
        val coordinator = TrainingSyncCoordinator(remote, storage, { _, _, _ -> awaitCancellation() }, backgroundScope)
        runCurrent()
        val running = async { coordinator.sync() }
        runCurrent()
        coordinator.signOut()
        runCurrent()
        assertNull(running.await())
        assertNull(storage.load(owner).progress.cursor)
        assertNull(coordinator.account.value)
        assertNull(coordinator.sync())
    }

    @Test fun badStoredCursorAndFailedRestoreResetNeverReachServerOrDatabaseReplacement() = runTest {
        val remote = FakeTrainingRemote()
        val storage = MemoryTrainingStorage()
        val owner = remote.account.value!!
        storage.saveProgress(owner, TrainingSyncProgress(TrainingCursor(123, -1, "id")))
        val coordinator = TrainingSyncCoordinator(remote, storage, { _, _, _ -> error("invalid cursor") }, backgroundScope)
        runCurrent()
        assertEquals(1, coordinator.sync()!!.failed)
        assertEquals(0, remote.calls)
        val broken = object : MemoryTrainingStorage() {
            override suspend fun resetAllProgress() { error("disk failure") }
        }
        val restoreCoordinator = TrainingSyncCoordinator(remote, broken, { _, _, _ -> error("unused") }, backgroundScope)
        runCurrent()
        var replaced = false
        assertFailsWith<IllegalStateException> {
            restoreCoordinator.withPausedSyncForRestore { replaced = true }
        }
        assertFalse(replaced)
        assertFalse(restoreCoordinator.state.value.busy)
    }

}

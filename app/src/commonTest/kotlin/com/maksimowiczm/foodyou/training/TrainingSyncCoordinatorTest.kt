package com.maksimowiczm.foodyou.training

import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*

internal class MemoryTrainingStorage : TrainingSyncStorage {
    private val enabled = mutableMapOf<String, Boolean>()
    private val reports = mutableMapOf<String, TrainingSyncReport>()
    override fun enabled(account: TrainingAccount) = enabled[account.uid] ?: true
    override fun setEnabled(account: TrainingAccount, enabled: Boolean) { this.enabled[account.uid] = enabled }
    override fun report(account: TrainingAccount) = reports[account.uid]
    override fun saveReport(account: TrainingAccount, report: TrainingSyncReport) { reports[account.uid] = report }
}

internal class FakeTrainingRemote : TrainingRemote {
    override val configured = true
    override val account = MutableStateFlow<TrainingAccount?>(TrainingAccount(uid = "A", email = "a@example.test"))
    var calls = 0
    var fetch: suspend () -> List<TrainingDocument> = { listOf(trainingDocument()) }
    override suspend fun signIn(email: String, password: String) { account.value = TrainingAccount(uid = email, email = email) }
    override fun signOut() { account.value = null }
    override suspend fun fetch(account: TrainingAccount): List<TrainingDocument> { calls++; return fetch() }
}

class TrainingSyncCoordinatorTest {
    @Test fun onlyManualSyncFetchesAndReportsCommittedResults() = runTest {
        val remote = FakeTrainingRemote()
        val coordinator = TrainingSyncCoordinator(remote, MemoryTrainingStorage(), { _, _, _ -> TrainingImportResult.Imported }, backgroundScope)
        runCurrent()
        assertEquals(0, remote.calls)
        val report = coordinator.sync()!!
        assertEquals(1, report.imported)
        assertTrue(report.successful)
        coordinator.setEnabled(false)
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
}

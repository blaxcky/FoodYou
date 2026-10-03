package com.maksimowiczm.foodyou.food.infrastructure.fddb

import com.maksimowiczm.foodyou.food.domain.repository.FddbRequestPriority
import com.maksimowiczm.foodyou.sync.SyncLog
import com.maksimowiczm.foodyou.sync.SyncLogRun
import com.maksimowiczm.foodyou.sync.SyncLogStore
import kotlin.time.TestTimeSource
import kotlin.time.Duration.Companion.milliseconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.MutableStateFlow

class FddbRequestQueueTest {
    @Test
    fun syncLogSeparatesQueueWaitFromRequestTimeAndIgnoresOrdinarySearches() = runTest {
        val store = object : SyncLogStore {
            override val runs = MutableStateFlow(emptyList<SyncLogRun>())
            override suspend fun update(transform: (List<SyncLogRun>) -> List<SyncLogRun>) {
                runs.value = transform(runs.value)
            }
        }
        val time = TestTimeSource()
        val log = SyncLog(store, time)
        val queue = FddbRequestQueue(log)
        val release = CompletableDeferred<Unit>()
        launch { queue.execute { release.await() } }
        runCurrent()
        assertEquals(emptyList(), store.runs.value)
        launch { log.run("Sync") { queue.execute { time += 40.milliseconds; 42 } } }
        runCurrent()
        time += 500.milliseconds
        release.complete(Unit)
        runCurrent()
        val run = store.runs.value.single()
        assertEquals(listOf("FDDB: Auf freie Anfrage warten", "FDDB: Anfrage ausführen"), run.steps.map { it.title })
        assertEquals(listOf(500L, 40L), run.steps.map { it.durationMillis })
        assertEquals(540L, run.durationMillis)
    }

    @Test
    fun serializesRequestsAndPrioritizesDiaryInFifoOrder() = runTest {
        val queue = FddbRequestQueue()
        val release = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()
        launch { queue.execute { order.add("active"); release.await() } }
        runCurrent()
        launch { queue.execute { order.add("product1") } }
        launch { queue.execute(FddbRequestPriority.Diary) { order.add("diary1") } }
        launch { queue.execute { order.add("product2") } }
        launch { queue.execute(FddbRequestPriority.Diary) { order.add("diary2") } }
        runCurrent()
        assertEquals(listOf("active"), order)
        release.complete(Unit)
        runCurrent()
        assertEquals(listOf("active", "diary1", "diary2", "product1", "product2"), order)
    }

    @Test
    fun cancelledWaiterIsRemovedAndCancelledRequestReleasesPermit() = runTest {
        val queue = FddbRequestQueue()
        val order = mutableListOf<String>()
        val active = launch { queue.execute { awaitCancellation() } }
        runCurrent()
        val waiting = launch {
            queue.execute(FddbRequestPriority.Diary) { error("Cancelled waiter ran") }
        }
        launch { queue.execute { order.add("next") } }
        runCurrent()
        waiting.cancelAndJoin()
        active.cancelAndJoin()
        runCurrent()
        assertEquals(listOf("next"), order)
    }

    @Test
    fun failedRequestReleasesPermit() = runTest {
        val queue = FddbRequestQueue()
        assertFailsWith<IllegalStateException> { queue.execute { error("Network failed") } }
        assertEquals("recovered", queue.execute { "recovered" })
    }

    @Test
    fun cancellationAfterHandoffDoesNotLeakPermit() = runTest {
        val queue = FddbRequestQueue()
        val release = CompletableDeferred<Unit>()
        launch { queue.execute { release.await() } }
        runCurrent()
        val next = launch { queue.execute { error("Cancelled request ran") } }
        runCurrent()
        // Both resumption and cancellation are queued before the next request executes.
        release.complete(Unit)
        next.cancel()
        runCurrent()
        assertEquals(42, queue.execute { 42 })
    }
}

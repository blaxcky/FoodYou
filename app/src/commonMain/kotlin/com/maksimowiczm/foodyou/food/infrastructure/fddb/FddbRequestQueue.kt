package com.maksimowiczm.foodyou.food.infrastructure.fddb

import com.maksimowiczm.foodyou.food.domain.repository.FddbRequestPriority
import com.maksimowiczm.foodyou.sync.SyncLog
import com.maksimowiczm.foodyou.sync.recordActiveSyncStep
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One app-wide permit, held until the complete HTTP response has been consumed. */
internal class FddbRequestQueue(private val syncLog: SyncLog? = null) {
    private class Ticket(val priority: FddbRequestPriority) {
        val ready = CompletableDeferred<Unit>()
    }

    private val mutex = Mutex()
    private val waiting = mutableListOf<Ticket>()
    private var active: Ticket? = null

    suspend fun <T> execute(
        priority: FddbRequestPriority = FddbRequestPriority.Normal,
        request: suspend () -> T,
    ): T {
        val ticket = Ticket(priority)
        try {
            mutex.withLock {
                waiting.add(ticket)
                dispatch()
            }
            syncLog.recordActiveSyncStep("FDDB: Auf freie Anfrage warten") { ticket.ready.await() }
            return syncLog.recordActiveSyncStep("FDDB: Anfrage ausführen", request)
        } finally {
            withContext(NonCancellable) {
                mutex.withLock {
                    waiting.remove(ticket)
                    if (active === ticket) active = null
                    dispatch()
                }
            }
        }
    }

    private fun dispatch() {
        if (active != null) return
        val next = waiting.firstOrNull { it.priority == FddbRequestPriority.Diary }
            ?: waiting.firstOrNull() ?: return
        waiting.remove(next)
        active = next
        next.ready.complete(Unit)
    }
}

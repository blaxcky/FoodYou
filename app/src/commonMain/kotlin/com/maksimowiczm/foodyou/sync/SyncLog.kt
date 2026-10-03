package com.maksimowiczm.foodyou.sync

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
enum class SyncLogStatus { Running, Success, Skipped, Failed, Cancelled, Interrupted }

@Serializable
data class SyncLogStep(
    val number: Int,
    val parentNumber: Int?,
    val title: String,
    val startedAtMillis: Long,
    val offsetMillis: Long,
    val durationMillis: Long? = null,
    val status: SyncLogStatus = SyncLogStatus.Running,
    val detail: String = "",
)

@Serializable
data class SyncLogRun(
    val id: Long,
    val title: String,
    val startedAtMillis: Long,
    val durationMillis: Long? = null,
    val status: SyncLogStatus = SyncLogStatus.Running,
    val steps: List<SyncLogStep> = emptyList(),
    val omittedSteps: Int = 0,
)

data class SyncLogOutcome(val status: SyncLogStatus, val detail: String = "") {
    companion object {
        fun success(detail: String = "") = SyncLogOutcome(SyncLogStatus.Success, detail)
        fun skipped(detail: String) = SyncLogOutcome(SyncLogStatus.Skipped, detail)
        fun failed(detail: String) = SyncLogOutcome(SyncLogStatus.Failed, detail)
    }
}

interface SyncLogStore {
    val runs: Flow<List<SyncLogRun>>
    suspend fun update(transform: (List<SyncLogRun>) -> List<SyncLogRun>)
}

/** Bounded local diagnostics. Coroutine context preserves ownership across parallel sync branches. */
class SyncLog(private val store: SyncLogStore, private val timeSource: TimeSource = TimeSource.Monotonic) {
    private val initializationMutex = Mutex()
    private var initialized = false

    val runs: Flow<List<SyncLogRun>> = flow {
        initialize()
        emitAll(store.runs)
    }

    private suspend fun initialize() = initializationMutex.withLock {
        if (!initialized) {
            updateSafely { runs ->
                runs.map { run ->
                    if (run.status != SyncLogStatus.Running) run
                    else run.copy(
                        status = SyncLogStatus.Interrupted,
                        steps = run.steps.map { step ->
                            if (step.status == SyncLogStatus.Running) step.copy(status = SyncLogStatus.Interrupted)
                            else step
                        },
                    )
                }
            }
            initialized = true
        }
    }

    suspend fun clearCompleted() {
        initialize()
        updateSafely { runs -> runs.filter { it.status == SyncLogStatus.Running } }
    }

    suspend fun <T> run(title: String, block: suspend () -> T): T {
        initialize()
        val mark = timeSource.markNow()
        val startedAt = Clock.System.now().toEpochMilliseconds()
        var id: Long? = null
        var status = SyncLogStatus.Success
        try {
            updateSafely { runs ->
                val nextId = (runs.maxOfOrNull { it.id } ?: 0) + 1
                id = nextId
                val updated = runs + SyncLogRun(nextId, title.take(MAX_TEXT_LENGTH), startedAt)
                val active = updated.filter { it.status == SyncLogStatus.Running }
                val retained = updated.filter { it.status != SyncLogStatus.Running }
                    .takeLast((MAX_RUNS - active.size).coerceAtLeast(0))
                (retained + active).sortedBy { it.id }
            }
            return withContext(SyncLogContext(this, id, mark, null)) { block() }
        } catch (cancel: CancellationException) {
            status = if (cancel is TimeoutCancellationException) SyncLogStatus.Failed else SyncLogStatus.Cancelled
            throw cancel
        } catch (error: Exception) {
            status = SyncLogStatus.Failed
            throw error
        } finally {
            val elapsed = mark.elapsedNow().inWholeMilliseconds
            withContext(NonCancellable) {
                updateSafely { runs -> runs.map { run ->
                    if (run.id != id) run else run.copy(
                        durationMillis = elapsed,
                        status = if (status == SyncLogStatus.Success && run.steps.any { it.status == SyncLogStatus.Failed })
                            SyncLogStatus.Failed else status,
                    )
                } }
            }
        }
    }

    suspend fun <T> step(
        title: String,
        outcome: (T) -> SyncLogOutcome = { SyncLogOutcome.success() },
        block: suspend () -> T,
    ): T {
        val context = currentCoroutineContext()[SyncLogContext]?.takeIf { it.log === this }
            ?: return run(title) { step(title, outcome, block) }
        val mark = timeSource.markNow()
        val startedAt = Clock.System.now().toEpochMilliseconds()
        val offset = context.mark.elapsedNow().inWholeMilliseconds
        var number: Int? = null
        var result = SyncLogOutcome.success()
        try {
            updateSafely { runs -> runs.map { run ->
                if (run.id != context.runId) run
                else if (run.steps.size >= MAX_STEPS) run.copy(omittedSteps = run.omittedSteps + 1)
                else {
                    val nextNumber = run.steps.size + 1
                    number = nextNumber
                    run.copy(steps = run.steps + SyncLogStep(nextNumber, context.parentNumber,
                        title.take(MAX_TEXT_LENGTH), startedAt, offset))
                }
            } }
            return withContext(SyncLogContext(this, context.runId, context.mark, number ?: context.parentNumber)) {
                block().also { result = outcome(it) }
            }
        } catch (cancel: CancellationException) {
            result = if (cancel is TimeoutCancellationException) SyncLogOutcome.failed("Zeitlimit überschritten")
                else SyncLogOutcome(SyncLogStatus.Cancelled, "Abgebrochen")
            throw cancel
        } catch (error: Exception) {
            // Raw exception messages can contain account identifiers or URLs with credentials.
            result = SyncLogOutcome.failed("Vorgang fehlgeschlagen (${error::class.simpleName ?: "Unbekannt"})")
            throw error
        } finally {
            val elapsed = mark.elapsedNow().inWholeMilliseconds
            if (number != null) withContext(NonCancellable) {
                updateSafely { runs -> runs.map { run ->
                    if (run.id != context.runId) run else run.copy(steps = run.steps.map { step ->
                        if (step.number != number) step else step.copy(durationMillis = elapsed,
                            status = if (result.status == SyncLogStatus.Success && run.steps.any {
                                it.parentNumber == number && it.status == SyncLogStatus.Failed
                            }) SyncLogStatus.Failed else result.status,
                            detail = result.detail.take(MAX_TEXT_LENGTH))
                    })
                } }
            }
        }
    }

    private suspend fun updateSafely(transform: (List<SyncLogRun>) -> List<SyncLogRun>) {
        try { store.update(transform) }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { /* Diagnostics must never prevent the actual sync. */ }
    }

    companion object {
        const val MAX_RUNS = 30
        const val MAX_STEPS = 200
        private const val MAX_TEXT_LENGTH = 500
    }
}

private class SyncLogContext(
    val log: SyncLog,
    val runId: Long?,
    val mark: TimeMark,
    val parentNumber: Int?,
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<SyncLogContext>
}

suspend fun <T> SyncLog?.recordSyncRun(title: String, block: suspend () -> T): T =
    if (this == null) block() else run(title, block)

suspend fun <T> SyncLog?.recordSyncStep(
    title: String,
    outcome: (T) -> SyncLogOutcome = { SyncLogOutcome.success() },
    block: suspend () -> T,
): T = if (this == null) block() else step(title, outcome, block)

suspend fun SyncLog?.recordSyncSkipped(title: String, reason: String) {
    recordSyncStep(title, { SyncLogOutcome.skipped(reason) }) { Unit }
}

/** Add request details only when called from a sync, rather than ordinary product searches. */
suspend fun <T> SyncLog?.recordActiveSyncStep(title: String, block: suspend () -> T): T =
    if (this != null && currentCoroutineContext()[SyncLogContext]?.log === this) step(title, block = block)
    else block()

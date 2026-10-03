package com.maksimowiczm.foodyou.training

import kotlin.time.Clock
import com.maksimowiczm.foodyou.sync.SyncLog
import com.maksimowiczm.foodyou.sync.SyncLogOutcome
import com.maksimowiczm.foodyou.sync.SyncLogStatus
import com.maksimowiczm.foodyou.sync.recordSyncStep
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

@Serializable
data class TrainingCursor(val seconds: Long, val nanoseconds: Int, val documentId: String) : Comparable<TrainingCursor> {
    fun validate() {
        require(nanoseconds in 0..999_999_999 && documentId.isNotBlank() && '/' !in documentId) { "Ungültiger Trainingscursor" }
    }
    override fun compareTo(other: TrainingCursor): Int = compareValuesBy(this, other,
        { it.seconds }, { it.nanoseconds }, { it.documentId })
    companion object {
        fun of(document: TrainingDocument): TrainingCursor {
            val receivedAt = requireNotNull(document.receivedAt) { "Server-Empfangszeit fehlt" }
            return TrainingCursor(receivedAt.epochSeconds, receivedAt.nanosecondsOfSecond, document.id).also { it.validate() }
        }
    }
}

@Serializable
data class TrainingSyncProgress(
    val cursor: TrainingCursor? = null,
    val initialImportComplete: Boolean = false,
    val pendingIds: List<String> = emptyList(),
)

@Serializable
data class StoredTrainingSync(
    val enabled: Boolean = true,
    val report: TrainingSyncReport? = null,
    val progress: TrainingSyncProgress = TrainingSyncProgress(),
)

data class TrainingPage(val documents: List<TrainingDocument>)

@Serializable
data class TrainingSyncReport(
    val finishedAtMillis: Long,
    val imported: Int = 0,
    val existing: Int = 0,
    val zeroCalories: Int = 0,
    val failed: Int = 0,
    val errors: List<String> = emptyList(),
    val pending: Int = 0,
) {
    val successful: Boolean get() = failed == 0 && pending == 0
    fun description(): String = "$imported Trainings übernommen · $existing bereits vorhanden · $zeroCalories ohne Kalorien · $failed Fehler" +
        (if (pending == 0) "" else " · $pending weiterhin offen") +
        (if (errors.isEmpty()) "" else "\n" + errors.joinToString("\n"))
}

data class TrainingSyncState(
    val configured: Boolean = false,
    val account: TrainingAccount? = null,
    val enabled: Boolean = false,
    val busy: Boolean = false,
    val report: TrainingSyncReport? = null,
    val message: String? = null,
)

interface TrainingSync {
    val account: StateFlow<TrainingAccount?>
    val state: StateFlow<TrainingSyncState>
    suspend fun signIn(email: String, password: String)
    fun signOut()
    fun setEnabled(enabled: Boolean)
    suspend fun sync(): TrainingSyncReport?
    /** Stops and joins imports, signs out and resets progress before replacing the database. */
    suspend fun withPausedSyncForRestore(restore: suspend () -> Unit)
}

interface TrainingRemote {
    val configured: Boolean
    val account: StateFlow<TrainingAccount?>
    suspend fun signIn(email: String, password: String)
    fun signOut()
    suspend fun fetchPage(account: TrainingAccount, after: TrainingCursor?, limit: Int): TrainingPage
    suspend fun fetchByIds(account: TrainingAccount, ids: List<String>): List<TrainingDocument>
}

interface TrainingSyncStorage {
    suspend fun load(account: TrainingAccount): StoredTrainingSync
    suspend fun setEnabled(account: TrainingAccount, enabled: Boolean)
    suspend fun saveReport(account: TrainingAccount, report: TrainingSyncReport)
    suspend fun saveProgress(account: TrainingAccount, progress: TrainingSyncProgress)
    suspend fun resetAllProgress()
}

private class TrainingProgressException(cause: Exception) : Exception(cause)

/** One manual run; account changes cancel the old run and never retag its results. */
class TrainingSyncCoordinator(
    private val remote: TrainingRemote,
    private val storage: TrainingSyncStorage,
    private val importDocument: suspend (TrainingAccount, TrainingDocument, Long) -> TrainingImportResult,
    private val scope: CoroutineScope,
    private val onAccountChanged: suspend () -> Unit = {},
    private val syncLog: SyncLog? = null,
) : TrainingSync {
    override val account = remote.account
    private val ownerContext = scope.coroutineContext.minusKey(Job)
    private val mutableState = MutableStateFlow(TrainingSyncState(remote.configured, account.value))
    override val state = mutableState.asStateFlow()
    private val runMutex = Mutex()
    private var running: Job? = null
    private var settingsWrite: Job? = null
    private var generation = 0L
    private var restoring = false

    private suspend fun snapshot(owner: TrainingAccount? = account.value): TrainingSyncState {
        val stored = owner?.let { storage.load(it) }
        return TrainingSyncState(remote.configured, owner, stored?.enabled ?: false,
            busy = restoring, report = stored?.report)
    }

    init {
        scope.launch {
            var previous = account.value
            account.collectLatest { next ->
                val changed = next != previous
                if (changed) {
                    generation++
                    running?.cancel()
                    previous = next
                }
                val currentGeneration = generation
                mutableState.value = TrainingSyncState(remote.configured, next, busy = restoring)
                try {
                    settingsWrite?.join()
                    runMutex.withLock {
                        val loaded = snapshot(next)
                        if (generation == currentGeneration && account.value == next) mutableState.value = loaded
                    }
                } catch (cancel: CancellationException) { throw cancel
                } catch (_: Exception) {
                    if (account.value == next) mutableState.value = state.value.copy(message = "Trainings-Sync-Einstellungen konnten nicht gelesen werden.")
                }
                if (changed) onAccountChanged()
            }
        }
    }

    override suspend fun signIn(email: String, password: String) = withContext(ownerContext) {
        if (!remote.configured || state.value.busy || restoring) return@withContext
        mutableState.value = state.value.copy(busy = true, message = null)
        try { remote.signIn(email.trim(), password) }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { mutableState.value = state.value.copy(message = "Anmeldung fehlgeschlagen. E-Mail, Passwort und Verbindung prüfen.") }
        finally { mutableState.value = state.value.copy(busy = restoring) }
    }

    override fun signOut() {
        generation++
        running?.cancel()
        remote.signOut()
        mutableState.value = TrainingSyncState(remote.configured, account.value, busy = restoring)
    }

    override fun setEnabled(enabled: Boolean) {
        val owner = account.value ?: return
        if (restoring) return
        if (enabled != state.value.enabled) generation++
        if (!enabled) running?.cancel()
        mutableState.value = state.value.copy(enabled = enabled)
        val previousWrite = settingsWrite
        settingsWrite = scope.launch {
            previousWrite?.join()
            try { storage.setEnabled(owner, enabled) }
            catch (cancel: CancellationException) { throw cancel
            } catch (_: Exception) {
                if (account.value == owner) mutableState.value = state.value.copy(enabled = false,
                    message = "Trainings-Sync-Einstellung konnte nicht gespeichert werden.")
            }
        }
    }

    override suspend fun withPausedSyncForRestore(restore: suspend () -> Unit) = withContext(ownerContext) {
        check(!restoring) { "Eine Wiederherstellung läuft bereits." }
        restoring = true
        generation++
        try {
            running?.cancelAndJoin()
            settingsWrite?.join()
            runMutex.withLock {
                remote.signOut()
                mutableState.value = TrainingSyncState(remote.configured, busy = true)
                storage.resetAllProgress()
                restore()
            }
        } finally {
            restoring = false
            mutableState.value = state.value.copy(busy = false)
        }
    }

    override suspend fun sync(): TrainingSyncReport? = withContext(ownerContext) {
        syncLog.recordSyncStep("Trainings synchronisieren", { report ->
            if (report == null) SyncLogOutcome.skipped(when {
                restoring -> "Wiederherstellung läuft"
                !remote.configured -> "Trainingsquelle nicht eingerichtet"
                account.value == null -> "Nicht angemeldet"
                !state.value.enabled -> "Trainings-Sync deaktiviert"
                state.value.busy -> "Ein Trainings-Sync läuft bereits"
                else -> "Anmeldung oder Sync-Einstellung hat sich geändert"
            }) else SyncLogOutcome(if (report.successful) SyncLogStatus.Success else SyncLogStatus.Failed,
                report.description())
        }) { syncTraining() }
    }

    private suspend fun syncTraining(): TrainingSyncReport? {
        if (!remote.configured || restoring || !runMutex.tryLock()) return null
        val owner = account.value
        if (owner == null) { runMutex.unlock(); return null }
        val runGeneration = generation
        fun stillCurrent() = account.value == owner && generation == runGeneration && !restoring
        try {
            settingsWrite?.join()
            if (!stillCurrent()) return null
            val stored = try { storage.load(owner) } catch (cancel: CancellationException) { throw cancel
            } catch (_: Exception) {
                return TrainingSyncReport(Clock.System.now().toEpochMilliseconds(), failed = 1,
                    errors = listOf("Trainingsfortschritt konnte nicht gelesen werden.")).also {
                    mutableState.value = state.value.copy(report = it)
                }
            }
            if (!stillCurrent() || !stored.enabled || !state.value.enabled) return null
            mutableState.value = state.value.copy(account = owner, enabled = true, busy = true, message = null, report = stored.report)
            return supervisorScope {
                val job = async(start = CoroutineStart.LAZY) {
                    var progress = stored.progress
                    val retryIds = progress.pendingIds.distinct().take(20)
                    val pending = linkedSetOf<String>().apply { addAll(progress.pendingIds) }
                    var imported = 0
                    var existing = 0
                    var zero = 0
                    var failed = 0
                    val errors = mutableListOf<String>()
                    fun failure(message: String) {
                        failed++
                        if (errors.size < 20) errors += message
                    }
                    fun checkCurrent() {
                        if (!stillCurrent()) throw CancellationException("Account changed")
                    }
                    suspend fun checkpoint(next: TrainingSyncProgress) {
                        ensureActive()
                        checkCurrent()
                        if (next == progress) return
                        try { storage.saveProgress(owner, next) }
                        catch (cancel: CancellationException) { throw cancel
                        } catch (error: Exception) { throw TrainingProgressException(error) }
                        progress = next
                    }
                    suspend fun import(document: TrainingDocument) {
                        ensureActive()
                        checkCurrent()
                        var success = false
                        try {
                            when (importDocument(owner, document, Clock.System.now().toEpochMilliseconds())) {
                                TrainingImportResult.Imported -> { imported++; success = true }
                                TrainingImportResult.AlreadyImported -> { existing++; success = true }
                                TrainingImportResult.ZeroCalories -> { zero++; success = true }
                                TrainingImportResult.Conflict -> failure("Trainingsabschluss wurde nachträglich verändert; vorhandener Import bleibt erhalten.")
                            }
                        } catch (cancel: CancellationException) { throw cancel
                        } catch (invalid: TrainingValidationException) { failure(invalid.message.orEmpty())
                        } catch (_: Exception) { failure("Ein Training konnte lokal nicht gespeichert werden.") }
                        pending.remove(document.id)
                        if (!success) pending.add(document.id)
                    }
                    try {
                        progress.cursor?.validate()
                        while (true) {
                            ensureActive()
                            checkCurrent()
                            val page = syncLog.recordSyncStep("Trainings vom Server laden", { page ->
                                SyncLogOutcome.success("${page.documents.size} Trainings empfangen · " +
                                    if (progress.initialImportComplete) "Neue Trainings" else "Erstimport")
                            }) {
                                withTimeout(60_000) { remote.fetchPage(owner, progress.cursor, 200) }.also { page ->
                                    require(page.documents.size <= 200) { "Ungültige Seitengröße" }
                                    var previous = progress.cursor
                                    for (document in page.documents) {
                                        val cursor = TrainingCursor.of(document)
                                        require(previous == null || cursor > previous) { "Ungültige Trainingsreihenfolge" }
                                        previous = cursor
                                    }
                                }
                            }
                            val previousFailed = failed
                            val previousImported = imported
                            syncLog.recordSyncStep("Trainings lokal übernehmen", { _: Unit ->
                                SyncLogOutcome(if (failed > previousFailed) SyncLogStatus.Failed else SyncLogStatus.Success,
                                    "${imported - previousImported} übernommen · ${failed - previousFailed} Fehler")
                            }) {
                                for (document in page.documents) import(document)
                                checkpoint(progress.copy(
                                    cursor = page.documents.lastOrNull()?.let(TrainingCursor::of) ?: progress.cursor,
                                    initialImportComplete = progress.initialImportComplete || page.documents.size < 200,
                                    pendingIds = pending.toList(),
                                ))
                            }
                            if (page.documents.size < 200) break
                        }
                        val retries = retryIds.filter { it in pending }
                        if (retries.isNotEmpty()) syncLog.recordSyncStep("Offene Trainings erneut prüfen", { _: Unit ->
                            SyncLogOutcome(if (pending.isEmpty()) SyncLogStatus.Success else SyncLogStatus.Failed,
                                "${pending.size} Trainings weiterhin offen")
                        }) {
                            try {
                                withTimeout(10_000) {
                                    for (ids in retries.chunked(10)) {
                                        // Persist fair rotation before requesting; a timeout must not starve later IDs.
                                        ids.forEach { pending.remove(it); pending.add(it) }
                                        checkpoint(progress.copy(pendingIds = pending.toList()))
                                        val documents = remote.fetchByIds(owner, ids)
                                        require(documents.map { it.id }.distinct().size == documents.size &&
                                            documents.all { it.id in ids }) { "Ungültige Trainingsantwort" }
                                        val byId = documents.associateBy { it.id }
                                        for (id in ids) {
                                            val document = byId[id]
                                            if (document == null) failure("Ein vorgemerktes Training fehlt auf dem Server.")
                                            else import(document)
                                        }
                                        checkpoint(progress.copy(pendingIds = pending.toList()))
                                    }
                                }
                            } catch (_: TimeoutCancellationException) {
                                ensureActive()
                                failure("Zeitbudget für offene Trainings erreicht. Bitte erneut synchronisieren.")
                            }
                        }
                    } catch (_: TimeoutCancellationException) {
                        ensureActive()
                        failure("Firestore antwortet nicht rechtzeitig. Bitte erneut synchronisieren.")
                    } catch (cancel: CancellationException) { throw cancel
                    } catch (_: TrainingProgressException) {
                        failure("Trainingsfortschritt konnte nicht gespeichert werden. Bitte erneut synchronisieren.")
                    } catch (_: Exception) {
                        failure("Firestore konnte nicht vom Server gelesen werden. Anmeldung, Verbindung und Trainingsdaten prüfen.")
                    }
                    ensureActive()
                    checkCurrent()
                    var report = TrainingSyncReport(Clock.System.now().toEpochMilliseconds(), imported, existing, zero, failed, errors.toList(), pending.size)
                    try { storage.saveReport(owner, report) }
                    catch (cancel: CancellationException) { throw cancel
                    } catch (_: Exception) {
                        failure("Trainingsbericht konnte nicht gespeichert werden.")
                        report = report.copy(failed = failed, errors = errors.toList())
                    }
                    ensureActive()
                    checkCurrent()
                    mutableState.value = state.value.copy(report = report)
                    report
                }
                running = job
                job.start()
                try { job.await() } catch (cancel: CancellationException) {
                    currentCoroutineContext().ensureActive()
                    null
                }
            }
        } finally {
            running = null
            mutableState.value = state.value.copy(busy = restoring)
            runMutex.unlock()
        }
    }
}

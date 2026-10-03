package com.maksimowiczm.foodyou.training

import kotlin.time.Clock
import com.maksimowiczm.foodyou.sync.SyncLog
import com.maksimowiczm.foodyou.sync.SyncLogOutcome
import com.maksimowiczm.foodyou.sync.SyncLogStatus
import com.maksimowiczm.foodyou.sync.recordSyncStep
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.Serializable

@Serializable
data class TrainingSyncReport(
    val finishedAtMillis: Long,
    val imported: Int = 0,
    val existing: Int = 0,
    val zeroCalories: Int = 0,
    val failed: Int = 0,
    val errors: List<String> = emptyList(),
) {
    val successful: Boolean get() = failed == 0
    fun description(): String = "$imported Trainings übernommen · $existing bereits vorhanden · $zeroCalories ohne Kalorien · $failed Fehler" +
        if (errors.isEmpty()) "" else "\n" + errors.joinToString("\n")
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
}

interface TrainingRemote {
    val configured: Boolean
    val account: StateFlow<TrainingAccount?>
    suspend fun signIn(email: String, password: String)
    fun signOut()
    suspend fun fetch(account: TrainingAccount): List<TrainingDocument>
}

interface TrainingSyncStorage {
    fun enabled(account: TrainingAccount): Boolean
    fun setEnabled(account: TrainingAccount, enabled: Boolean)
    fun report(account: TrainingAccount): TrainingSyncReport?
    fun saveReport(account: TrainingAccount, report: TrainingSyncReport)
}

/** One manual run; account changes cancel the old run and never retag its results. */
class TrainingSyncCoordinator(
    private val remote: TrainingRemote,
    private val storage: TrainingSyncStorage,
    private val importDocument: suspend (TrainingAccount, TrainingDocument, Long) -> TrainingImportResult,
    scope: CoroutineScope,
    private val onAccountChanged: suspend () -> Unit = {},
    private val syncLog: SyncLog? = null,
) : TrainingSync {
    override val account = remote.account
    private fun snapshot() = TrainingSyncState(remote.configured, account.value,
        account.value?.let(storage::enabled) ?: false, report = account.value?.let(storage::report))
    private val mutableState = MutableStateFlow(snapshot())
    override val state = mutableState.asStateFlow()
    private val runMutex = Mutex()
    private var running: Job? = null
    private var generation = 0L

    init {
        scope.launch {
            var previous = account.value
            account.collect { next ->
                if (next != previous) {
                    generation++
                    running?.cancel()
                    mutableState.value = snapshot()
                    previous = next
                    onAccountChanged()
                }
            }
        }
    }

    override suspend fun signIn(email: String, password: String) {
        if (!remote.configured || state.value.busy) return
        mutableState.value = state.value.copy(busy = true, message = null)
        try { remote.signIn(email.trim(), password) }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { mutableState.value = snapshot().copy(message = "Anmeldung fehlgeschlagen. E-Mail, Passwort und Verbindung prüfen.") }
        finally { mutableState.value = state.value.copy(busy = false) }
    }

    override fun signOut() {
        generation++
        running?.cancel()
        remote.signOut()
        mutableState.value = snapshot()
    }

    override fun setEnabled(enabled: Boolean) {
        val owner = account.value ?: return
        storage.setEnabled(owner, enabled)
        if (!enabled) { generation++; running?.cancel() }
        mutableState.value = state.value.copy(enabled = enabled)
    }

    override suspend fun sync(): TrainingSyncReport? = syncLog.recordSyncStep("Trainings synchronisieren", { report ->
        if (report == null) SyncLogOutcome.skipped(when {
            !remote.configured -> "Trainingsquelle nicht eingerichtet"
            account.value == null -> "Nicht angemeldet"
            !state.value.enabled -> "Trainings-Sync deaktiviert"
            state.value.busy -> "Ein Trainings-Sync läuft bereits"
            else -> "Anmeldung oder Sync-Einstellung hat sich geändert"
        }) else SyncLogOutcome(if (report.successful) SyncLogStatus.Success else SyncLogStatus.Failed,
            report.description())
    }) { syncTraining() }

    private suspend fun syncTraining(): TrainingSyncReport? {
        if (!remote.configured || !state.value.enabled || !runMutex.tryLock()) return null
        val owner = account.value
        if (owner == null) { runMutex.unlock(); return null }
        val runGeneration = generation
        fun stillCurrent() = account.value == owner && generation == runGeneration
        mutableState.value = state.value.copy(busy = true, message = null)
        try {
            return supervisorScope {
                val job = async(start = CoroutineStart.LAZY) {
                    var imported = 0
                    var existing = 0
                    var zero = 0
                    var failed = 0
                    val errors = mutableListOf<String>()
                    try {
                        val documents = syncLog.recordSyncStep("Trainings vom Server laden", { documents ->
                            SyncLogOutcome.success("${documents.size} Trainings empfangen")
                        }) { withTimeout(60_000) { remote.fetch(owner) } }
                        syncLog.recordSyncStep("Trainings lokal übernehmen", { _: Unit ->
                            SyncLogOutcome(if (failed > 0) SyncLogStatus.Failed else SyncLogStatus.Success,
                                "$imported übernommen · $existing bereits vorhanden · $zero ohne Kalorien · $failed Fehler")
                        }) {
                            for (document in documents) {
                                ensureActive()
                                if (!stillCurrent()) throw CancellationException("Account changed")
                                try {
                                    when (importDocument(owner, document, Clock.System.now().toEpochMilliseconds())) {
                                        TrainingImportResult.Imported -> imported++
                                        TrainingImportResult.AlreadyImported -> existing++
                                        TrainingImportResult.ZeroCalories -> zero++
                                        TrainingImportResult.Conflict -> {
                                            failed++
                                            if (errors.size < 20) errors += "Trainingsabschluss wurde nachträglich verändert; vorhandener Import bleibt erhalten."
                                        }
                                    }
                                } catch (cancel: CancellationException) { throw cancel
                                } catch (invalid: TrainingValidationException) {
                                    failed++
                                    if (errors.size < 20) errors += invalid.message.orEmpty()
                                } catch (_: Exception) {
                                    failed++
                                    if (errors.size < 20) errors += "Ein Training konnte lokal nicht gespeichert werden."
                                }
                            }
                        }
                    } catch (_: TimeoutCancellationException) {
                        failed++; errors += "Firestore antwortet nicht rechtzeitig. Bitte erneut synchronisieren."
                    } catch (cancel: CancellationException) { throw cancel
                    } catch (_: Exception) {
                        failed++; errors += "Firestore konnte nicht vom Server gelesen werden. Anmeldung, Verbindung und Zugriffsrechte prüfen."
                    }
                    ensureActive()
                    if (!stillCurrent()) throw CancellationException("Account changed")
                    TrainingSyncReport(Clock.System.now().toEpochMilliseconds(), imported, existing, zero, failed, errors).also {
                        storage.saveReport(owner, it)
                        mutableState.value = state.value.copy(report = it)
                    }
                }
                running = job
                job.start()
                try { job.await() } catch (cancel: CancellationException) {
                    currentCoroutineContext().ensureActive() // A caller cancellation still propagates.
                    null // Account switch cancels only this provider, not the other manual sync branches.
                }
            }
        } finally {
            running = null
            mutableState.value = state.value.copy(busy = false)
            runMutex.unlock()
        }
    }
}

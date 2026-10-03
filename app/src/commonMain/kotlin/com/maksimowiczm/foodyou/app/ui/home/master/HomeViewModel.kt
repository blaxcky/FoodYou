package com.maksimowiczm.foodyou.app.ui.home.master

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.activity.HealthConnectActivitySync
import com.maksimowiczm.foodyou.activity.HealthConnectSyncResult
import com.maksimowiczm.foodyou.activity.domain.repository.ActivityRepository
import com.maksimowiczm.foodyou.app.widget.updateCalorieWidgetValues
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.food.domain.repository.FddbCredentialsRepository
import com.maksimowiczm.foodyou.food.domain.usecase.FddbDiarySyncResult
import com.maksimowiczm.foodyou.food.domain.usecase.ManualFddbDiarySyncUseCase
import com.maksimowiczm.foodyou.settings.domain.entity.FddbDiarySyncStatus
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
import com.maksimowiczm.foodyou.settings.domain.entity.fddbDiarySyncStatus
import com.maksimowiczm.foodyou.weight.HealthConnectWeightSync
import com.maksimowiczm.foodyou.sync.SyncLog
import com.maksimowiczm.foodyou.sync.recordSyncRun
import com.maksimowiczm.foodyou.sync.recordSyncStep
import com.maksimowiczm.foodyou.sync.recordSyncSkipped
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

internal data class HomeActivitySyncState(
    val isStale: Boolean,
    val isSyncing: Boolean,
    val burnedEnergySyncDeltas: Map<LocalDate, Int>,
)

internal sealed interface HomeFddbSyncState {
    data class Idle(val hasCredentials: Boolean, val lastStatus: FddbDiarySyncStatus? = null) :
        HomeFddbSyncState

    data object Syncing : HomeFddbSyncState

    data object MissingCredentials : HomeFddbSyncState

    data class Failed(val status: FddbDiarySyncStatus) : HomeFddbSyncState
}

internal data class HomeSyncState(
    val activitySyncState: HomeActivitySyncState,
    val fddbSyncState: HomeFddbSyncState,
    val healthConnectEnabled: Boolean,
    val fddbDiaryEnabled: Boolean,
    val configuredSyncInProgress: Boolean = false,
    val trainingState: com.maksimowiczm.foodyou.training.TrainingSyncState = com.maksimowiczm.foodyou.training.TrainingSyncState(),
) {
    val isSyncing: Boolean
        get() =
            configuredSyncInProgress || (healthConnectEnabled && activitySyncState.isSyncing) ||
                (fddbDiaryEnabled && fddbSyncState is HomeFddbSyncState.Syncing)

    val hasFddbFailure: Boolean
        get() =
            when (fddbSyncState) {
                is HomeFddbSyncState.Failed -> true
                is HomeFddbSyncState.Idle -> fddbSyncState.lastStatus?.hasFailure == true
                HomeFddbSyncState.MissingCredentials,
                HomeFddbSyncState.Syncing,
                -> false
            }
}

internal data class HomeConfiguredSyncResult(
    val healthConnectSynced: Boolean,
    val fddbDiarySynced: Boolean,
    val fddbMissingCredentials: Boolean,
    val trainingSynced: Boolean = false,
)

private const val BURNED_ENERGY_SYNC_DELTA_VISIBLE_MILLIS = 120_000L

internal class HomeViewModel(
    private val settingsRepository: UserPreferencesRepository<Settings>,
    private val healthConnectActivitySync: HealthConnectActivitySync,
    private val healthConnectWeightSync: HealthConnectWeightSync,
    private val activityRepository: ActivityRepository,
    private val manualFddbDiarySyncUseCase: ManualFddbDiarySyncUseCase,
    private val fddbCredentialsRepository: FddbCredentialsRepository,
    private val trainingSync: com.maksimowiczm.foodyou.training.TrainingSync,
    private val syncLog: SyncLog? = null,
) : ViewModel() {

    private val _homeOrder = settingsRepository.observe().map { it.homeCardOrder }
    val homeOrder =
        _homeOrder.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(2_000),
            initialValue = runBlocking { _homeOrder.first() },
        )

    private val nowEpochSeconds = MutableStateFlow(Clock.System.now().epochSeconds)
    private val configuredSyncRunner = HomeSyncRunner(viewModelScope)
    private val configuredSyncInProgress = configuredSyncRunner.isSyncing
    private val isSyncing = MutableStateFlow(false)
    private val burnedEnergySyncDeltas = MutableStateFlow<Map<LocalDate, Int>>(emptyMap())
    private val fddbSyncInProgress = MutableStateFlow(false)
    private var burnedEnergySyncDeltaClearJob: Job? = null

    val activitySyncState: StateFlow<HomeActivitySyncState> =
        combine(settingsRepository.observe(), nowEpochSeconds, isSyncing, burnedEnergySyncDeltas) {
                settings,
                now,
                syncing,
                syncDeltas,
            ->
                val lastSynced = settings.healthConnectStepsLastSyncedEpochSeconds
                HomeActivitySyncState(
                    isStale = lastSynced == null || now - lastSynced > 5 * 60,
                    isSyncing = syncing,
                    burnedEnergySyncDeltas = syncDeltas,
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(2_000),
                initialValue =
                    runBlocking {
                        val settings = settingsRepository.observe().first()
                        val lastSynced = settings.healthConnectStepsLastSyncedEpochSeconds
                        HomeActivitySyncState(
                            isStale =
                                lastSynced == null ||
                                    Clock.System.now().epochSeconds - lastSynced > 5 * 60,
                            isSyncing = false,
                            burnedEnergySyncDeltas = emptyMap(),
                        )
                    },
            )

    val fddbSyncState: StateFlow<HomeFddbSyncState> =
        combine(
                settingsRepository.observe(),
                fddbCredentialsRepository.hasCredentials(),
                fddbSyncInProgress,
            ) { settings, hasCredentials, syncing ->
                val status = settings.fddbDiarySyncStatus()
                when {
                    syncing -> HomeFddbSyncState.Syncing
                    status?.hasFailure == true -> HomeFddbSyncState.Failed(status)
                    !hasCredentials -> HomeFddbSyncState.MissingCredentials
                    else -> HomeFddbSyncState.Idle(hasCredentials = true, lastStatus = status)
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(2_000),
                initialValue = HomeFddbSyncState.Idle(hasCredentials = false),
            )

    val homeSyncState: StateFlow<HomeSyncState> =
        combine(settingsRepository.observe(), activitySyncState, fddbSyncState, configuredSyncInProgress, trainingSync.state) {
                settings,
                activityState,
                fddbState,
                configuredSyncing,
                trainingState,
            ->
                HomeSyncState(
                    activitySyncState = activityState,
                    fddbSyncState = fddbState,
                    configuredSyncInProgress = configuredSyncing,
                    trainingState = trainingState,
                    healthConnectEnabled = settings.homeSyncHealthConnectEnabled,
                    fddbDiaryEnabled = settings.homeSyncFddbDiaryEnabled,
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(2_000),
                initialValue =
                    runBlocking {
                        val settings = settingsRepository.observe().first()
                        HomeSyncState(
                            activitySyncState = activitySyncState.value,
                            fddbSyncState = fddbSyncState.value,
                            healthConnectEnabled = settings.homeSyncHealthConnectEnabled,
                            fddbDiaryEnabled = settings.homeSyncFddbDiaryEnabled,
                        )
                    },
            )

    init {
        viewModelScope.launch {
            while (true) {
                nowEpochSeconds.value = Clock.System.now().epochSeconds
                delay(30_000)
            }
        }
    }

    fun syncActivities(date: LocalDate) {
        if (configuredSyncInProgress.value || isSyncing.value) return

        viewModelScope.launch {
            isSyncing.value = true
            clearBurnedEnergySyncDelta()
            try {
                val syncDelta =
                    syncActivitiesForBurnedEnergyDelta(
                        date = date,
                        settingsRepository = settingsRepository,
                        healthConnectActivitySync = healthConnectActivitySync,
                        activityRepository = activityRepository,
                    )
                syncDelta?.let(::showBurnedEnergySyncDelta)
                if (syncDelta != null) {
                    updateCalorieWidgetValues()
                }
            } finally {
                nowEpochSeconds.value = Clock.System.now().epochSeconds
                isSyncing.value = false
            }
        }
    }

    fun syncConfigured(date: LocalDate) {
        if (isSyncing.value || fddbSyncInProgress.value) return

        configuredSyncRunner.launch {
            syncLog.recordSyncRun("Startseite · $date") {
                val settings = settingsRepository.observe().first()
                val syncAccount = trainingSync.account.value
                val deltaDates = burnedEnergySyncDeltaDates(date)
                val before = syncLog.recordSyncStep("Kalorienstand vor dem Sync lesen") {
                    readBurnedEnergySnapshot(deltaDates, settingsRepository, activityRepository)
                }
                clearBurnedEnergySyncDelta()
                val result =
                    syncConfiguredHomeSync(
                        date = date,
                        settings = settings,
                        settingsRepository = settingsRepository,
                        syncLog = syncLog,
                        syncHealthConnect = {
                            if (!isSyncing.value) {
                                isSyncing.value = true
                                try {
                                    syncHomeSteps(
                                        date = date,
                                        settingsRepository = settingsRepository,
                                        healthConnectActivitySync = healthConnectActivitySync,
                                    )
                                } finally {
                                    nowEpochSeconds.value = Clock.System.now().epochSeconds
                                    isSyncing.value = false
                                }
                            }
                        },
                        syncWeight = { healthConnectWeightSync.syncHistorical() },
                        syncTraining = { trainingSync.sync() != null },
                        hasFddbCredentials = manualFddbDiarySyncUseCase::hasCredentials,
                        syncFddbDiary = { selectedDate ->
                            if (fddbSyncInProgress.value) {
                                Result.Success(FddbDiarySyncResult(imported = 0, skipped = 0, failed = 0))
                            } else {
                                fddbSyncInProgress.value = true
                                try {
                                    manualFddbDiarySyncUseCase.sync(selectedDate)
                                } finally {
                                    fddbSyncInProgress.value = false
                                }
                            }
                        },
                    )
                if (syncAccount == trainingSync.account.value) {
                    syncLog.recordSyncStep("Kalorienänderung nach dem Sync berechnen") {
                        val after = readBurnedEnergySnapshot(deltaDates, settingsRepository, activityRepository)
                        showBurnedEnergySyncDelta(deltaDates.associateWith {
                            burnedEnergySyncDeltaKcal(before.getValue(it), after.getValue(it))
                        })
                    }
                }
                if (result.healthConnectSynced || result.fddbDiarySynced || result.trainingSynced) {
                    syncLog.recordSyncStep("Kalorien-Widgets aktualisieren") { updateCalorieWidgetValues() }
                }
            }
        }
    }

    fun syncFddbDiary(date: LocalDate) {
        if (configuredSyncInProgress.value || fddbSyncInProgress.value) return

        viewModelScope.launch {
            if (!manualFddbDiarySyncUseCase.hasCredentials()) {
                return@launch
            }
            fddbSyncInProgress.value = true
            try {
                when (manualFddbDiarySyncUseCase.sync(date)) {
                    is Result.Success -> updateCalorieWidgetValues()
                    is Result.Error -> Unit
                }
            } finally {
                fddbSyncInProgress.value = false
            }
        }
    }

    fun clearFddbFailure() {
        viewModelScope.launch {
            settingsRepository.update {
                copy(fddbDiarySyncLastFailed = 0, fddbDiarySyncLastErrorMessage = null)
            }
        }
    }

    private fun showBurnedEnergySyncDelta(deltas: Map<LocalDate, Int>) {
        burnedEnergySyncDeltas.value = deltas
        burnedEnergySyncDeltaClearJob?.cancel()
        burnedEnergySyncDeltaClearJob =
            viewModelScope.launch {
                delay(BURNED_ENERGY_SYNC_DELTA_VISIBLE_MILLIS)
                burnedEnergySyncDeltas.value = emptyMap()
                burnedEnergySyncDeltaClearJob = null
            }
    }

    private fun clearBurnedEnergySyncDelta() {
        burnedEnergySyncDeltaClearJob?.cancel()
        burnedEnergySyncDeltaClearJob = null
        burnedEnergySyncDeltas.value = emptyMap()
    }
}

internal suspend fun syncConfiguredHomeSync(
    date: LocalDate,
    settings: Settings,
    settingsRepository: UserPreferencesRepository<Settings>,
    syncHealthConnect: suspend () -> Unit,
    hasFddbCredentials: suspend () -> Boolean,
    syncFddbDiary: suspend (LocalDate) -> Result<FddbDiarySyncResult, Throwable>,
    syncWeight: suspend () -> Unit = {},
    syncTraining: suspend () -> Boolean = { false },
    syncLog: SyncLog? = null,
): HomeConfiguredSyncResult = supervisorScope {
    val training = async {
        var completed = false
        syncBranch { completed = syncTraining() }
        completed
    }
    val health = async {
        if (!settings.homeSyncHealthConnectEnabled) {
            syncLog.recordSyncSkipped("Schritte synchronisieren", "Startseiten-Sync für Schritte deaktiviert")
            false
        }
        else syncBranch { syncHealthConnect() }
    }
    val weight = async {
        if (settings.healthConnectWeightEnabled) syncBranch { syncWeight() }
        else syncLog.recordSyncSkipped("Gewicht synchronisieren", "Gewichts-Sync deaktiviert")
    }
    val diary = async {
        var synced = false
        var missingCredentials = false
        if (settings.homeSyncFddbDiaryEnabled) {
            syncBranch {
                if (syncLog.recordSyncStep("FDDB-Anmeldung prüfen") { hasFddbCredentials() }) {
                    synced = syncFddbDiary(date) is Result.Success
                } else {
                    missingCredentials = true
                    syncLog.recordSyncSkipped("FDDB-Tagebuch synchronisieren", "Nicht bei FDDB angemeldet")
                }
            }
        }
        if (!settings.homeSyncFddbDiaryEnabled) {
            syncLog.recordSyncSkipped("FDDB-Tagebuch synchronisieren", "Startseiten-Sync für FDDB deaktiviert")
        }
        synced to missingCredentials
    }
    val healthSynced = health.await()
    val (diarySynced, missingCredentials) = diary.await()
    weight.await()
    HomeConfiguredSyncResult(healthSynced, diarySynced, missingCredentials, training.await())
}

private suspend fun syncBranch(block: suspend () -> Unit): Boolean =
    try {
        block()
        true
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        false
    }

internal suspend fun syncActivitiesForBurnedEnergyDelta(
    date: LocalDate,
    settingsRepository: UserPreferencesRepository<Settings>,
    healthConnectActivitySync: HealthConnectActivitySync,
    activityRepository: ActivityRepository,
    today: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
): Map<LocalDate, Int>? {
    val targetDates = burnedEnergySyncDeltaDates(selectedDate = date, today = today)
    val before = readBurnedEnergySnapshot(targetDates, settingsRepository, activityRepository)
    return when (syncHomeSteps(date, settingsRepository, healthConnectActivitySync)) {
        HealthConnectSyncResult.MissingPermission,
        HealthConnectSyncResult.Unavailable,
        HealthConnectSyncResult.UpdateRequired,
        -> {
            null
        }

        HealthConnectSyncResult.Synced,
        -> {
            val after = readBurnedEnergySnapshot(targetDates, settingsRepository, activityRepository)
            targetDates.associateWith { targetDate ->
                burnedEnergySyncDeltaKcal(before = before.getValue(targetDate), after = after.getValue(targetDate))
            }
        }

        HealthConnectSyncResult.Disabled,
        HealthConnectSyncResult.Failed,
        -> null
    }
}

internal suspend fun syncHomeSteps(
    date: LocalDate,
    settingsRepository: UserPreferencesRepository<Settings>,
    healthConnectActivitySync: HealthConnectActivitySync,
): HealthConnectSyncResult {
    val result = healthConnectActivitySync.syncStepsForHome(date)
    if (result == HealthConnectSyncResult.MissingPermission ||
        result == HealthConnectSyncResult.Unavailable || result == HealthConnectSyncResult.UpdateRequired) {
        settingsRepository.update { copy(healthConnectStepsEnabled = false) }
    }
    return result
}

internal suspend fun readBurnedEnergySnapshot(
    dates: List<LocalDate>,
    settingsRepository: UserPreferencesRepository<Settings>,
    activityRepository: ActivityRepository,
): Map<LocalDate, Double> {
    val settings = settingsRepository.observe().first()
    return dates.distinct().associateWith { date ->
        activityRepository.observeDailySummary(date, settings.stepsCaloriesPerStepKcal).first().totalEnergyKcal
    }
}

internal fun burnedEnergySyncDeltaKcal(before: Double, after: Double): Int =
    (after.roundToInt() - before.roundToInt()).coerceAtLeast(0)

internal fun burnedEnergySyncDeltaDates(
    selectedDate: LocalDate,
    today: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
): List<LocalDate> = listOf(selectedDate, today, today.minus(1, DateTimeUnit.DAY)).distinct()

/** Owns the complete manual sync lifetime, including a weight-only sync. */
internal class HomeSyncRunner(private val scope: CoroutineScope) {
    private val running = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = running.asStateFlow()

    fun launch(block: suspend () -> Unit) {
        if (!running.compareAndSet(false, true)) return
        scope.launch { block() }.invokeOnCompletion { running.value = false }
    }
}

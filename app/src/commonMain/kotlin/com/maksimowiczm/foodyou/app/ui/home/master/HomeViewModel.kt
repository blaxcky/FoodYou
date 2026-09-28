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

private const val HEALTH_CONNECT_STEPS_SYNC_LOOKBACK_DAYS = 30
private const val BURNED_ENERGY_SYNC_DELTA_VISIBLE_MILLIS = 120_000L

internal class HomeViewModel(
    private val settingsRepository: UserPreferencesRepository<Settings>,
    private val healthConnectActivitySync: HealthConnectActivitySync,
    private val healthConnectWeightSync: HealthConnectWeightSync,
    private val activityRepository: ActivityRepository,
    private val manualFddbDiarySyncUseCase: ManualFddbDiarySyncUseCase,
    private val fddbCredentialsRepository: FddbCredentialsRepository,
    private val trainingSync: com.maksimowiczm.foodyou.training.TrainingSync,
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
        viewModelScope.launch { healthConnectWeightSync.syncHistorical() }
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
            val settings = settingsRepository.observe().first()
            val syncAccount = trainingSync.account.value
            val deltaDates = burnedEnergySyncDeltaDates(date)
            val before = deltaDates.associateWith { dailyBurnedEnergyKcal(it, settingsRepository, activityRepository) }
            clearBurnedEnergySyncDelta()
            val result =
                syncConfiguredHomeSync(
                    date = date,
                    settings = settings,
                    settingsRepository = settingsRepository,
                    syncHealthConnect = {
                        if (!isSyncing.value) {
                            isSyncing.value = true
                            clearBurnedEnergySyncDelta()
                            try {
                                syncActivitiesForBurnedEnergyDelta(
                                        date = date,
                                        settingsRepository = settingsRepository,
                                        healthConnectActivitySync = healthConnectActivitySync,
                                        activityRepository = activityRepository,
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
                showBurnedEnergySyncDelta(deltaDates.associateWith {
                    burnedEnergySyncDeltaKcal(before.getValue(it), dailyBurnedEnergyKcal(it, settingsRepository, activityRepository))
                })
            }
            if (result.healthConnectSynced || result.fddbDiarySynced || result.trainingSynced) {
                updateCalorieWidgetValues()
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
): HomeConfiguredSyncResult = supervisorScope {
    val training = async {
        var completed = false
        syncBranch { completed = syncTraining() }
        completed
    }
    val health = async {
        if (!settings.homeSyncHealthConnectEnabled) false
        else syncBranch { syncHealthConnect() }
    }
    val weight = async {
        if (settings.healthConnectWeightEnabled) syncBranch { syncWeight() }
    }
    val diary = async {
        var synced = false
        var missingCredentials = false
        if (settings.homeSyncFddbDiaryEnabled) {
            syncBranch {
                if (hasFddbCredentials()) {
                    synced = syncFddbDiary(date) is Result.Success
                } else {
                    missingCredentials = true
                }
            }
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
    val before =
        targetDates.associateWith { targetDate ->
            dailyBurnedEnergyKcal(targetDate, settingsRepository, activityRepository)
        }
    return when (healthConnectActivitySync.syncSteps(healthConnectStepsSyncDates(date, today))) {
        HealthConnectSyncResult.MissingPermission,
        HealthConnectSyncResult.Unavailable,
        HealthConnectSyncResult.UpdateRequired,
        -> {
            settingsRepository.update { copy(healthConnectStepsEnabled = false) }
            null
        }

        HealthConnectSyncResult.Synced,
        -> {
            targetDates.associateWith { targetDate ->
                val after = dailyBurnedEnergyKcal(targetDate, settingsRepository, activityRepository)
                burnedEnergySyncDeltaKcal(before = before.getValue(targetDate), after = after)
            }
        }

        HealthConnectSyncResult.Disabled,
        HealthConnectSyncResult.Failed,
        -> null
    }
}

private suspend fun dailyBurnedEnergyKcal(
    date: LocalDate,
    settingsRepository: UserPreferencesRepository<Settings>,
    activityRepository: ActivityRepository,
): Double {
    val settings = settingsRepository.observe().first()
    return activityRepository
        .observeDailySummary(date, settings.stepsCaloriesPerStepKcal)
        .first()
        .totalEnergyKcal
}

internal fun burnedEnergySyncDeltaKcal(before: Double, after: Double): Int =
    (after.roundToInt() - before.roundToInt()).coerceAtLeast(0)

internal fun burnedEnergySyncDeltaDates(
    selectedDate: LocalDate,
    today: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
): List<LocalDate> = listOf(selectedDate, today, today.minus(1, DateTimeUnit.DAY)).distinct()

internal fun healthConnectStepsSyncDates(
    selectedDate: LocalDate,
    today: LocalDate =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
    lookbackDays: Int = HEALTH_CONNECT_STEPS_SYNC_LOOKBACK_DAYS,
): List<LocalDate> {
    val start = today.minus(lookbackDays, DateTimeUnit.DAY)
    val dates = mutableListOf<LocalDate>()
    var date = start
    while (date <= today) {
        dates += date
        date = date.plus(1, DateTimeUnit.DAY)
    }
    if (selectedDate !in dates) {
        dates += selectedDate
    }
    return dates.distinct().sorted()
}

/** Owns the complete manual sync lifetime, including a weight-only sync. */
internal class HomeSyncRunner(private val scope: CoroutineScope) {
    private val running = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = running.asStateFlow()

    fun launch(block: suspend () -> Unit) {
        if (!running.compareAndSet(false, true)) return
        scope.launch { block() }.invokeOnCompletion { running.value = false }
    }
}

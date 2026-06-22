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
import kotlin.time.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt

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
) {
    val isSyncing: Boolean
        get() =
            (healthConnectEnabled && activitySyncState.isSyncing) ||
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
)

private const val HEALTH_CONNECT_STEPS_SYNC_LOOKBACK_DAYS = 30
private const val BURNED_ENERGY_SYNC_DELTA_VISIBLE_MILLIS = 120_000L

internal class HomeViewModel(
    private val settingsRepository: UserPreferencesRepository<Settings>,
    private val healthConnectActivitySync: HealthConnectActivitySync,
    private val activityRepository: ActivityRepository,
    private val manualFddbDiarySyncUseCase: ManualFddbDiarySyncUseCase,
    private val fddbCredentialsRepository: FddbCredentialsRepository,
) : ViewModel() {

    private val _homeOrder = settingsRepository.observe().map { it.homeCardOrder }
    val homeOrder =
        _homeOrder.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(2_000),
            initialValue = runBlocking { _homeOrder.first() },
        )

    private val nowEpochSeconds = MutableStateFlow(Clock.System.now().epochSeconds)
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
        combine(settingsRepository.observe(), activitySyncState, fddbSyncState) {
                settings,
                activityState,
                fddbState,
            ->
                HomeSyncState(
                    activitySyncState = activityState,
                    fddbSyncState = fddbState,
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
        if (isSyncing.value) return

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
        if (homeSyncState.value.isSyncing) return

        viewModelScope.launch {
            val settings = settingsRepository.observe().first()
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
                                    ?.let(::showBurnedEnergySyncDelta)
                            } finally {
                                nowEpochSeconds.value = Clock.System.now().epochSeconds
                                isSyncing.value = false
                            }
                        }
                    },
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
            if (result.healthConnectSynced || result.fddbDiarySynced) {
                updateCalorieWidgetValues()
            }
        }
    }

    fun syncFddbDiary(date: LocalDate) {
        if (fddbSyncInProgress.value) return

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
): HomeConfiguredSyncResult {
    var healthConnectSynced = false
    var fddbDiarySynced = false
    var fddbMissingCredentials = false

    if (settings.homeSyncHealthConnectEnabled) {
        syncHealthConnect()
        healthConnectSynced = true
    }

    if (settings.homeSyncFddbDiaryEnabled) {
        if (hasFddbCredentials()) {
            when (syncFddbDiary(date)) {
                is Result.Success -> fddbDiarySynced = true
                is Result.Error -> Unit
            }
        } else {
            fddbMissingCredentials = true
        }
    }

    return HomeConfiguredSyncResult(
        healthConnectSynced = healthConnectSynced,
        fddbDiarySynced = fddbDiarySynced,
        fddbMissingCredentials = fddbMissingCredentials,
    )
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

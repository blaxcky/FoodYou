package com.maksimowiczm.foodyou.app.ui.home.master

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.activity.HealthConnectActivitySync
import com.maksimowiczm.foodyou.activity.HealthConnectSyncResult
import com.maksimowiczm.foodyou.activity.domain.repository.ActivityRepository
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
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

internal data class BurnedEnergySyncDelta(val date: LocalDate, val kcal: Int)

internal data class HomeActivitySyncState(
    val isStale: Boolean,
    val isSyncing: Boolean,
    val burnedEnergySyncDelta: BurnedEnergySyncDelta?,
)

private const val HEALTH_CONNECT_STEPS_SYNC_LOOKBACK_DAYS = 30
private const val BURNED_ENERGY_SYNC_DELTA_VISIBLE_MILLIS = 120_000L

internal class HomeViewModel(
    private val settingsRepository: UserPreferencesRepository<Settings>,
    private val healthConnectActivitySync: HealthConnectActivitySync,
    private val activityRepository: ActivityRepository,
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
    private val burnedEnergySyncDelta = MutableStateFlow<BurnedEnergySyncDelta?>(null)
    private var burnedEnergySyncDeltaClearJob: Job? = null

    val activitySyncState: StateFlow<HomeActivitySyncState> =
        combine(settingsRepository.observe(), nowEpochSeconds, isSyncing, burnedEnergySyncDelta) {
                settings,
                now,
                syncing,
                syncDelta,
            ->
                val lastSynced = settings.healthConnectStepsLastSyncedEpochSeconds
                HomeActivitySyncState(
                    isStale = lastSynced == null || now - lastSynced > 5 * 60,
                    isSyncing = syncing,
                    burnedEnergySyncDelta = syncDelta,
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
                            burnedEnergySyncDelta = null,
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
    }

    private fun showBurnedEnergySyncDelta(delta: BurnedEnergySyncDelta) {
        burnedEnergySyncDelta.value = delta
        burnedEnergySyncDeltaClearJob?.cancel()
        burnedEnergySyncDeltaClearJob =
            viewModelScope.launch {
                delay(BURNED_ENERGY_SYNC_DELTA_VISIBLE_MILLIS)
                burnedEnergySyncDelta.value = null
                burnedEnergySyncDeltaClearJob = null
            }
    }

    private fun clearBurnedEnergySyncDelta() {
        burnedEnergySyncDeltaClearJob?.cancel()
        burnedEnergySyncDeltaClearJob = null
        burnedEnergySyncDelta.value = null
    }
}

internal suspend fun syncActivitiesForBurnedEnergyDelta(
    date: LocalDate,
    settingsRepository: UserPreferencesRepository<Settings>,
    healthConnectActivitySync: HealthConnectActivitySync,
    activityRepository: ActivityRepository,
): BurnedEnergySyncDelta? {
    val before = dailyBurnedEnergyKcal(date, settingsRepository, activityRepository)
    return when (healthConnectActivitySync.syncSteps(healthConnectStepsSyncDates(date))) {
        HealthConnectSyncResult.MissingPermission,
        HealthConnectSyncResult.Unavailable,
        HealthConnectSyncResult.UpdateRequired,
        -> {
            settingsRepository.update { copy(healthConnectStepsEnabled = false) }
            null
        }

        HealthConnectSyncResult.Synced,
        -> {
            val after = dailyBurnedEnergyKcal(date, settingsRepository, activityRepository)
            BurnedEnergySyncDelta(
                date = date,
                kcal = burnedEnergySyncDeltaKcal(before = before, after = after),
            )
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

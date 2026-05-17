package com.maksimowiczm.foodyou.app.ui.home.master

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.activity.HealthConnectActivitySync
import com.maksimowiczm.foodyou.activity.HealthConnectSyncResult
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
import kotlin.time.Clock
import kotlinx.coroutines.delay
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

internal data class HomeActivitySyncState(val isStale: Boolean, val isSyncing: Boolean)

private const val HEALTH_CONNECT_STEPS_SYNC_LOOKBACK_DAYS = 30

internal class HomeViewModel(
    private val settingsRepository: UserPreferencesRepository<Settings>,
    private val healthConnectActivitySync: HealthConnectActivitySync,
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

    val activitySyncState: StateFlow<HomeActivitySyncState> =
        combine(settingsRepository.observe(), nowEpochSeconds, isSyncing) {
                settings,
                now,
                syncing,
            ->
                val lastSynced = settings.healthConnectStepsLastSyncedEpochSeconds
                HomeActivitySyncState(
                    isStale = lastSynced == null || now - lastSynced > 5 * 60,
                    isSyncing = syncing,
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
            try {
                when (healthConnectActivitySync.syncSteps(healthConnectStepsSyncDates(date))) {
                    HealthConnectSyncResult.MissingPermission,
                    HealthConnectSyncResult.Unavailable,
                    HealthConnectSyncResult.UpdateRequired,
                    -> settingsRepository.update { copy(healthConnectStepsEnabled = false) }

                    HealthConnectSyncResult.Synced,
                    HealthConnectSyncResult.Disabled,
                    HealthConnectSyncResult.Failed,
                    -> Unit
                }
            } finally {
                nowEpochSeconds.value = Clock.System.now().epochSeconds
                isSyncing.value = false
            }
        }
    }
}

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

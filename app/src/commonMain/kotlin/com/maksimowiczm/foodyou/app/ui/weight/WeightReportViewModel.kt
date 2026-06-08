package com.maksimowiczm.foodyou.app.ui.weight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.activity.HealthConnectAvailability
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.goals.domain.entity.BasalMetabolicRateProfile
import com.maksimowiczm.foodyou.goals.domain.repository.BasalMetabolicRateProfileRepository
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
import com.maksimowiczm.foodyou.weight.HealthConnectWeightSync
import com.maksimowiczm.foodyou.weight.domain.entity.DailyWeightEntry
import com.maksimowiczm.foodyou.weight.domain.entity.WeightGoal
import com.maksimowiczm.foodyou.weight.domain.repository.WeightRepository
import kotlin.math.round
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

internal class WeightReportViewModel(
    private val repository: WeightRepository,
    private val basalMetabolicRateProfileRepository: BasalMetabolicRateProfileRepository,
    private val healthConnectWeightSync: HealthConnectWeightSync,
    private val settingsRepository: UserPreferencesRepository<Settings>,
) : ViewModel() {
    private val healthConnectPermissionGranted = MutableStateFlow(false)
    private val availability = MutableStateFlow<HealthConnectAvailability?>(null)

    private val reportState =
        combine(
            repository.observeEntries(),
            repository.observeToday(),
            repository.observeGoal(),
            basalMetabolicRateProfileRepository.observeProfile(),
            ::createReportState,
        )

    val state =
        combine(
                reportState,
                healthConnectPermissionGranted,
                availability,
            ) { reportState, permissionGranted, availability ->
                reportState.copy(
                    healthConnectAvailable = availability == HealthConnectAvailability.Available,
                    healthConnectPermissionGranted = permissionGranted,
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeightReportUiState())

    init {
        viewModelScope.launch {
            availability.value = healthConnectWeightSync.availability()
            healthConnectPermissionGranted.value = healthConnectWeightSync.hasWeightPermission()
            if (healthConnectPermissionGranted.value) {
                settingsRepository.update { copy(healthConnectWeightEnabled = true) }
                healthConnectWeightSync.syncHistorical()
            }
        }
    }

    fun setWeight(weightKg: Double) {
        if (weightKg <= 0.0) return
        viewModelScope.launch {
            val rounded = round(weightKg * 10.0) / 10.0
            repository.upsertToday(rounded)
            repository.entry(today())?.let { healthConnectWeightSync.writeToday(it) }
        }
    }

    fun adjustWeight(deltaKg: Double) {
        val base = state.value.todayWeightKg ?: state.value.suggestedWeightKg ?: return
        setWeight(base + deltaKg)
    }

    fun setTargetWeight(weightKg: Double?) {
        viewModelScope.launch { repository.updateGoal(WeightGoal(weightKg)) }
    }

    fun onHealthConnectPermissionResult(granted: Boolean) {
        healthConnectPermissionGranted.value = granted
        if (granted) {
            viewModelScope.launch {
                settingsRepository.update { copy(healthConnectWeightEnabled = true) }
                healthConnectWeightSync.syncHistorical()
            }
        }
    }

    private fun createReportState(
        entries: List<DailyWeightEntry>,
        todayEntry: DailyWeightEntry?,
        goal: WeightGoal,
        profile: BasalMetabolicRateProfile,
    ): WeightReportUiState {
        val current = todayEntry ?: entries.maxByOrNull { it.measuredAt }
        val chartStart = today().minus(1, DateTimeUnit.YEAR)
        return WeightReportUiState(
            entries = entries,
            chartEntries = entries.filter { it.date >= chartStart }.sortedBy { it.date },
            todayWeightKg = todayEntry?.weightKg,
            suggestedWeightKg = current?.weightKg,
            startWeightKg = entries.minByOrNull { it.date }?.weightKg,
            currentWeightKg = current?.weightKg,
            targetWeightKg = goal.targetWeightKg,
            heightCm = profile.heightCm,
        )
    }

    private fun today(): LocalDate =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
}

internal data class WeightReportUiState(
    val entries: List<DailyWeightEntry> = emptyList(),
    val chartEntries: List<DailyWeightEntry> = emptyList(),
    val todayWeightKg: Double? = null,
    val suggestedWeightKg: Double? = null,
    val startWeightKg: Double? = null,
    val currentWeightKg: Double? = null,
    val targetWeightKg: Double? = null,
    val heightCm: Double? = null,
    val healthConnectAvailable: Boolean = false,
    val healthConnectPermissionGranted: Boolean = false,
)

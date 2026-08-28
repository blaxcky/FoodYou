package com.maksimowiczm.foodyou.app.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.activity.HealthConnectActivitySync
import com.maksimowiczm.foodyou.activity.HealthConnectSyncResult
import com.maksimowiczm.foodyou.activity.domain.entity.StepExclusionPeriod
import com.maksimowiczm.foodyou.activity.domain.repository.ActivityRepository
import com.maksimowiczm.foodyou.activity.domain.usecase.isValidStepExclusionPeriod
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

internal data class StepExclusionsUiState(
    val date: LocalDate? = null,
    val rawSteps: Long = 0,
    val excludedSteps: Long = 0,
    val countedSteps: Long = 0,
    val periods: List<StepExclusionPeriod> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val syncFailed: Boolean = false,
    val saveFailed: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
) {
    val invalidPeriodIndices: Set<Int> =
        periods.indices.filterTo(mutableSetOf()) { !isValidStepExclusionPeriod(periods[it]) }
    val canSave: Boolean =
        !isLoading && !isSaving && hasUnsavedChanges && invalidPeriodIndices.isEmpty()
}

internal class StepExclusionsViewModel(
    private val repository: ActivityRepository,
    private val healthConnectActivitySync: HealthConnectActivitySync,
) : ViewModel() {
    private val mutableState = MutableStateFlow(StepExclusionsUiState())
    val state: StateFlow<StepExclusionsUiState> = mutableState.asStateFlow()

    private var loadJob: Job? = null
    private var loadedDate: LocalDate? = null
    private var persistedPeriods: List<StepExclusionPeriod> = emptyList()
    private var draftInitialized = false

    fun load(date: LocalDate) {
        if (loadedDate == date && loadJob?.isActive == true) return
        loadedDate = date
        draftInitialized = false
        mutableState.value = StepExclusionsUiState(date = date)
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                combine(
                    repository.observeDailySummary(date, kcalPerStep = null),
                    repository.observeStepExclusionPeriods(date),
                ) { summary, periods -> summary to periods }
                    .collect { (summary, periods) ->
                        persistedPeriods = periods
                        val current = mutableState.value
                        val draft = if (draftInitialized) current.periods else periods
                        draftInitialized = true
                        mutableState.value =
                            current.copy(
                                date = date,
                                rawSteps = summary.rawSteps,
                                excludedSteps = summary.excludedSteps,
                                countedSteps = summary.countedSteps,
                                periods = draft,
                                isLoading = false,
                                hasUnsavedChanges = draft != periods,
                            )
                    }
            }
    }

    fun addPeriod() {
        val date = loadedDate ?: return
        updateDraft(mutableState.value.periods + StepExclusionPeriod(date, 9 * 60, 10 * 60))
    }

    fun updatePeriod(index: Int, startMinute: Int, endMinute: Int) {
        val periods = mutableState.value.periods
        if (index !in periods.indices) return
        updateDraft(periods.toMutableList().also { it[index] = it[index].copy(startMinute = startMinute, endMinute = endMinute) })
    }

    fun deletePeriod(index: Int) {
        val periods = mutableState.value.periods
        if (index !in periods.indices) return
        updateDraft(periods.toMutableList().also { it.removeAt(index) })
    }

    fun save(onSuccess: () -> Unit) {
        val current = mutableState.value
        val date = loadedDate ?: return
        if (!current.canSave) return
        val draft =
            current.periods
                .distinct()
                .sortedWith(
                    compareBy(StepExclusionPeriod::startMinute, StepExclusionPeriod::endMinute)
                )

        viewModelScope.launch {
            mutableState.value = current.copy(isSaving = true, syncFailed = false, saveFailed = false)
            try {
                repository.replaceStepExclusionPeriods(date, draft)
                persistedPeriods = draft
                mutableState.value =
                    mutableState.value.copy(
                        periods = draft,
                        hasUnsavedChanges = false,
                    )
            } catch (error: CancellationException) {
                throw error
            } catch (_: RuntimeException) {
                mutableState.value = mutableState.value.copy(isSaving = false, saveFailed = true)
                return@launch
            }

            finishSync(date, onSuccess)
        }
    }

    fun retrySync(onSuccess: () -> Unit) {
        val date = loadedDate ?: return
        if (mutableState.value.isSaving) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isSaving = true, syncFailed = false)
            finishSync(date, onSuccess)
        }
    }

    private suspend fun finishSync(date: LocalDate, onSuccess: () -> Unit) {
        when (healthConnectActivitySync.syncSteps(listOf(date))) {
            HealthConnectSyncResult.Synced -> {
                mutableState.value = mutableState.value.copy(isSaving = false, syncFailed = false)
                onSuccess()
            }

            else ->
                mutableState.value = mutableState.value.copy(isSaving = false, syncFailed = true)
        }
    }

    private fun updateDraft(periods: List<StepExclusionPeriod>) {
        mutableState.value =
            mutableState.value.copy(
                periods = periods,
                syncFailed = false,
                saveFailed = false,
                hasUnsavedChanges = periods != persistedPeriods,
            )
    }
}

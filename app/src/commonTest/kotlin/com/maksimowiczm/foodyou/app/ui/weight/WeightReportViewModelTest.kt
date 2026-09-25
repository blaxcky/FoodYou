package com.maksimowiczm.foodyou.app.ui.weight

import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.activity.HealthConnectAvailability
import com.maksimowiczm.foodyou.activity.HealthConnectSyncResult
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.goals.domain.entity.BasalMetabolicRateProfile
import com.maksimowiczm.foodyou.goals.domain.repository.BasalMetabolicRateProfileRepository
import com.maksimowiczm.foodyou.settings.domain.entity.AppLaunchInfo
import com.maksimowiczm.foodyou.settings.domain.entity.EnergyFormat
import com.maksimowiczm.foodyou.settings.domain.entity.GoalDisplayMode
import com.maksimowiczm.foodyou.settings.domain.entity.HomeCard
import com.maksimowiczm.foodyou.settings.domain.entity.NutrientsOrder
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
import com.maksimowiczm.foodyou.weight.HealthConnectWeightSync
import com.maksimowiczm.foodyou.weight.domain.entity.DailyWeightEntry
import com.maksimowiczm.foodyou.weight.domain.entity.WeightGoal
import com.maksimowiczm.foodyou.weight.domain.repository.WeightRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate

class WeightReportViewModelTest {
    @Test
    fun unchangedImportedWeightIsNotExported() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = FakeWeightRepository(nextManualEntry = null)
        val sync = FakeWeightSync()
        val viewModel = createViewModel(repository, sync)
        try {
            viewModel.setWeight(102.5)
            advanceUntilIdle()

            assertEquals(1, repository.upsertTodayCalls)
            assertEquals(emptyList(), sync.exportedEntries)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun changedWeightExportsReturnedFoodYouEntryExactlyOnce() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val date = LocalDate(2026, 6, 7)
        val entry =
            DailyWeightEntry(
                id = "local:${date.toEpochDays()}",
                date = date,
                weightKg = 102.6,
                measuredAt = Instant.parse("2026-06-07T08:00:00Z"),
                healthConnectRecordId = null,
                isFoodYouRecord = true,
            )
        val repository = FakeWeightRepository(nextManualEntry = entry)
        val sync = FakeWeightSync()
        val viewModel = createViewModel(repository, sync)
        try {
            viewModel.setWeight(102.6)
            advanceUntilIdle()

            assertEquals(1, repository.upsertTodayCalls)
            assertEquals(listOf(entry), sync.exportedEntries)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    private fun createViewModel(repository: WeightRepository, sync: HealthConnectWeightSync) =
        WeightReportViewModel(
            repository = repository,
            basalMetabolicRateProfileRepository = FakeBmrRepository(),
            healthConnectWeightSync = sync,
            settingsRepository = FakeSettingsRepository(),
        )
}

private class FakeWeightRepository(
    private val nextManualEntry: DailyWeightEntry?,
) : WeightRepository {
    var upsertTodayCalls = 0

    override fun observeEntries(): Flow<List<DailyWeightEntry>> = flowOf(emptyList())

    override fun observeMeasurements(): Flow<List<DailyWeightEntry>> = flowOf(emptyList())

    override fun observeToday(): Flow<DailyWeightEntry?> = flowOf(null)

    override fun observeGoal(): Flow<WeightGoal> = flowOf(WeightGoal(null))

    override suspend fun upsertToday(weightKg: Double): DailyWeightEntry? {
        upsertTodayCalls += 1
        return nextManualEntry
    }

    override suspend fun upsert(entry: DailyWeightEntry) = Unit

    override suspend fun upsertAll(entries: List<DailyWeightEntry>) = Unit

    override suspend fun entry(date: LocalDate): DailyWeightEntry? = null

    override suspend fun setHidden(id: String, hidden: Boolean) = Unit

    override suspend fun updateGoal(goal: WeightGoal) = Unit
}

private class FakeWeightSync : HealthConnectWeightSync {
    val exportedEntries = mutableListOf<DailyWeightEntry>()

    override suspend fun availability() = HealthConnectAvailability.Unavailable

    override suspend fun hasWeightPermission() = false

    override suspend fun syncHistorical() = HealthConnectSyncResult.Synced

    override suspend fun writeFoodYouEntry(entry: DailyWeightEntry): HealthConnectSyncResult {
        exportedEntries += entry
        return HealthConnectSyncResult.Synced
    }
}

private class FakeBmrRepository : BasalMetabolicRateProfileRepository {
    override suspend fun updateProfile(profile: BasalMetabolicRateProfile) = Unit

    override fun observeProfile(): Flow<BasalMetabolicRateProfile> =
        flowOf(BasalMetabolicRateProfile.Empty)
}

private class FakeSettingsRepository : UserPreferencesRepository<Settings> {
    private val settings =
        MutableStateFlow(
            Settings(
                lastRememberedVersion = null,
                hidePreviewDialog = false,
                showTranslationWarning = false,
                nutrientsOrder = NutrientsOrder.defaultOrder,
                secureScreen = false,
                homeCardOrder = HomeCard.defaultOrder,
                expandGoalCard = false,
                goalDisplayMode = GoalDisplayMode.Normal,
                dietEnergyDeficitKcal = null,
                onboardingFinished = true,
                energyFormat = EnergyFormat.DEFAULT,
                appLaunchInfo = AppLaunchInfo(null, null, 0),
                stepsCaloriesPerStepKcal = null,
                healthConnectStepsEnabled = false,
                healthConnectStepsLastSyncedEpochSeconds = null,
            )
        )

    override fun observe(): Flow<Settings> = settings

    override suspend fun update(transform: Settings.() -> Settings) {
        settings.value = transform(settings.value)
    }
}

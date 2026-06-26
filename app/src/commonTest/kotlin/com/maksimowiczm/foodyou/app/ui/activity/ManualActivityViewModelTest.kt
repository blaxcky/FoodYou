package com.maksimowiczm.foodyou.app.ui.activity

import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.activity.domain.entity.DailyActivitySummary
import com.maksimowiczm.foodyou.activity.domain.entity.DailyStepSummary
import com.maksimowiczm.foodyou.activity.domain.entity.ManualActivityEntry
import com.maksimowiczm.foodyou.activity.domain.entity.ManualActivityEntryId
import com.maksimowiczm.foodyou.activity.domain.repository.ActivityRepository
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.settings.domain.entity.AppLaunchInfo
import com.maksimowiczm.foodyou.settings.domain.entity.EnergyFormat
import com.maksimowiczm.foodyou.settings.domain.entity.GoalDisplayMode
import com.maksimowiczm.foodyou.settings.domain.entity.HomeCard
import com.maksimowiczm.foodyou.settings.domain.entity.NutrientsOrder
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

class ManualActivityViewModelTest {
    private val viewModels = mutableListOf<ManualActivityViewModel>()

    @Test
    fun crosstrainerPresetSavesCalculatedEnergyAndPersistsDiscount() = runViewModelTest {
        val activityRepository = FakeActivityRepository()
        val settingsRepository =
            FakeSettingsRepository(
                defaultSettings().copy(crosstrainerCalorieDiscountPercent = 20.0)
            )
        val viewModel = createViewModel(activityRepository, settingsRepository)
        var saved = false

        viewModel.selectPreset(ManualActivityPreset.Crosstrainer)
        viewModel.setEnergyKcal("500")
        viewModel.setDiscountPercent("20")
        viewModel.save(LocalDate(2026, 6, 26), id = null) { saved = true }
        advanceUntilIdle()

        assertEquals("Crosstrainer", activityRepository.created.single().name)
        assertEquals(400.0, activityRepository.created.single().energyKcal)
        assertEquals(20.0, settingsRepository.value.crosstrainerCalorieDiscountPercent)
        assertEquals(1, settingsRepository.updateCount)
        assertTrue(saved)
    }

    @Test
    fun customNameSavesEnteredEnergyWithoutPersistingDiscount() = runViewModelTest {
        val activityRepository = FakeActivityRepository()
        val settingsRepository =
            FakeSettingsRepository(
                defaultSettings().copy(crosstrainerCalorieDiscountPercent = 20.0)
            )
        val viewModel = createViewModel(activityRepository, settingsRepository)

        viewModel.setName("Walk")
        viewModel.setEnergyKcal("500")
        viewModel.setDiscountPercent("20")
        viewModel.save(LocalDate(2026, 6, 26), id = null) {}
        advanceUntilIdle()

        assertEquals("Walk", activityRepository.created.single().name)
        assertEquals(500.0, activityRepository.created.single().energyKcal)
        assertEquals(0, settingsRepository.updateCount)
    }

    @Test
    fun existingCrosstrainerEntryDoesNotActivateCalculatorOnSave() = runViewModelTest {
        val existing =
            manualEntry(
                id = 12,
                date = LocalDate(2026, 6, 25),
                name = "Crosstrainer",
                energyKcal = 500.0,
            )
        val activityRepository = FakeActivityRepository(existing)
        val settingsRepository =
            FakeSettingsRepository(
                defaultSettings().copy(crosstrainerCalorieDiscountPercent = 20.0)
            )
        val viewModel = createViewModel(activityRepository, settingsRepository)

        viewModel.load(12)
        viewModel.save(LocalDate(2026, 6, 26), id = 12) {}
        advanceUntilIdle()

        assertNull(viewModel.preset.value)
        assertEquals(500.0, activityRepository.updated.single().energyKcal)
        assertEquals(0, settingsRepository.updateCount)
    }

    private fun runViewModelTest(block: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            block()
        } finally {
            viewModels.forEach { it.viewModelScope.cancel() }
            viewModels.clear()
            advanceUntilIdle()
            Dispatchers.resetMain()
        }
    }

    private fun createViewModel(
        activityRepository: FakeActivityRepository,
        settingsRepository: FakeSettingsRepository,
    ): ManualActivityViewModel =
        ManualActivityViewModel(activityRepository, settingsRepository).also(viewModels::add)

    private class FakeActivityRepository(vararg initialEntries: ManualActivityEntry) :
        ActivityRepository {
        private val entries = initialEntries.associateBy { it.id }.toMutableMap()
        val created = mutableListOf<ManualActivityEntry>()
        val updated = mutableListOf<ManualActivityEntry>()

        override fun observeManualEntry(id: ManualActivityEntryId): Flow<ManualActivityEntry?> =
            flowOf(entries[id])

        override fun observeManualEntries(date: LocalDate): Flow<List<ManualActivityEntry>> =
            flowOf(entries.values.filter { it.date == date })

        override fun observeDailySummary(
            date: LocalDate,
            kcalPerStep: Double?,
        ): Flow<DailyActivitySummary> = error("Not used")

        override suspend fun createManualEntry(entry: ManualActivityEntry): ManualActivityEntryId {
            val id = ManualActivityEntryId(created.size + 1L)
            val createdEntry = entry.copy(id = id)
            entries[id] = createdEntry
            created += createdEntry
            return id
        }

        override suspend fun updateManualEntry(entry: ManualActivityEntry) {
            entries[entry.id] = entry
            updated += entry
        }

        override suspend fun deleteManualEntry(id: ManualActivityEntryId) = error("Not used")

        override suspend fun upsertStepSummary(summary: DailyStepSummary) = error("Not used")
    }

    private class FakeSettingsRepository(initialValue: Settings) :
        UserPreferencesRepository<Settings> {
        private val state = MutableStateFlow(initialValue)
        val value: Settings
            get() = state.value
        var updateCount = 0
            private set

        override fun observe(): Flow<Settings> = state

        override suspend fun update(transform: Settings.() -> Settings) {
            updateCount++
            state.value = state.value.transform()
        }
    }

    private companion object {
        fun manualEntry(
            id: Long,
            date: LocalDate,
            name: String,
            energyKcal: Double,
        ): ManualActivityEntry =
            ManualActivityEntry(
                id = ManualActivityEntryId(id),
                date = date,
                name = name,
                energyKcal = energyKcal,
                createdAt = LocalDateTime(2026, 6, 25, 12, 0),
                updatedAt = LocalDateTime(2026, 6, 25, 12, 0),
            )

        fun defaultSettings(): Settings =
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
                appLaunchInfo =
                    AppLaunchInfo(
                        firstLaunch = null,
                        firstLaunchCurrentVersion = null,
                        launchesCount = 0,
                    ),
                stepsCaloriesPerStepKcal = null,
                healthConnectStepsEnabled = true,
                healthConnectStepsLastSyncedEpochSeconds = null,
            )
    }
}

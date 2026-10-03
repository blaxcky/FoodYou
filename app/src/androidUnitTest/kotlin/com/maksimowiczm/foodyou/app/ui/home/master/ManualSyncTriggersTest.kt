package com.maksimowiczm.foodyou.app.ui.home.master

import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.activity.HealthConnectAvailability
import com.maksimowiczm.foodyou.activity.HealthConnectActivitySync
import com.maksimowiczm.foodyou.activity.HealthConnectSyncResult
import com.maksimowiczm.foodyou.activity.domain.entity.DailyActivitySummary
import com.maksimowiczm.foodyou.activity.domain.repository.ActivityRepository
import com.maksimowiczm.foodyou.app.ui.settings.SynchronizationSettingsViewModel
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.food.domain.repository.FddbCredentialsRepository
import com.maksimowiczm.foodyou.food.domain.usecase.*
import com.maksimowiczm.foodyou.fooddiary.domain.usecase.CreateFoodDiaryEntryUseCase
import com.maksimowiczm.foodyou.settings.domain.entity.*
import com.maksimowiczm.foodyou.sync.SyncLog
import com.maksimowiczm.foodyou.sync.SyncLogRun
import com.maksimowiczm.foodyou.sync.SyncLogStore
import com.maksimowiczm.foodyou.training.*
import com.maksimowiczm.foodyou.weight.HealthConnectWeightSync
import com.maksimowiczm.foodyou.weight.domain.entity.DailyWeightEntry
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class ManualSyncTriggersTest {
    @Test
    fun openingHomeDoesNotSyncWeightAndManualSyncHonorsSetting() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            for (enabled in listOf(false, true)) {
                val fixture = Fixture(enabled)
                val viewModel = fixture.home()
                try {
                    runCurrent()
                    assertEquals(0, fixture.weight.imports)
                    viewModel.syncConfigured(today())
                    runCurrent()
                    assertEquals(if (enabled) 1 else 0, fixture.weight.imports)
                } finally {
                    viewModel.viewModelScope.cancel()
                }
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun homeReadsCaloriesOnlyBeforeAndAfterTrainingFinishes() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture(true, stepsEnabled = true)
        val release = CompletableDeferred<Unit>()
        fixture.training.run = { release.await(); TrainingSyncReport(0) }
        val viewModel = fixture.home()
        try {
            viewModel.syncConfigured(today())
            runCurrent()
            val dates = burnedEnergySyncDeltaDates(today())
            assertEquals(dates, fixture.calorieReads)
            assertEquals(1, fixture.weight.imports)
            release.complete(Unit)
            runCurrent()
            assertEquals(dates + dates, fixture.calorieReads)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun trainingAccountChangeSuppressesAfterSnapshot() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture(true)
        fixture.training.run = {
            fixture.training.account.value = TrainingAccount(uid = "other", email = null)
            TrainingSyncReport(0)
        }
        val viewModel = fixture.home()
        try {
            viewModel.syncConfigured(today())
            runCurrent()
            assertEquals(burnedEnergySyncDeltaDates(today()), fixture.calorieReads)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun enablingWeightSyncOnlyChangesSetting() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture(false)
        val viewModel = SynchronizationSettingsViewModel(
            fixture.settings, fixture.diary, fixture.credentials, fixture.weight, fixture.log,
        )
        try {
            viewModel.setWeightSyncEnabled(true)
            runCurrent()
            assertTrue(fixture.settings.value.healthConnectWeightEnabled)
            assertEquals(0, fixture.weight.imports)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    private class Fixture(weightEnabled: Boolean, stepsEnabled: Boolean = false) {
        val settings = TestSettings(weightEnabled, stepsEnabled)
        val weight = TestWeightSync()
        val training = TestTrainingSync()
        val calorieReads = mutableListOf<LocalDate>()
        private val activities: ActivityRepository = stub { name, args ->
            check(name == "observeDailySummary") { "Unexpected activity call: $name" }
            calorieReads += args[0] as LocalDate
            flowOf(DailyActivitySummary(0, 0, 0, 0.0, 0.0, 100.0))
        }
        val credentials = object : FddbCredentialsRepository {
            override fun hasCredentials() = flowOf(false)
            override suspend fun loadCredentials(): Pair<String, String>? = null
            override suspend fun store(login: String, password: String) = error("Not used")
            override suspend fun clear() = error("Not used")
        }
        val log = SyncLog(object : SyncLogStore {
            override val runs = MutableStateFlow(emptyList<SyncLogRun>())
            override suspend fun update(transform: (List<SyncLogRun>) -> List<SyncLogRun>) {
                runs.value = transform(runs.value)
            }
        })
        // FDDB is disabled; accidental calls fail rather than perform any work.
        private val productSync = SyncFddbProductUseCase(
            unused(), ResyncFddbProductUseCase(unused(), unused(), unused(), unused()), unused(), settings,
        )
        val diary = ManualFddbDiarySyncUseCase(
            settings,
            FddbDiarySyncUseCase(credentials, unused(), unused(), unused(), unused(), unused(),
                CreateFoodDiaryEntryUseCase(unused(), unused(), unused(), unused(), unused()), unused(), unused()),
            FddbProductSyncCoordinator(settings, unused(), SyncDueFddbProductsUseCase(unused(), productSync), productSync),
        )
        fun home() = HomeViewModel(settings,
            stub<HealthConnectActivitySync> { name, _ ->
                check(name == "syncStepsForHome") { "Unexpected step call: $name" }
                HealthConnectSyncResult.Synced
            }, weight, activities, diary, credentials, training, log)
    }

    private class TestSettings(weightEnabled: Boolean, stepsEnabled: Boolean) : UserPreferencesRepository<Settings> {
        private val state = MutableStateFlow(Settings(
            lastRememberedVersion = null, hidePreviewDialog = false,
            showTranslationWarning = false, nutrientsOrder = NutrientsOrder.defaultOrder,
            secureScreen = false, homeCardOrder = HomeCard.defaultOrder,
            expandGoalCard = false, goalDisplayMode = GoalDisplayMode.Normal,
            dietEnergyDeficitKcal = null, onboardingFinished = true,
            energyFormat = EnergyFormat.DEFAULT, appLaunchInfo = AppLaunchInfo(null, null, 0),
            stepsCaloriesPerStepKcal = null, healthConnectStepsEnabled = stepsEnabled,
            healthConnectStepsLastSyncedEpochSeconds = null,
            homeSyncHealthConnectEnabled = stepsEnabled, homeSyncFddbDiaryEnabled = false,
            healthConnectWeightEnabled = weightEnabled,
        ))
        val value get() = state.value
        override fun observe(): Flow<Settings> = state
        override suspend fun update(transform: Settings.() -> Settings) {
            state.value = state.value.transform()
        }
    }

    private class TestWeightSync : HealthConnectWeightSync {
        var imports = 0
        override suspend fun availability() = HealthConnectAvailability.Available
        override suspend fun hasWeightPermission() = true
        override suspend fun syncHistorical(): HealthConnectSyncResult {
            imports++
            return HealthConnectSyncResult.Synced
        }
        override suspend fun writeFoodYouEntry(entry: DailyWeightEntry) = error("No export expected")
    }

    private class TestTrainingSync : TrainingSync {
        override val account = MutableStateFlow<TrainingAccount?>(TrainingAccount(uid = "first", email = null))
        override val state = MutableStateFlow(TrainingSyncState())
        var run: suspend () -> TrainingSyncReport? = { null }
        override suspend fun sync() = run()
        override suspend fun signIn(email: String, password: String) = error("Not used")
        override fun signOut() = error("Not used")
        override fun setEnabled(enabled: Boolean) = error("Not used")
    }

    companion object {
        private fun today() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        private inline fun <reified T> unused(): T = stub { name, _ -> error("Unexpected call: $name") }

        @Suppress("UNCHECKED_CAST")
        private inline fun <reified T> stub(crossinline call: (String, Array<out Any?>) -> Any?): T =
            Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
                call(method.name, args ?: emptyArray())
            } as T
    }
}

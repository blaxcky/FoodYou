package com.maksimowiczm.foodyou.app.ui.activity

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.maksimowiczm.foodyou.activity.domain.repository.ActivityRepository
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.settings.domain.entity.*
import com.maksimowiczm.foodyou.training.*
import java.lang.reflect.Proxy
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "de-rDE-w390dp-h844dp-mdpi", application = Application::class)
class ManualActivityScreenScreenshotTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ActivityController<ComponentActivity>
    private var settingsOpened = 0
    private var saved = 0
    private var syncs = 0

    @After fun tearDown() {
        if (::activity.isInitialized) activity.close()
        stopKoin()
    }

    @Test fun trainingButtonSitsBelowSaveAndImportsWithoutSavingActivity() {
        show(TrainingSyncState(configured = true,
            account = TrainingAccount(uid = "test", email = "training@example.test")))
        val save = compose.onNodeWithText("Speichern").fetchSemanticsNode().boundsInRoot.top
        val training = compose.onNodeWithText("Trainings-App").fetchSemanticsNode().boundsInRoot.top
        assertTrue(save < training)
        assertEquals(0, syncs)
        compose.onNodeWithText("Name").performTextInput("Spaziergang")
        compose.onNodeWithText("Verbrannte kcal").performTextInput("200")
        compose.onNodeWithText("Trainings jetzt importieren").performScrollTo().performClick()
        assertEquals(1, syncs)
        assertEquals(0, saved)
        capture("manual-training")
    }

    @Test fun signedOutCanScrollToSetupWithoutSavingActivity() {
        show(TrainingSyncState(configured = true))
        compose.onNodeWithText("Konto einrichten").performScrollTo().performClick()
        assertEquals(1, settingsOpened)
        assertEquals(0, saved)
        assertEquals(0, syncs)
        capture("training-setup")
    }

    @Test fun detailsBelowFormRemainScrollable() {
        show(TrainingSyncState(configured = true, account = TrainingAccount(uid = "test", email = null),
            report = TrainingSyncReport(1791208900000, imported = 1,
                importedSessions = listOf(ImportedTrainingSession("session", "2026-09-27", 210, 120)))))
        compose.onNodeWithText("27.09.2026").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("330 kcal").assertIsDisplayed()
        capture("training-details")
    }

    private fun show(state: TrainingSyncState) {
        val training = object : TrainingSync {
            override val account = MutableStateFlow(state.account)
            override val state = MutableStateFlow(state)
            override fun startManualSync() { syncs++ }
            override suspend fun sync(): TrainingSyncReport? = error("Use manual start")
            override suspend fun signIn(email: String, password: String) = error("Use settings")
            override fun signOut() = error("Use settings")
            override suspend fun withPausedSyncForRestore(restore: suspend () -> Unit) = error("Unused")
        }
        val settings = object : UserPreferencesRepository<Settings> {
            override fun observe() = flowOf(Settings(
                lastRememberedVersion = null, hidePreviewDialog = false, showTranslationWarning = false,
                nutrientsOrder = NutrientsOrder.defaultOrder, secureScreen = false,
                homeCardOrder = HomeCard.defaultOrder, expandGoalCard = false,
                goalDisplayMode = GoalDisplayMode.Normal, dietEnergyDeficitKcal = null,
                onboardingFinished = true, energyFormat = EnergyFormat.DEFAULT,
                appLaunchInfo = AppLaunchInfo(null, null, 0), stepsCaloriesPerStepKcal = null,
                healthConnectStepsEnabled = false, healthConnectStepsLastSyncedEpochSeconds = null,
            ))
            override suspend fun update(transform: Settings.() -> Settings) = error("No setting change expected")
        }
        val repository = Proxy.newProxyInstance(ActivityRepository::class.java.classLoader,
            arrayOf(ActivityRepository::class.java)) { _, method, _ ->
            error("Training button must not save an activity: ${method.name}")
        } as ActivityRepository
        startKoin {
            modules(module {
                single<TrainingSync> { training }
                viewModel { ManualActivityViewModel(repository, settings) }
            })
        }
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        activity.get().setContent {
            MaterialTheme {
                ManualActivityScreen(LocalDate(2026, 10, 5), null, null,
                    onBack = {}, onSave = { saved++ }, onTrainingSettings = { settingsOpened++ })
            }
        }
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        compose.onNode(isRoot()).captureRoboImage("ManualActivityScreenScreenshotTest.$name.png")
    }
}

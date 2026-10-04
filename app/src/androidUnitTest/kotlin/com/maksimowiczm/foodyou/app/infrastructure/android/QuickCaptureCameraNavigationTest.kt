package com.maksimowiczm.foodyou.app.infrastructure.android

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.maksimowiczm.foodyou.app.ui.FoodYouLaunchAction
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.common.infrastructure.system.AndroidSystemDetails
import com.maksimowiczm.foodyou.settings.domain.entity.AppLaunchInfo
import com.maksimowiczm.foodyou.settings.domain.entity.EnergyFormat
import com.maksimowiczm.foodyou.settings.domain.entity.GoalDisplayMode
import com.maksimowiczm.foodyou.settings.domain.entity.HomeCard
import com.maksimowiczm.foodyou.settings.domain.entity.NutrientsOrder
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class QuickCaptureCameraNavigationTest {
    private lateinit var controller: ActivityController<QuickCaptureCameraActivity>

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        startKoin {
            modules(module {
                single { AndroidSystemDetails(context) }
                single<UserPreferencesRepository<Settings>>(named(Settings::class.qualifiedName!!)) {
                    object : UserPreferencesRepository<Settings> {
                        override fun observe() = flowOf(cameraSettings())
                        override suspend fun update(transform: Settings.() -> Settings) = Unit
                    }
                }
            })
        }
        controller = Robolectric.buildActivity(QuickCaptureCameraActivity::class.java).create().start()
    }

    @After
    fun tearDown() {
        if (::controller.isInitialized) controller.stop().destroy()
        stopKoin()
    }

    @Test
    fun arrowOpensMainAppInPhotosAndRemovesCameraTask() {
        val activity = controller.get()

        activity.openPhotos()

        val intent = assertNotNull(shadowOf(activity).nextStartedActivity)
        assertEquals(ComponentName(activity, MainActivity::class.java), intent.component)
        assertEquals(FoodYouLaunchAction.QuickCapturePhotos, intent.toLaunchRequest()?.action)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun closeRemovesCameraTaskWithoutOpeningMainApp() {
        val activity = controller.get()

        activity.closeCamera()

        assertNull(shadowOf(activity).nextStartedActivity)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun androidBackRemovesCameraTaskWithoutOpeningMainApp() {
        val activity = controller.get()

        activity.onBackPressedDispatcher.onBackPressed()

        assertNull(shadowOf(activity).nextStartedActivity)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun eachPhotoLaunchHasANewRequest() {
        val intent = quickCapturePhotosIntent(controller.get())

        val first = assertNotNull(intent.toLaunchRequest())
        val second = assertNotNull(intent.toLaunchRequest())

        assertEquals(FoodYouLaunchAction.QuickCapturePhotos, first.action)
        assertEquals(first.action, second.action)
        assertNotEquals(first.nonce, second.nonce)
    }

    @Test
    fun ordinaryLaunchAndBarcodeKeepTheirExistingMeaning() {
        assertNull(Intent(Intent.ACTION_MAIN).toLaunchRequest())
        assertNull(null.toLaunchRequest())
        assertEquals(
            FoodYouLaunchAction.ScanBarcode,
            Intent(ACTION_SCAN_BARCODE).toLaunchRequest()?.action,
        )
    }

    private fun cameraSettings() = Settings(
        lastRememberedVersion = null,
        hidePreviewDialog = true,
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
}

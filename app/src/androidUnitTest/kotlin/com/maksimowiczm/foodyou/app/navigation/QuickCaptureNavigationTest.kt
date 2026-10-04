package com.maksimowiczm.foodyou.app.navigation

import android.app.Application
import android.content.Context
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.toRoute
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.serialization.Serializable
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class QuickCaptureNavigationTest {
    private lateinit var navController: NavHostController

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        navController = NavHostController(context).apply {
            navigatorProvider.addNavigator(ComposeNavigator())
            graph = createGraph(startDestination = Home) {
                composable<Home> {}
                composable<QuickCapture> {}
                composable<OtherScreen> {}
            }
        }
    }

    @Test
    fun coldLaunchOpensPhotosWithHomeUnderneath() {
        navController.openQuickCapturePhotos()

        assertPhotosWithHomeUnderneath()
    }

    @Test
    fun warmLaunchReplacesExistingScreenWithPhotos() {
        navController.navigate(OtherScreen)

        navController.openQuickCapturePhotos()

        assertPhotosWithHomeUnderneath()
    }

    @Test
    fun warmLaunchReplacesExistingQuickCaptureWithPhotos() {
        navController.navigate(QuickCapture())
        navController.navigate(OtherScreen)

        navController.openQuickCapturePhotos()

        assertPhotosWithHomeUnderneath()
    }

    @Test
    fun repeatedLaunchDoesNotDuplicateQuickCapture() {
        repeat(3) { navController.openQuickCapturePhotos() }

        assertPhotosWithHomeUnderneath()
    }

    @Test
    fun inAppQuickCaptureStillDefaultsToLog() {
        navController.navigate(QuickCapture())

        val route = assertNotNull(navController.currentBackStackEntry).toRoute<QuickCapture>()
        assertFalse(route.showPhotos)
    }

    private fun assertPhotosWithHomeUnderneath() {
        val route = assertNotNull(navController.currentBackStackEntry).toRoute<QuickCapture>()
        assertTrue(route.showPhotos)
        assertEquals(Home, assertNotNull(navController.previousBackStackEntry).toRoute<Home>())
        assertTrue(navController.popBackStack())
        assertEquals(Home, assertNotNull(navController.currentBackStackEntry).toRoute<Home>())
        assertFalse(navController.popBackStack<Home>(inclusive = false))
    }
}

@Serializable internal object OtherScreen

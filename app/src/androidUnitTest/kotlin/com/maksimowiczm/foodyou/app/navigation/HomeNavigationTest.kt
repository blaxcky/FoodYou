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
import kotlin.test.assertNull
import kotlinx.serialization.Serializable
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class HomeNavigationTest {
    private lateinit var navController: NavHostController

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        navController = NavHostController(context).apply {
            navigatorProvider.addNavigator(ComposeNavigator())
            graph = createGraph(startDestination = Home) {
                composable<Home> {}
                composable<WidgetTestFoodSearch> {}
                composable<WidgetTestFoodEntry> {}
            }
        }
    }

    @Test
    fun coldWidgetLaunchKeepsOnlyHome() {
        navController.openHome()

        assertOnlyHome()
    }

    @Test
    fun widgetLaunchLeavesFoodEntryAndClearsPreviousScreens() {
        navController.navigate(WidgetTestFoodSearch)
        navController.navigate(WidgetTestFoodEntry)

        navController.openHome()

        assertOnlyHome()
    }

    @Test
    fun repeatedWidgetLaunchDoesNotDuplicateHome() {
        repeat(3) { navController.openHome() }

        assertOnlyHome()
    }

    @Test
    fun widgetLaunchReturnsHomeAgainAfterOpeningAnotherScreen() {
        navController.openHome()
        navController.navigate(WidgetTestFoodEntry)

        navController.openHome()

        assertOnlyHome()
    }

    private fun assertOnlyHome() {
        assertEquals(Home, assertNotNull(navController.currentBackStackEntry).toRoute<Home>())
        assertNull(navController.previousBackStackEntry)
        assertFalse(navController.popBackStack<Home>(inclusive = false))
    }
}

@Serializable internal object WidgetTestFoodSearch

@Serializable internal object WidgetTestFoodEntry

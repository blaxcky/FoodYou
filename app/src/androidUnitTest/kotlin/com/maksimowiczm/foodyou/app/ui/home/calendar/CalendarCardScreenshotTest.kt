package com.maksimowiczm.foodyou.app.ui.home.calendar

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.locale
import com.github.takahirom.roborazzi.size
import com.maksimowiczm.foodyou.app.ui.common.utility.EnergyFormatter
import com.maksimowiczm.foodyou.app.ui.common.utility.EnergyFormatterProvider
import com.maksimowiczm.foodyou.common.compose.utility.AndroidDateFormatter
import com.maksimowiczm.foodyou.common.compose.utility.DateFormatterProvider
import java.util.Locale
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "de-rDE-w414dp-h480dp-hdpi", application = Application::class)
@OptIn(ExperimentalRoborazziApi::class)
class CalendarCardScreenshotTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var activity: ActivityController<ComponentActivity>? = null
    private lateinit var calendarState: CalendarState
    private val today = LocalDate(2026, 7, 30)

    @After
    fun tearDown() {
        activity?.close()
        stopKoin()
    }

    @Test
    fun todayShortcut() {
        showCalendar(selectedDate = LocalDate(2026, 6, 15))
        compose.onNode(isRoot()).captureRoboImage(
            "CalendarCardScreenshotTest.today-shortcut.png"
        )

        compose.onNodeWithContentDescription("Gehe zu heute").performTouchInput { click() }

        compose.onNode(isDialog()).assertDoesNotExist()
        compose.onNodeWithText("Juli 2026").assertExists()
        compose.runOnIdle {
            assertEquals(today, calendarState.selectedDate)
            assertEquals(calendarState.weekRange.pageFor(today), calendarState.pagerState.currentPage)
        }
    }

    @Test
    fun todayShortcutReturnsToTodaysWeekEvenIfTodayIsAlreadySelected() {
        showCalendar(selectedDate = today)
        compose.runOnIdle {
            runBlocking {
                calendarState.pagerState.scrollToPage(
                    calendarState.weekRange.pageFor(LocalDate(2026, 9, 15))
                )
            }
        }
        compose.onNodeWithText("September 2026").assertExists()

        compose.onNodeWithContentDescription("Gehe zu heute").performTouchInput { click() }

        compose.onNode(isDialog()).assertDoesNotExist()
        compose.onNodeWithText("Juli 2026").assertExists()
        compose.runOnIdle {
            assertEquals(today, calendarState.selectedDate)
            assertEquals(calendarState.weekRange.pageFor(today), calendarState.pagerState.currentPage)
        }
    }

    @Test
    fun monthNameStillOpensCalendar() {
        val selectedDate = LocalDate(2026, 6, 15)
        showCalendar(selectedDate)

        compose.onNodeWithText("Juni 2026").performTouchInput { click() }

        compose.onNode(isDialog()).assertExists()
        compose.runOnIdle { assertEquals(selectedDate, calendarState.selectedDate) }
    }

    private fun showCalendar(selectedDate: LocalDate) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        activity = controller
        val formatter = AndroidDateFormatter(controller.get()) { Locale.GERMANY }
        controller.get().setContent {
            DateFormatterProvider(formatter) {
                GoldenSurface(height = 150) {
                    calendarState = rememberCalendarState(
                        namesOfDayOfWeek = formatter.weekDayNamesShort,
                        referenceDate = today,
                        selectedDate = selectedDate,
                    )
                    CalendarCard(
                        calendarState = calendarState,
                        lockedDays = emptyList(),
                        defaultLockedDaySurplusKcal = 500.0,
                        onLockDay = { _, _ -> },
                        onUnlockDay = {},
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun lockedDayMarker() {
        captureRoboImage(
            filePath = "CalendarCardScreenshotTest.locked-day-marker.png",
            roborazziComposeOptions = screenshotOptions(height = 150),
        ) {
            GoldenSurface(height = 150) {
                val date = LocalDate(2026, 7, 30)
                LockedCalendarDatePreview(date = date, modifier = Modifier.padding(8.dp))
            }
        }
    }

    @Test
    fun lockedDayDialog() {
        captureRoboImage(
            filePath = "CalendarCardScreenshotTest.locked-day-dialog.png",
            roborazziComposeOptions = screenshotOptions(height = 480),
        ) {
            GoldenSurface(height = 480) {
                LockedDayDialog(
                    date = LocalDate(2026, 7, 30),
                    currentSurplusKcal = 500.0,
                    defaultSurplusKcal = 500.0,
                    onDismiss = {},
                    onSave = {},
                    onUnlock = {},
                )
            }
        }
    }

    @Composable
    private fun GoldenSurface(height: Int, content: @Composable () -> Unit) {
        EnergyFormatterProvider(EnergyFormatter.kilocalories) {
            MaterialTheme {
                Box(
                    Modifier.requiredSize(414.dp, height.dp).background(Color(0xFFEEF5FA))
                ) {
                    content()
                }
            }
        }
    }

    private fun screenshotOptions(height: Int) =
        RoborazziComposeOptions.Builder().size(widthDp = 414, heightDp = height)
            .locale("de-rDE")
            .build()
}

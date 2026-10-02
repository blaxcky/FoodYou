package com.maksimowiczm.foodyou.app.ui.weight

import android.app.Application
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.maksimowiczm.foodyou.weight.domain.entity.DailyWeightEntry
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "de-rDE-w390dp-h844dp-mdpi", application = Application::class)
class WeightReportScreenScreenshotTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ActivityController<ComponentActivity>
    private lateinit var state: MutableState<WeightReportUiState>
    private val savedWeights = mutableListOf<Double>()
    private val toggledHidden = mutableListOf<Pair<String, Boolean>>()

    @After
    fun tearDown() {
        if (::activity.isInitialized) activity.close()
        stopKoin()
    }

    private fun show(reportState: WeightReportUiState = reportState(), fontScale: Float = 1f) {
        RuntimeEnvironment.setFontScale(fontScale)
        state = mutableStateOf(reportState)
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        setActivityContent()
        compose.waitForIdle()
    }

    private fun setActivityContent() {
        activity.get().setContent { WeightReportGolden() }
    }

    private fun dialogWeight(text: String) =
        compose.onNode(hasText(text) and hasAnyAncestor(isDialog()))

    private fun openDialog() {
        compose.onNodeWithText("Neues Gewicht eintragen").performClick()
        compose.onNode(isDialog()).assertExists()
    }

    private fun plus() = compose.onNodeWithContentDescription("0,1 kg addieren")
    private fun minus() = compose.onNodeWithContentDescription("0,1 kg abziehen")

    @Test
    fun weightEntryAndHistory() {
        show()
        compose.onNode(isRoot()).captureRoboImage(
            "WeightReportScreenScreenshotTest.weight-entry-history.png"
        )
    }

    @Test
    fun adjustmentsOnlySaveOnConfirmation() {
        show()
        plus().assertDoesNotExist()
        minus().assertDoesNotExist()
        openDialog()
        dialogWeight("101,3 kg").assertIsDisplayed()
        plus().performClick()
        plus().performClick()
        minus().performClick()
        dialogWeight("101,4 kg").assertIsDisplayed()
        assertTrue(savedWeights.isEmpty())
        compose.onNode(hasText("Dein Gewicht") and hasText("101,3 kg")).assertExists()
        compose.onNode(isDialog()).captureRoboImage(
            "WeightReportScreenScreenshotTest.weight-entry-dialog.png"
        )

        compose.onNodeWithText("Übernehmen").performClick()

        compose.onNode(isDialog()).assertDoesNotExist()
        assertEquals(listOf(101.4), savedWeights)
    }

    @Test
    fun rapidConfirmationOnlySavesOnce() {
        show()
        openDialog()
        plus().performClick()
        val confirm = compose.onNodeWithText("Übernehmen")
            .fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        compose.runOnIdle {
            assertTrue(confirm())
            assertTrue(confirm())
        }
        compose.onNode(isDialog()).assertDoesNotExist()
        assertEquals(listOf(101.4), savedWeights)
    }

    @Test
    fun cancelDiscardsDraftAndReopeningUsesSavedWeight() {
        show()
        openDialog()
        plus().performClick()
        compose.onNodeWithText("Abbrechen").performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
        assertTrue(savedWeights.isEmpty())

        openDialog()
        dialogWeight("101,3 kg").assertIsDisplayed()
    }

    @Test
    fun backAndOutsideTapDiscardDraft() {
        show()
        openDialog()
        plus().performClick()
        compose.runOnIdle {
            val dialog = ShadowDialog.getLatestDialog() as ComponentDialog
            dialog.onBackPressedDispatcher.onBackPressed()
        }
        compose.onNode(isDialog()).assertDoesNotExist()
        assertTrue(savedWeights.isEmpty())

        openDialog()
        dialogWeight("101,3 kg").assertIsDisplayed()
        minus().performClick()
        compose.runOnIdle {
            val dialog = ShadowDialog.getLatestDialog()
            listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
                val event = MotionEvent.obtain(0, 0, action, -100f, -100f, 0)
                try {
                    dialog.onTouchEvent(event)
                } finally {
                    event.recycle()
                }
            }
        }
        compose.onNode(isDialog()).assertDoesNotExist()
        assertTrue(savedWeights.isEmpty())
        openDialog()
        dialogWeight("101,3 kg").assertIsDisplayed()
    }

    @Test
    fun noisyHistoryAboveStartWeight() {
        show(noisyReportState())
        compose.onNode(hasText("Dein Gewicht") and hasText("Mo, 28. Sep.")).assertExists()
        compose.onNodeWithText("noch 17,4 kg").assertExists()
        compose.onNode(isRoot()).captureRoboImage(
            "WeightReportScreenScreenshotTest.noisy-history-above-start.png"
        )

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Alle 31 Werte anzeigen"))
        compose.onNode(isRoot()).captureRoboImage(
            "WeightReportScreenScreenshotTest.noisy-history-list.png"
        )
    }

    @Test
    fun rangeSelectionUpdatesSubtitle() {
        show()
        compose.onNodeWithText("1 Jahr").assertExists()
        compose.onNodeWithText("3M").performClick()
        compose.onNodeWithText("3 Monate").assertExists()
        compose.onNodeWithText("1 Jahr").assertDoesNotExist()
    }

    @Test
    fun historyRowMenuHidesEntry() {
        show()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("So, 7. Juni"))
        compose.onNodeWithText("So, 7. Juni").performClick()
        compose.onNodeWithText("Ausblenden").performClick()
        assertEquals(listOf("local:${LocalDate.parse("2026-06-07").toEpochDays()}" to true), toggledHidden)
    }

    @Test
    fun scrollingStartingOnEntryButtonDoesNotOpenOrSave() {
        show()
        compose.onNodeWithText("Neues Gewicht eintragen").performTouchInput {
            swipe(start = center, end = center.copy(y = center.y - 200f))
        }
        compose.onNode(isDialog()).assertDoesNotExist()
        assertTrue(savedWeights.isEmpty())
    }

    @Test
    fun todayWeightTakesPriorityOverSuggestion() {
        show(reportState().copy(todayWeightKg = 100.2, suggestedWeightKg = 101.3))
        openDialog()
        dialogWeight("100,2 kg").assertIsDisplayed()
        assertTrue(savedWeights.isEmpty())
    }

    @Test
    fun lastKnownWeightIsUsedWhenTodayHasNoEntry() {
        show(reportState().copy(todayWeightKg = null, suggestedWeightKg = 99.84))
        openDialog()
        dialogWeight("99,8 kg").assertIsDisplayed()
        assertTrue(savedWeights.isEmpty())
    }

    @Test
    fun missingWeightDisablesEntry() {
        show(WeightReportUiState())
        compose.onNodeWithText("Neues Gewicht eintragen").assertIsNotEnabled()
        compose.onNode(isDialog()).assertDoesNotExist()
        assertTrue(savedWeights.isEmpty())
    }

    @Test
    fun minimumWeightDisablesMinus() {
        show(reportState().copy(todayWeightKg = 0.2))
        openDialog()
        minus().performClick()
        compose.onAllNodes(hasText("0,1 kg") and hasAnyAncestor(isDialog())).assertCountEquals(2)
        minus().assertIsNotEnabled()
        assertTrue(savedWeights.isEmpty())
        plus().performClick()
        dialogWeight("0,2 kg").assertIsDisplayed()
        minus().assertIsEnabled()
        compose.onNodeWithText("Übernehmen").performClick()
        assertEquals(listOf(0.2), savedWeights)
    }

    @Test
    fun holdingPlusRepeatsWithoutSaving() {
        show()
        openDialog()
        compose.mainClock.autoAdvance = false
        plus().performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1000)
        plus().performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertTrue(savedWeights.isEmpty())
        compose.onNodeWithText("Übernehmen").performClick()
        assertEquals(1, savedWeights.size)
        assertTrue(savedWeights.single() >= 101.5)
    }

    @Test
    fun draftSurvivesActivityRecreation() {
        show()
        openDialog()
        plus().performClick()
        compose.runOnIdle {
            activity.recreate()
            setActivityContent()
        }
        compose.waitForIdle()
        compose.onNode(isDialog()).assertExists()
        dialogWeight("101,4 kg").assertIsDisplayed()
        assertTrue(savedWeights.isEmpty())
        compose.onNodeWithText("Übernehmen").performClick()
        assertEquals(listOf(101.4), savedWeights)
    }

    @Test
    fun incomingWeightDoesNotReplaceOpenDraft() {
        show()
        openDialog()
        plus().performClick()
        compose.runOnIdle { state.value = state.value.copy(todayWeightKg = 98.0) }
        dialogWeight("101,4 kg").assertIsDisplayed()
        assertTrue(savedWeights.isEmpty())
        compose.onNodeWithText("Abbrechen").performClick()
        openDialog()
        dialogWeight("98,0 kg").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "de-rDE-w320dp-h844dp-mdpi")
    fun dialogFitsNarrowScreenWithLargeFont() {
        show(fontScale = 1.5f)
        openDialog()
        dialogWeight("101,3 kg").assertIsDisplayed()
        plus().assertIsDisplayed()
        minus().assertIsDisplayed()
        compose.onNodeWithText("Übernehmen").assertIsDisplayed()
        compose.onNodeWithText("Abbrechen").assertIsDisplayed()
    }

    @Composable
    private fun WeightReportGolden() {
        MaterialTheme {
            Box(
                modifier =
                    Modifier.requiredSize(width = 390.dp, height = 844.dp)
                        .background(Color(0xFFEEF5FA))
            ) {
                WeightReportContent(
                    state = state.value,
                    onBack = {},
                    onSaveWeight = { savedWeights += it },
                    onHealthConnectClick = {},
                    onToggleHidden = { id, hidden -> toggledHidden += id to hidden },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    private companion object {
        fun reportState() =
            WeightReportUiState(
                entries = WeightEntries,
                hiddenEntries = listOf(
                    weight("2025-08-08", 106.0, "2025-08-08T05:30:00Z").copy(
                        id = "hc:hidden",
                        isFoodYouRecord = false,
                        sourcePackageName = "com.fitbit.FitbitMobile",
                        sourceDeviceType = 3,
                        isHidden = true,
                    )
                ),
                today = LocalDate.parse("2026-06-07"),
                todayWeightKg = 101.3,
                suggestedWeightKg = 101.3,
                startWeightKg = 105.0,
                currentWeightKg = 101.3,
                targetWeightKg = 90.0,
                heightCm = 188.0,
            )

        fun noisyReportState(): WeightReportUiState {
            val entries =
                listOf(
                    "2026-04-01" to 97.8, "2026-04-24" to 102.6, "2026-04-25" to 99.5,
                    "2026-05-11" to 100.4, "2026-05-26" to 102.8, "2026-05-30" to 102.6,
                    "2026-06-03" to 99.5, "2026-06-07" to 99.4, "2026-06-24" to 103.4,
                    "2026-06-27" to 103.4, "2026-06-29" to 103.0, "2026-06-30" to 100.0,
                    "2026-07-05" to 99.7, "2026-07-23" to 103.8, "2026-07-27" to 104.0,
                    "2026-08-04" to 105.6, "2026-08-23" to 104.9, "2026-08-26" to 104.5,
                    "2026-08-28" to 104.8, "2026-09-02" to 103.2, "2026-09-03" to 102.3,
                    "2026-09-12" to 104.7, "2026-09-14" to 105.4, "2026-09-19" to 104.2,
                    "2026-09-21" to 103.8, "2026-09-22" to 103.8, "2026-09-24" to 103.7,
                    "2026-09-25" to 102.6, "2026-09-26" to 102.1, "2026-09-27" to 102.1,
                    "2026-09-28" to 102.4,
                )
                    .map { (date, weightKg) -> weight(date, weightKg, "${date}T06:30:00Z") }
                    .sortedByDescending { it.date }
            return WeightReportUiState(
                entries = entries,
                today = LocalDate.parse("2026-10-02"),
                todayWeightKg = null,
                suggestedWeightKg = 102.4,
                startWeightKg = 97.8,
                currentWeightKg = 102.4,
                targetWeightKg = 85.0,
                heightCm = 186.0,
            )
        }

        val WeightEntries =
            listOf(
                weight("2026-06-07", 101.3, "2026-06-07T06:30:00Z"),
                weight("2026-04-16", 101.8, "2026-04-16T06:25:00Z"),
                weight("2026-02-18", 102.1, "2026-02-18T06:25:00Z"),
                weight("2025-12-12", 102.8, "2025-12-12T06:25:00Z"),
                weight("2025-10-20", 103.7, "2025-10-20T06:25:00Z"),
                weight("2025-08-08", 104.4, "2025-08-08T06:35:00Z").copy(
                    id = "hc:fitbit",
                    isFoodYouRecord = false,
                    sourcePackageName = "com.fitbit.FitbitMobile",
                    sourceDeviceType = 3,
                ),
                weight("2025-08-07", 105.0, "2025-08-07T06:20:00Z"),
            )

        fun weight(date: String, weightKg: Double, measuredAt: String) =
            DailyWeightEntry(
                date = LocalDate.parse(date),
                weightKg = weightKg,
                measuredAt = Instant.parse(measuredAt),
                healthConnectRecordId = null,
                isFoodYouRecord = true,
            )
    }
}

package com.maksimowiczm.foodyou.app.ui.food.diary.quickadd

import android.app.Application
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.maksimowiczm.foodyou.app.ui.common.utility.EnergyFormatter
import com.maksimowiczm.foodyou.app.ui.common.utility.LocalEnergyFormatter
import com.maksimowiczm.foodyou.common.infrastructure.csv.CsvParserImpl
import foodyou.app.generated.resources.Res
import foodyou.app.generated.resources.example_quick_add_csv
import org.jetbrains.compose.resources.stringResource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "de-rDE-w390dp-h844dp-mdpi", application = Application::class)
class QuickAddFormScreenshotTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ActivityController<ComponentActivity>

    private lateinit var state: QuickAddFormState
    private lateinit var csvExample: String

    @After
    fun tearDown() {
        if (::activity.isInitialized) activity.close()
    }

    private fun show(fontScale: Float = 1f, kilojoules: Boolean = false) {
        RuntimeEnvironment.setFontScale(fontScale)
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        activity.get().setContent {
            CompositionLocalProvider(
                LocalEnergyFormatter provides
                    if (kilojoules) EnergyFormatter.kilojoules else EnergyFormatter.kilocalories,
            ) {
                MaterialTheme {
                    csvExample = stringResource(Res.string.example_quick_add_csv)
                    state = rememberQuickAddFormState(
                        name = "Joghurt",
                        proteins = 10.0,
                        carbohydrates = 20.0,
                        fats = 3.5,
                        quickAddCsvParser = QuickAddCsvParserImpl(CsvParserImpl()),
                    )
                    Surface(Modifier.fillMaxSize()) {
                        QuickAddForm(
                            state,
                            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun capture(name: String, dialog: Boolean = false) {
        compose.onNode(if (dialog) isDialog() else isRoot())
            .captureRoboImage("QuickAddFormScreenshotTest.$name.png")
    }

    private fun values(): List<String> = listOf(
        state.name.textFieldState.text.toString(),
        state.energy.textFieldState.text.toString(),
        state.proteins.textFieldState.text.toString(),
        state.carbohydrates.textFieldState.text.toString(),
        state.fats.textFieldState.text.toString(),
        state.csvTextFieldState.text.toString(),
        state.autoCalculateEnergy.toString(),
    )

    @Test
    fun compactForm() {
        show()
        compose.onNodeWithText("Automatisch aus Makros").assertIsDisplayed()
        capture("compact")
    }

    @Test
    fun csvHelpPreservesInputsAndError() {
        show()
        compose.onNodeWithText("CSV-Import").performTextInput("invalid CSV")
        compose.onNodeWithText("Übernehmen").performClick()
        compose.waitForIdle()
        val before = values()
        val error = state.csvError
        assertEquals(QuickAddCsvError.InvalidHeader, error)
        compose.onNodeWithContentDescription("Hilfe zum CSV-Import anzeigen").performClick()
        compose.onNodeWithText("CSV importieren").assertIsDisplayed()
        capture("csv-help", dialog = true)
        compose.onNodeWithText("Verstanden").performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(before, values())
            assertEquals(error, state.csvError)
        }
        compose.onNodeWithText("CSV header must be:", substring = true).assertIsDisplayed()
    }

    @Test
    fun energyHelpAndModeSwitch() {
        show()
        compose.onNodeWithContentDescription("Energie manuell eingeben").performClick()
        compose.onNodeWithText("Manuelle Eingabe").assertIsDisplayed()
        compose.onNodeWithText("Brennwert in Kalorien", substring = false).performTextClearance()
        compose.onNodeWithText("Brennwert in Kalorien", substring = false).performTextInput("200")
        compose.waitForIdle()
        val before = values()
        compose.onNodeWithContentDescription("Hilfe zur Energieberechnung anzeigen").performClick()
        capture("energy-help", dialog = true)
        compose.onNodeWithText("Verstanden").performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.runOnIdle { assertEquals(before, values()) }
        compose.onNodeWithContentDescription("Energie automatisch berechnen").performClick()
        compose.onNodeWithText("Automatisch aus Makros").assertIsDisplayed()
        compose.runOnIdle { assertEquals("151.5", state.energy.textFieldState.text.toString()) }
    }

    @Test
    @Config(qualifiers = "de-rDE-w320dp-h640dp-mdpi")
    fun narrowLargeFont() {
        show(fontScale = 1.6f)
        capture("large-font")
        compose.onNodeWithContentDescription("Hilfe zum CSV-Import anzeigen")
            .performScrollTo()
            .performClick()
        capture("csv-help-large-font", dialog = true)
        compose.onNodeWithText("Verstanden").performClick()
        compose.onNodeWithContentDescription("Hilfe zur Energieberechnung anzeigen")
            .performScrollTo()
            .performClick()
        capture("energy-help-large-font", dialog = true)
        compose.onNodeWithText("Fette 9 kcal/g", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Verstanden").performClick()
        compose.onNodeWithText("Fette").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun bothDialogsDismissOnBackAndOutsideTap() {
        show()
        val before = values()
        listOf("Hilfe zum CSV-Import anzeigen", "Hilfe zur Energieberechnung anzeigen").forEach { help ->
            compose.onNodeWithContentDescription(help).performClick()
            compose.runOnIdle {
                val dialog = ShadowDialog.getLatestDialog()
                (dialog as ComponentDialog).onBackPressedDispatcher.onBackPressed()
            }
            compose.onNode(isDialog()).assertDoesNotExist()
            compose.onNodeWithContentDescription(help).performClick()
            compose.runOnIdle {
                val dialog = ShadowDialog.getLatestDialog()
                listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)
                    .forEach { action ->
                        val event = MotionEvent.obtain(0, 0, action, -100f, -100f, 0)
                        try {
                            dialog.onTouchEvent(event)
                        } finally {
                            event.recycle()
                        }
                    }
            }
            compose.onNode(isDialog()).assertDoesNotExist()
            compose.runOnIdle { assertEquals(before, values()) }
        }
    }

    @Test
    fun csvHelpExampleCanBeImported() {
        show()
        compose.onNodeWithText("CSV-Import").performTextInput(csvExample)
        compose.onNodeWithText("Übernehmen").performClick()
        compose.onNodeWithText("Manuelle Eingabe").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(null, state.csvError)
            assertEquals("Joghurt", state.name.textFieldState.text.toString())
            assertEquals("150", state.energy.textFieldState.text.toString())
            assertEquals("10", state.proteins.textFieldState.text.toString())
            assertEquals("20", state.carbohydrates.textFieldState.text.toString())
            assertEquals("3.5", state.fats.textFieldState.text.toString())
        }
    }

    @Test
    fun energyHelpUsesSelectedUnit() {
        show(kilojoules = true)
        compose.onNodeWithContentDescription("Hilfe zur Energieberechnung anzeigen").performClick()
        compose.onNodeWithText("17 kJ", substring = true).assertExists()
        compose.onNodeWithText("37 kJ", substring = true).assertExists()
    }
}

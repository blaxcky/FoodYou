package com.maksimowiczm.foodyou.app.ui.food.diary.add

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.maksimowiczm.foodyou.app.ui.common.utility.ServingUnit
import com.maksimowiczm.foodyou.app.ui.food.component.MeasurementPickerState
import com.maksimowiczm.foodyou.app.ui.food.component.rememberMeasurementPickerState
import com.maksimowiczm.foodyou.app.ui.food.component.toMeasurementPickerOptions
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.measurement.MeasurementType
import com.maksimowiczm.foodyou.food.domain.entity.ProductPortion
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "de-rDE-w390dp-h844dp-mdpi", application = Application::class)
class PortionListAmountPickerScreenshotTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ActivityController<ComponentActivity>
    private lateinit var pickerState: MeasurementPickerState
    private val portions = mutableStateOf(defaultPortions)

    @After
    fun tearDown() {
        if (::activity.isInitialized) activity.close()
    }

    private fun show(selected: Measurement = Measurement.Gram(150.0)) {
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        activity.get().setContent {
            val currentPortions by portions
            MaterialTheme {
                Surface(Modifier.testTag(ROOT)) {
                    val state =
                        rememberMeasurementPickerState(
                            suggestions = emptyList(),
                            portionOptions =
                                currentPortions.toMeasurementPickerOptions(isLiquid = false),
                            totalWeight = 500.0,
                            servingWeight = 190.0,
                            isLiquid = false,
                            possibleTypes =
                                listOf(
                                    MeasurementType.Gram,
                                    MeasurementType.Package,
                                    MeasurementType.Serving,
                                ),
                            selectedMeasurement = selected,
                        )
                    pickerState = state
                    Column(Modifier.padding(16.dp)) {
                        PortionListAmountInput(state = state, servingUnit = ServingUnit.Piece)
                        PortionListOptions(
                            state = state,
                            servingUnit = ServingUnit.Piece,
                            portions = currentPortions,
                            onSavePortions = { portions.value = it },
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun input() = compose.onNode(hasSetTextAction())

    private fun capture(name: String) =
        compose.onNodeWithTag(ROOT).captureRoboImage("PortionListAmountPickerScreenshotTest.$name.png")

    @Test
    fun prefilledAmountMatchingPortionSelectsIt() {
        show()
        input().assertTextContains("1")
        compose.onNodeWithText("× mittelgroße = 150 g").assertExists()
        assertEquals(Measurement.Gram(150.0), pickerState.measurement)
        capture("portion-preselected")
    }

    @Test
    fun selectingRowsConvertsOrResetsAmount() {
        show()
        compose.onNodeWithText("Gramm").performClick()
        input().assertTextContains("150")
        compose.onNodeWithText("halbe").performClick()
        input().assertTextContains("1")
        compose.onNodeWithText("× halbe = 75 g").assertExists()
        compose.onNodeWithText("Stück").performClick()
        compose.onNodeWithText("× Stück = 190 g").assertExists()
        assertEquals(Measurement.Serving(1.0), pickerState.measurement)
    }

    @Test
    fun longPressEditsSelectedPortionAndKeepsSelection() {
        show()
        compose.onNodeWithText("mittelgroße").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNode(isDialog()).captureRoboImage("PortionListAmountPickerScreenshotTest.edit-dialog.png")
        compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(isDialog()))[0]
            .performTextReplacement("groß")
        compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(isDialog()))[1]
            .performTextReplacement("180")
        compose.onNodeWithText("Speichern").performClick()
        compose.waitForIdle()

        assertEquals(
            listOf(
                ProductPortion("groß", 180.0, ProductPortion.Unit.Gram),
                ProductPortion("halbe", 75.0, ProductPortion.Unit.Gram),
            ),
            portions.value,
        )
        compose.onNodeWithText("× groß = 180 g").assertExists()
        assertEquals(Measurement.Gram(180.0), pickerState.measurement)
    }

    @Test
    fun deletingSelectedPortionFallsBackToGrams() {
        show()
        compose.onNodeWithText("mittelgroße").performTouchInput { longClick() }
        compose.onNodeWithText("Löschen").performClick()
        compose.waitForIdle()

        assertEquals(
            listOf(ProductPortion("halbe", 75.0, ProductPortion.Unit.Gram)),
            portions.value,
        )
        input().assertTextContains("150")
        assertEquals(Measurement.Gram(150.0), pickerState.measurement)
    }

    @Test
    fun manyPortionsAreCollapsed() {
        portions.value =
            listOf("Scheibe", "Stück", "Hand voll", "Becher", "Teller", "Schale", "Prise").mapIndexed {
                index,
                label ->
                ProductPortion(label, 10.0 * (index + 1), ProductPortion.Unit.Gram)
            }
        show(selected = Measurement.Gram(100.0))
        compose.onNodeWithText("Alle anzeigen (10)").assertExists()
        compose.onNodeWithText("Prise").assertDoesNotExist()
        capture("collapsed")
        compose.onNodeWithText("Alle anzeigen (10)").performClick()
        compose.onNodeWithText("Prise").assertExists()
    }

    private companion object {
        const val ROOT = "root"
        val defaultPortions =
            listOf(
                ProductPortion("mittelgroße", 150.0, ProductPortion.Unit.Gram),
                ProductPortion("halbe", 75.0, ProductPortion.Unit.Gram),
            )
    }
}

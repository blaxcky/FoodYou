package com.maksimowiczm.foodyou.app.ui.home.meals.card

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.text.TextRange
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.food.domain.entity.ProductPortion
import com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntryId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "de-rDE-w390dp-h844dp-mdpi", application = Application::class)
class AddToEntryDialogScreenshotTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ActivityController<ComponentActivity>
    private var addition: EntryAddition? = null
    private var dismissed = false

    @After
    fun tearDown() {
        if (::activity.isInitialized) activity.close()
    }

    private fun show(entry: FoodMealEntryModel = food(), fontScale: Float = 1f) {
        RuntimeEnvironment.setFontScale(fontScale)
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        activity.get().setContent {
            MaterialTheme {
                AddToEntryDialog(entry, { dismissed = true }, { addition = it })
            }
        }
        compose.waitForIdle()
    }

    private fun input() = compose.onNode(hasSetTextAction())
    private fun capture(name: String) = compose.onNode(isDialog())
        .captureRoboImage("AddToEntryDialogScreenshotTest.$name.png")
    private fun openUnits() {
        compose.onNode(hasClickAction() and hasText("g", substring = false)).performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
    }

    @Test
    fun gramsPreselectedAndSelectedForReplacement() {
        show()
        input().assertTextContains("100")
        assertEquals(TextRange(0, 3), input().fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
        capture("grams")
        compose.onNodeWithText("Hinzufügen").performClick()
        assertEquals(EntryAddition.Food(Measurement.Gram(100.0)), addition)
    }

    @Test
    fun piecesConvertAndAcceptDecimalComma() {
        show()
        openUnits()
        compose.onNodeWithText("Stück (25 g)").performClick()
        input().assertTextContains("4")
        input().performTextReplacement("1,5")
        capture("pieces")
        compose.onNodeWithText("Hinzufügen").performClick()
        assertEquals(EntryAddition.Food(Measurement.Serving(1.5)), addition)
    }

    @Test
    fun portionMenu() {
        show()
        openUnits()
        compose.onNode(isPopup()).captureRoboImage("AddToEntryDialogScreenshotTest.portion-menu.png")
        compose.onNodeWithText("1 Scheibe (30 g)").performClick()
        input().performTextReplacement("2")
        compose.onNodeWithText("Hinzufügen").performClick()
        assertEquals(EntryAddition.Food(Measurement.Gram(60.0)), addition)
    }

    @Test
    fun noPieceWeightKeepsNamedPortions() {
        show(food().copy(servingWeight = null))
        openUnits()
        compose.onNodeWithText("Stück", substring = true).assertDoesNotExist()
        compose.onNodeWithText("1 Scheibe (30 g)").assertExists()
        compose.onNode(isPopup()).captureRoboImage("AddToEntryDialogScreenshotTest.no-piece-weight.png")
    }

    @Test
    @Config(qualifiers = "de-rDE-w320dp-h844dp-mdpi")
    fun narrowWithLargeFont() {
        show(fontScale = 1.5f)
        capture("narrow-large-font")
    }

    @Test
    fun cancelDoesNotAdd() {
        show()
        input().performTextReplacement("42")
        compose.onNodeWithText("Abbrechen").performClick()
        assertTrue(dismissed)
        assertNull(addition)
    }

    @Test
    fun invalidAmountsDisableConfirmation() {
        show()
        for (text in listOf("", "0", "-1", "NaN", "Infinity", "1e309")) {
            input().performTextReplacement(text)
            compose.onNodeWithText("Hinzufügen").assertIsNotEnabled()
        }
        assertNull(addition)
    }

    @Test
    fun existingPiecesArePreselected() {
        show(food().copy(measurement = Measurement.Serving(2.0)))
        input().assertTextContains("2")
        compose.onNodeWithText("Stück (25 g)").assertExists()
    }

    @Test
    fun commaInputIsConvertedOnUnitChange() {
        show()
        input().performTextReplacement("37,5")
        openUnits()
        compose.onNodeWithText("Stück (25 g)").performClick()
        input().assertTextContains("1.5")
    }

    private fun food() = FoodMealEntryModel(
        id = FoodDiaryEntryId(1), mealId = 1, editableProductId = null,
        name = "Brot", energy = 100, proteins = 1.0, carbohydrates = 2.0, fats = 3.0,
        measurement = Measurement.Gram(100.0), weight = 100.0, isLiquid = false,
        isRecipe = false, servingWeight = 25.0, totalWeight = 250.0,
        portions = listOf(ProductPortion(label = "Scheibe", amount = 30.0, unit = ProductPortion.Unit.Gram)),
    )
}

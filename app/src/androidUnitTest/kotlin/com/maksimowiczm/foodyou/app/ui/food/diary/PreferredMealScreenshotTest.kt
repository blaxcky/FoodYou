package com.maksimowiczm.foodyou.app.ui.food.diary

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.maksimowiczm.foodyou.app.ui.food.diary.component.MealSelectionField
import com.maksimowiczm.foodyou.app.ui.home.meals.card.QuickCaptureAmountDialog
import com.maksimowiczm.foodyou.app.ui.home.meals.card.QuickCaptureProductModel
import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.Product
import org.junit.*
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

class PreferredMealTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { content() } }
    }
    companion object { var content: @Composable () -> Unit = {} }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "de-rDE-w390dp-h844dp-mdpi", application = Application::class)
class PreferredMealScreenshotTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ActivityController<PreferredMealTestActivity>
    private val meals = listOf(1L to "Frühstück", 2L to "Mittagessen", 4L to "Snacks")

    @After fun tearDown() {
        if (::activity.isInitialized) activity.close()
        PreferredMealTestActivity.content = {}
    }

    private fun show(content: @Composable () -> Unit) {
        PreferredMealTestActivity.content = content
        activity = Robolectric.buildActivity(PreferredMealTestActivity::class.java).setup()
        compose.waitForIdle()
    }

    @Test fun editorAllowsAutomaticAndSnackPreference() {
        show {
            var selected by rememberSaveable { mutableStateOf<Long?>(null) }
            MealSelectionField(meals, selected, { selected = it }, allowAutomatic = true)
        }
        compose.onNodeWithText("Standardmahlzeit: Automatisch").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.onNode(isPopup()).captureRoboImage("PreferredMealScreenshotTest.editor-menu.png")
        compose.onNodeWithText("Snacks").performClick()
        compose.onNodeWithText("Standardmahlzeit: Snacks").assertExists()
        compose.onRoot().captureRoboImage("PreferredMealScreenshotTest.editor-snacks.png")
        activity.recreate()
        compose.waitForIdle()
        compose.onNodeWithText("Standardmahlzeit: Snacks").performClick()
        compose.onNodeWithText("Automatisch").performClick()
        compose.onNodeWithText("Standardmahlzeit: Automatisch").assertExists()
    }

    @Test fun quickCaptureSeedsSnacksAndKeepsManualOverrideAndAmountAfterRecreation() {
        val domain = Product(
            id = FoodId.Product(1), name = "Nüsse", brand = null, barcode = null,
            note = null, isLiquid = false, packageWeight = null, servingWeight = null,
            source = FoodSource(FoodSource.Type.User), nutritionFacts = NutritionFacts(),
            preferredMealId = 4,
        )
        val product = QuickCaptureProductModel(domain.id, domain.name, null, false, null, null, domain)
        var preferredMeal by mutableStateOf(4L)
        var savedMeal: Long? = null
        var savedMeasurement: Measurement? = null
        show {
            QuickCaptureAmountDialog(
                product = product.copy(product = domain.copy(preferredMealId = preferredMeal)), suggestedMeasurement = Measurement.Gram(100.0),
                onDismissRequest = {}, onSave = { amount, meal ->
                    savedMeal = meal
                    savedMeasurement = amount
                }, meals = meals, requestedMealId = 1,
            )
        }
        compose.onNodeWithText("Mahlzeit: Snacks").assertExists()
        compose.onNode(isDialog()).captureRoboImage("PreferredMealScreenshotTest.quick-capture-snacks.png")
        // Editing the product affects the next new entry, not the already open form.
        compose.runOnIdle { preferredMeal = 2L }
        compose.onNodeWithText("Mahlzeit: Snacks").assertExists()
        compose.onNodeWithText("Mahlzeit: Snacks").performClick()
        compose.onNodeWithText("Mittagessen").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("75")
        activity.recreate()
        compose.waitForIdle()
        compose.onNodeWithText("Mahlzeit: Mittagessen").assertExists()
        compose.onNodeWithText("Speichern").performClick()
        assertEquals(2L, savedMeal)
        assertEquals(Measurement.Gram(75.0), savedMeasurement)
    }
}

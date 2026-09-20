package com.maksimowiczm.foodyou.app.ui.home.meals.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.locale
import com.github.takahirom.roborazzi.size
import com.maksimowiczm.foodyou.fooddiary.domain.entity.MealCardMacro
import com.maksimowiczm.foodyou.fooddiary.domain.entity.MealCardMacroStyle
import com.maksimowiczm.foodyou.fooddiary.domain.entity.MealsCardsLayout
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "de-rDE-w390dp-h844dp-mdpi")
@OptIn(ExperimentalRoborazziApi::class)
class MealsCardsSettingsScreenScreenshotTest {

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun proteinOnly() {
        captureRoboImage(
            filePath = "MealsCardsSettingsScreenScreenshotTest.protein-only.png",
            roborazziComposeOptions =
                RoborazziComposeOptions.Builder().size(390, 844).locale("de-rDE").build(),
        ) {
            MaterialTheme {
                Box(
                    modifier =
                        Modifier.requiredSize(390.dp, 844.dp).background(Color(0xFFEEF5FA))
                ) {
                    MealCardSettings(
                        layout = MealsCardsLayout.Vertical,
                        onLayoutChange = {},
                        useTimeBasedSorting = false,
                        toggleTimeBased = {},
                        ignoreAllDayMeals = false,
                        toggleIgnoreAllDayMeals = {},
                        displayedMacros = setOf(MealCardMacro.Proteins),
                        onMacroDisplayChange = { _, _ -> },
                        showMacrosInFoodEntries = true,
                        macroStyle = MealCardMacroStyle.Icons,
                        onMacroStyleChange = {},
                        toggleShowMacrosInFoodEntries = {},
                        showMealTimes = true,
                        toggleShowMealTimes = {},
                        onMealsSettings = {},
                        onBack = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

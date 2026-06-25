package com.maksimowiczm.foodyou.app.ui.home.meals.card

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.locale
import com.github.takahirom.roborazzi.size
import com.maksimowiczm.foodyou.app.ui.common.theme.LightNutrientsPalette
import com.maksimowiczm.foodyou.app.ui.common.theme.LocalNutrientsPalette
import kotlinx.datetime.LocalTime
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "de-rDE-w390dp-h120dp-mdpi")
@OptIn(ExperimentalRoborazziApi::class)
class MealCardScreenshotTest {

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun actionsRightOfNutrition() {
        captureRoboImage(
            filePath = "MealCardScreenshotTest.actions-right-of-nutrition.png",
            roborazziComposeOptions =
                RoborazziComposeOptions.Builder().size(widthDp = 390, heightDp = 120)
                    .locale("de-rDE")
                    .build(),
        ) {
            CompositionLocalProvider(LocalNutrientsPalette provides LightNutrientsPalette) {
                MaterialTheme {
                    Box(
                        modifier =
                            Modifier.requiredSize(width = 390.dp, height = 120.dp)
                                .background(Color(0xFFEEF5FA))
                                .padding(8.dp)
                    ) {
                        MealCard(
                            meal = EmptyAllDayMeal,
                            onAddFood = {},
                            onQuickAdd = {},
                            onBarcodeScan = {},
                            onEditEntry = {},
                            onEditFood = {},
                            onAddToEntry = { _, _ -> },
                            onDeleteEntry = {},
                            selectedEntries = emptySet(),
                            isSelectionMode = false,
                            onEnterSelection = {},
                            onToggleSelection = {},
                            onLongClick = {},
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    private companion object {
        val EmptyAllDayMeal =
            MealModel(
                id = 1,
                name = "Aktivitaeten",
                from = LocalTime(0, 0),
                to = LocalTime(0, 0),
                isAllDay = true,
                foods = emptyList(),
                energy = 0,
                proteins = 0.0,
                carbohydrates = 0.0,
                fats = 0.0,
            )
    }
}

package com.maksimowiczm.foodyou.app.ui.food.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.locale
import com.github.takahirom.roborazzi.size
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.search.domain.FoodSearch
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "de-rDE-w390dp-h562dp-mdpi")
@OptIn(ExperimentalRoborazziApi::class)
class FoodSearchAppScreenshotTest {

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun stackedSearchKeepsResultsBelowHeader() {
        captureRoboImage(
            filePath = "FoodSearchAppScreenshotTest.stacked-search.png",
            roborazziComposeOptions =
                RoborazziComposeOptions.Builder().size(widthDp = 390, heightDp = 562)
                    .locale("de-rDE")
                    .build(),
        ) {
            MaterialTheme {
                Column(
                    modifier =
                        Modifier.requiredSize(width = 390.dp, height = 562.dp)
                            .background(Color(0xFFEEF5FA))
                ) {
                    Box(
                        modifier =
                            Modifier.fillMaxWidth()
                                .height(180.dp)
                                .background(Color(0xFF8AA2B2))
                    )

                    FoodSearchApp(
                        uiState = SearchUiState,
                        onSearch = {},
                        onSourceChange = {},
                        onFoodClick = { _, _ -> },
                        onUpdateUsdaApiKey = {},
                        onUpdateOpenFoodFactsCredentials = {},
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        layout = FoodSearchLayout.Stacked,
                    )
                }
            }
        }
    }

    private companion object {
        val SearchUiState =
            FoodSearchUiState(
                sources =
                    mapOf(
                        FoodFilter.Source.All to
                            FoodSourceUiState(
                                remoteEnabled = RemoteStatus.LocalOnly,
                                pages = flowOf(PagingData.from((1..12).map(::food))),
                                count = 12,
                                alwaysShowFilter = true,
                            )
                    ),
                filter = FoodFilter(),
                recentSearches = emptyList(),
            )

        fun food(id: Int) =
            FoodSearch.Product(
                id = FoodId.Product(id.toLong()),
                headline = "Lebensmittel $id",
                isLiquid = false,
                nutritionFacts = NutritionFacts.Empty,
                totalWeight = null,
                servingWeight = null,
                suggestedMeasurement = Measurement.Gram(100.0),
            )
    }
}

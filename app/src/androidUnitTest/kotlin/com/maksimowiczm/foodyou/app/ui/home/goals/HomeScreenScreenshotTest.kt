package com.maksimowiczm.foodyou.app.ui.home.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.locale
import com.github.takahirom.roborazzi.size
import com.maksimowiczm.foodyou.app.ui.common.utility.EnergyFormatter
import com.maksimowiczm.foodyou.app.ui.common.utility.EnergyFormatterProvider
import com.maksimowiczm.foodyou.app.ui.home.shared.FoodYouHomeCard
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "de-rDE-w414dp-h924dp-hdpi")
@OptIn(ExperimentalRoborazziApi::class)
class HomeScreenScreenshotTest {

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun largeFont() {
        captureRoboImage(
            filePath = "HomeScreenScreenshotTest.large-font.png",
            roborazziComposeOptions = HomeScreenOptions,
        ) {
            HomeScreenGolden()
        }
    }

    @Composable
    private fun HomeScreenGolden() {
        val density = LocalDensity.current

        CompositionLocalProvider(
            LocalDensity provides Density(density = density.density, fontScale = 1.25f)
        ) {
            EnergyFormatterProvider(EnergyFormatter.kilocalories) {
                MaterialTheme {
                    Column(
                        modifier =
                            Modifier.requiredSize(width = 414.dp, height = 924.dp)
                                .background(Color(0xFFEEF5FA))
                    ) {
                        HomeTitleBar()
                        Column(modifier = Modifier.fillMaxSize()) {
                            FixtureCalendarCard(
                                modifier =
                                    Modifier.padding(horizontal = 8.dp).padding(bottom = 16.dp)
                            )
                            GoalsCard(
                                energy = 0,
                                burnedEnergy = 5,
                                netEnergy = -5,
                                energyGoal = 2100,
                                goalDisplaySummaries = goalDisplaySummaries(energyGoal = 2100),
                                proteins = 0,
                                proteinsGoal = 10,
                                carbohydrates = 0,
                                carbohydratesGoal = 263,
                                fats = 0,
                                fatsGoal = 70,
                                onClick = {},
                                onLongClick = {},
                                modifier =
                                    Modifier.padding(horizontal = 8.dp).padding(bottom = 24.dp)
                                        .fillMaxWidth(),
                            )
                            FixtureMealCard(
                                name = "Fruehstueck",
                                time = "07:00 - 11:00",
                                modifier =
                                    Modifier.padding(horizontal = 8.dp).padding(bottom = 16.dp)
                            )
                            FixtureMealCard(
                                name = "Mittagessen",
                                time = "11:00 - 15:00",
                                modifier =
                                    Modifier.padding(horizontal = 8.dp).padding(bottom = 24.dp)
                            )
                            FixtureActivitiesCard(
                                modifier =
                                    Modifier.padding(horizontal = 8.dp).padding(bottom = 16.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun HomeTitleBar() {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Food You",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                modifier = Modifier.padding(12.dp).size(24.dp),
            )
        }
    }

    @Composable
    private fun FixtureCalendarCard(modifier: Modifier = Modifier) {
        FoodYouHomeCard(modifier = modifier) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Mai 2026",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Icon(imageVector = Icons.Default.CalendarMonth, contentDescription = null)
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf("Mo" to "11", "Di" to "12", "Mi" to "13", "Do" to "14", "Fr" to "15")
                        .forEach { (weekday, day) ->
                            DateCell(
                                weekday = weekday,
                                day = day,
                                selected = day == "14",
                                modifier = Modifier.weight(1f),
                            )
                        }
                }
            }
        }
    }

    @Composable
    private fun DateCell(
        weekday: String,
        day: String,
        selected: Boolean,
        modifier: Modifier = Modifier,
    ) {
        val background =
            if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
        val content =
            if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant

        Column(
            modifier = modifier.background(background, MaterialTheme.shapes.medium).padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = weekday,
                color = content,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
            Text(
                text = day,
                color = content,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }

    @Composable
    private fun FixtureMealCard(name: String, time: String, modifier: Modifier = Modifier) {
        FoodYouHomeCard(modifier = modifier) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = time,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        MealValue(label = "kcal", value = "0")
                        MealValue(label = "P", value = "0")
                        MealValue(label = "KH", value = "0")
                        MealValue(label = "F", value = "0")
                    }
                }
                FilledTonalIconButton(onClick = {}) {
                    Icon(imageVector = Icons.Outlined.Bolt, contentDescription = null)
                }
                FilledIconButton(onClick = {}) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                }
            }
        }
    }

    @Composable
    private fun FixtureActivitiesCard(modifier: Modifier = Modifier) {
        FoodYouHomeCard(modifier = modifier) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(text = "Aktivitaeten", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Keine Aktivitaeten erfasst",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    @Composable
    private fun MealValue(label: String, value: String) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }

    private fun goalDisplaySummaries(energyGoal: Int): List<GoalDisplaySummaryModel> =
        listOf(
            GoalDisplaySummaryModel(
                mode = GoalDisplayMode.Normal,
                energyGoal = energyGoal,
                showEnergyGoalValue = true,
            ),
            GoalDisplaySummaryModel(
                mode = GoalDisplayMode.Optimized,
                energyGoal = energyGoal,
                showEnergyGoalValue = true,
            ),
            GoalDisplaySummaryModel(
                mode = GoalDisplayMode.Diet,
                energyGoal = energyGoal,
                showEnergyGoalValue = true,
            ),
        )

    private companion object {
        val HomeScreenOptions: RoborazziComposeOptions =
            RoborazziComposeOptions.Builder()
                .size(widthDp = 414, heightDp = 924)
                .locale("de-rDE")
                .build()
    }
}

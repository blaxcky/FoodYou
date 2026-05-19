package com.maksimowiczm.foodyou.app.ui.home.meals.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.maksimowiczm.foodyou.app.ui.common.component.FoodErrorListItem
import com.maksimowiczm.foodyou.app.ui.common.theme.LocalNutrientsPalette
import com.maksimowiczm.foodyou.app.ui.common.utility.EnergyFormatter
import com.maksimowiczm.foodyou.app.ui.common.utility.LocalEnergyFormatter
import com.maksimowiczm.foodyou.app.ui.common.utility.LocalNutrientsOrder
import com.maksimowiczm.foodyou.app.ui.common.utility.ServingUnit
import com.maksimowiczm.foodyou.app.ui.common.utility.stringResourceWithWeight
import com.maksimowiczm.foodyou.common.compose.utility.formatClipZeros
import com.maksimowiczm.foodyou.settings.domain.entity.NutrientsOrder
import foodyou.app.generated.resources.*
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MealFoodListItem(
    entry: MealEntryModel,
    color: Color,
    contentColor: Color,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    when (entry) {
        is FoodMealEntryModel ->
            MealFoodListItem(
                entry = entry,
                color = color,
                contentColor = contentColor,
                shape = shape,
                modifier = modifier,
            )

        is ManualMealEntryModel ->
            MealFoodListItem(
                entry = entry,
                color = color,
                contentColor = contentColor,
                shape = shape,
                modifier = modifier,
            )
    }
}

@Composable
internal fun MealFoodListItem(
    entry: FoodMealEntryModel,
    color: Color,
    contentColor: Color,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    val g = stringResource(Res.string.unit_gram_short)
    val energyFormatter = LocalEnergyFormatter.current
    val energySuffix = energyFormatter.suffix()

    val proteinsString = remember(entry.proteins, g) { entry.proteins?.formatGrams(g) }
    val carbohydratesString =
        remember(entry.carbohydrates, g) { entry.carbohydrates?.formatGrams(g) }
    val fatsString = remember(entry.fats, g) { entry.fats?.formatGrams(g) }
    val caloriesString =
        remember(entry.energy, energyFormatter, energySuffix) {
            entry.energy?.let { energyFormatter.formatEnergyString(it, energySuffix) }
        }

    val measurementString =
        entry.measurement.stringResourceWithWeight(
            totalWeight = entry.totalWeight,
            servingWeight = entry.servingWeight,
            isLiquid = entry.isLiquid,
            servingUnit = if (entry.isRecipe) ServingUnit.Serving else ServingUnit.Piece,
        )

    if (measurementString == null) {
        FoodErrorListItem(
            headline = entry.name,
            errorMessage = stringResource(Res.string.error_measurement_error),
            modifier = modifier,
        )
    } else if (
        proteinsString == null ||
            carbohydratesString == null ||
            fatsString == null ||
            caloriesString == null
    ) {
        FoodErrorListItem(
            headline = entry.name,
            errorMessage = stringResource(Res.string.error_food_is_missing_required_fields),
            modifier = modifier,
        )
    } else {
        LightweightMealFoodListItem(
            name = entry.name,
            proteins = proteinsString,
            carbohydrates = carbohydratesString,
            fats = fatsString,
            calories = caloriesString,
            measurement = measurementString,
            isRecipe = entry.isRecipe,
            modifier = modifier,
            containerColor = color,
            contentColor = contentColor,
            shape = shape,
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp),
        )
    }
}

@Composable
internal fun MealFoodListItem(
    entry: ManualMealEntryModel,
    color: Color,
    contentColor: Color,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    val g = stringResource(Res.string.unit_gram_short)
    val energyFormatter = LocalEnergyFormatter.current
    val energySuffix = energyFormatter.suffix()

    val proteinsString = remember(entry.proteins, g) { entry.proteins?.formatGrams(g) }
    val carbohydratesString =
        remember(entry.carbohydrates, g) { entry.carbohydrates?.formatGrams(g) }
    val fatsString = remember(entry.fats, g) { entry.fats?.formatGrams(g) }
    val caloriesString =
        remember(entry.energy, energyFormatter, energySuffix) {
            entry.energy?.let { energyFormatter.formatEnergyString(it, energySuffix) }
        }

    if (
        proteinsString == null ||
            carbohydratesString == null ||
            fatsString == null ||
            caloriesString == null
    ) {
        FoodErrorListItem(
            headline = entry.name,
            errorMessage = stringResource(Res.string.error_food_is_missing_required_fields),
            modifier = modifier,
        )
    } else {
        LightweightMealFoodListItem(
            name = entry.name,
            proteins = proteinsString,
            carbohydrates = carbohydratesString,
            fats = fatsString,
            calories = caloriesString,
            measurement = null,
            isRecipe = false,
            isManual = true,
            modifier = modifier,
            containerColor = color,
            contentColor = contentColor,
            shape = shape,
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp),
        )
    }
}

private fun Double.formatGrams(unit: String): String = formatClipZeros("%.1f") + " $unit"

private fun EnergyFormatter.formatEnergyString(
    energy: Int,
    suffix: String,
): String = fromKcal(energy.toDouble()).roundToInt().toString() + " " + suffix

@Composable
private fun LightweightMealFoodListItem(
    name: String,
    proteins: String,
    carbohydrates: String,
    fats: String,
    calories: String,
    measurement: String?,
    isRecipe: Boolean,
    modifier: Modifier = Modifier,
    isManual: Boolean = false,
    containerColor: Color,
    contentColor: Color,
    shape: Shape,
    contentPadding: PaddingValues,
) {
    val nutrientsPalette = LocalNutrientsPalette.current
    val order = LocalNutrientsOrder.current

    Surface(
        modifier = modifier,
        color = containerColor,
        contentColor = contentColor,
        shape = shape,
    ) {
        Row(
            modifier = Modifier.padding(contentPadding),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CompositionLocalProvider(
                    LocalTextStyle provides MaterialTheme.typography.titleMediumEmphasized
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(name)
                        if (isRecipe) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_skillet_filled),
                                contentDescription = stringResource(Res.string.headline_recipe),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        if (isManual) {
                            Icon(
                                imageVector = Icons.Outlined.Bolt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(text = calories, style = MaterialTheme.typography.bodySmall)

                        order.forEach { field ->
                            when (field) {
                                NutrientsOrder.Proteins ->
                                    CompositionLocalProvider(
                                        LocalContentColor provides
                                            nutrientsPalette.proteinsOnSurfaceContainer
                                    ) {
                                        Text(
                                            text = proteins,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }

                                NutrientsOrder.Fats ->
                                    CompositionLocalProvider(
                                        LocalContentColor provides
                                            nutrientsPalette.fatsOnSurfaceContainer
                                    ) {
                                        Text(text = fats, style = MaterialTheme.typography.bodySmall)
                                    }

                                NutrientsOrder.Carbohydrates ->
                                    CompositionLocalProvider(
                                        LocalContentColor provides
                                            nutrientsPalette.carbohydratesOnSurfaceContainer
                                    ) {
                                        Text(
                                            text = carbohydrates,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }

                                NutrientsOrder.Other,
                                NutrientsOrder.Vitamins,
                                NutrientsOrder.Minerals -> Unit
                            }
                        }
                    }
                }

                if (measurement != null) {
                    Text(text = measurement, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

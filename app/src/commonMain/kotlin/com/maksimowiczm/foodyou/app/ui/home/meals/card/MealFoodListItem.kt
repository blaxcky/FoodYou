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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.maksimowiczm.foodyou.app.ui.common.component.FoodErrorListItem
import com.maksimowiczm.foodyou.app.ui.common.theme.LocalNutrientsPalette
import com.maksimowiczm.foodyou.app.ui.common.utility.EnergyFormatter
import com.maksimowiczm.foodyou.app.ui.common.utility.LocalEnergyFormatter
import com.maksimowiczm.foodyou.app.ui.common.utility.ServingUnit
import com.maksimowiczm.foodyou.app.ui.common.utility.stringResourceWithWeight
import com.maksimowiczm.foodyou.common.compose.utility.formatClipZeros
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
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
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
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
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
    val fatsShort = stringResource(Res.string.nutriment_fats_short)
    val carbohydratesShort = stringResource(Res.string.nutriment_carbohydrates_short)
    val proteinsShort = stringResource(Res.string.nutriment_proteins_short)
    val measurementParts = remember(measurement) { measurement?.splitMeasurementAndWeight() }
    val headline =
        remember(name, measurementParts) {
            measurementParts?.measurement?.let { "$it $name" } ?: name
        }
    val supportingMeasurement = measurementParts?.weight ?: measurement

    Surface(
        modifier = modifier,
        color = containerColor,
        contentColor = contentColor,
        shape = shape,
    ) {
        Row(
            modifier = Modifier.padding(contentPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = headline,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Clip,
                        )
                        if (isRecipe) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_skillet_filled),
                                contentDescription = stringResource(Res.string.headline_recipe),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        if (isManual) {
                            Icon(
                                imageVector = Icons.Outlined.Bolt,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    Text(
                        text = calories,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (supportingMeasurement != null) {
                        Text(
                            text = supportingMeasurement,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = " - ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    MacroText(
                        text = "$fats $fatsShort",
                        color = nutrientsPalette.fatsOnSurfaceContainer,
                    )
                    SeparatorText()
                    MacroText(
                        text = "$carbohydrates $carbohydratesShort",
                        color = nutrientsPalette.carbohydratesOnSurfaceContainer,
                    )
                    SeparatorText()
                    MacroText(
                        text = "$proteins $proteinsShort",
                        color = nutrientsPalette.proteinsOnSurfaceContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun MacroText(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Clip,
    )
}

@Composable
private fun SeparatorText() {
    Text(
        text = ", ",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
    )
}

private data class MeasurementParts(val measurement: String?, val weight: String?)

private fun String.splitMeasurementAndWeight(): MeasurementParts {
    val start = lastIndexOf(" (")
    val end = lastIndexOf(')')
    if (start <= 0 || end != lastIndex) {
        return MeasurementParts(measurement = null, weight = this)
    }

    return MeasurementParts(
        measurement = substring(startIndex = 0, endIndex = start),
        weight = substring(startIndex = start + 2, endIndex = end),
    )
}

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
import com.maksimowiczm.foodyou.fooddiary.domain.entity.MealCardMacro
import foodyou.app.generated.resources.*
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MealFoodListItem(
    entry: MealEntryModel,
    displayedMacros: Set<MealCardMacro> = MealCardMacro.default,
    color: Color,
    contentColor: Color,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    when (entry) {
        is FoodMealEntryModel ->
            MealFoodListItem(
                entry = entry,
                displayedMacros = displayedMacros,
                color = color,
                contentColor = contentColor,
                shape = shape,
                modifier = modifier,
            )

        is ManualMealEntryModel ->
            MealFoodListItem(
                entry = entry,
                displayedMacros = displayedMacros,
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
    displayedMacros: Set<MealCardMacro> = MealCardMacro.default,
    color: Color,
    contentColor: Color,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    val energyFormatter = LocalEnergyFormatter.current
    val energySuffix = energyFormatter.suffix()

    val macros =
        foodMacroSummaries(
            proteins = entry.proteins,
            carbohydrates = entry.carbohydrates,
            fats = entry.fats,
            displayedMacros = displayedMacros,
        )
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
    } else if (macros == null || caloriesString == null) {
        FoodErrorListItem(
            headline = entry.name,
            errorMessage = stringResource(Res.string.error_food_is_missing_required_fields),
            modifier = modifier,
        )
    } else {
        LightweightMealFoodListItem(
            name = entry.name,
            macros = macros,
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
    displayedMacros: Set<MealCardMacro> = MealCardMacro.default,
    color: Color,
    contentColor: Color,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    val energyFormatter = LocalEnergyFormatter.current
    val energySuffix = energyFormatter.suffix()

    val macros =
        foodMacroSummaries(
            proteins = entry.proteins,
            carbohydrates = entry.carbohydrates,
            fats = entry.fats,
            displayedMacros = displayedMacros,
        )
    val caloriesString =
        remember(entry.energy, energyFormatter, energySuffix) {
            entry.energy?.let { energyFormatter.formatEnergyString(it, energySuffix) }
        }

    if (macros == null || caloriesString == null) {
        FoodErrorListItem(
            headline = entry.name,
            errorMessage = stringResource(Res.string.error_food_is_missing_required_fields),
            modifier = modifier,
        )
    } else {
        LightweightMealFoodListItem(
            name = entry.name,
            macros = macros,
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
private fun foodMacroSummaries(
    proteins: Double?,
    carbohydrates: Double?,
    fats: Double?,
    displayedMacros: Set<MealCardMacro>,
): List<FoodMacroSummary>? {
    val gram = stringResource(Res.string.unit_gram_short)
    val nutrientsPalette = LocalNutrientsPalette.current
    val summaries = mutableListOf<FoodMacroSummary>()

    MealCardMacro.entries.forEach { macro ->
        if (macro !in displayedMacros) return@forEach

        val value =
            when (macro) {
                MealCardMacro.Fats -> fats
                MealCardMacro.Carbohydrates -> carbohydrates
                MealCardMacro.Proteins -> proteins
            } ?: return null
        val label =
            when (macro) {
                MealCardMacro.Fats -> stringResource(Res.string.nutriment_fats_short)
                MealCardMacro.Carbohydrates ->
                    stringResource(Res.string.nutriment_carbohydrates_short)
                MealCardMacro.Proteins -> stringResource(Res.string.nutriment_proteins_short)
            }
        val color =
            when (macro) {
                MealCardMacro.Fats -> nutrientsPalette.fatsOnSurfaceContainer
                MealCardMacro.Carbohydrates ->
                    nutrientsPalette.carbohydratesOnSurfaceContainer
                MealCardMacro.Proteins -> nutrientsPalette.proteinsOnSurfaceContainer
            }

        summaries += FoodMacroSummary("${value.formatGrams(gram)} $label", color)
    }

    return summaries
}

private data class FoodMacroSummary(val text: String, val color: Color)

@Composable
private fun LightweightMealFoodListItem(
    name: String,
    macros: List<FoodMacroSummary>,
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
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }

                if (supportingMeasurement != null || macros.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (supportingMeasurement != null) {
                            Text(
                                text = supportingMeasurement,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (macros.isNotEmpty()) {
                                Text(
                                    text = " - ",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                        macros.forEachIndexed { index, macro ->
                            MacroText(text = macro.text, color = macro.color)
                            if (index != macros.lastIndex) SeparatorText()
                        }
                    }
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

package com.maksimowiczm.foodyou.app.ui.food.search

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.common.component.FoodErrorListItem
import com.maksimowiczm.foodyou.app.ui.common.component.FoodListItemSkeleton
import com.maksimowiczm.foodyou.app.ui.common.theme.LocalNutrientsPalette
import com.maksimowiczm.foodyou.app.ui.common.utility.LocalEnergyFormatter
import com.maksimowiczm.foodyou.app.ui.common.utility.LocalNutrientsOrder
import com.maksimowiczm.foodyou.app.ui.common.utility.ServingUnit
import com.maksimowiczm.foodyou.app.ui.common.utility.stringResourceWithWeight
import com.maksimowiczm.foodyou.common.compose.utility.formatLocalized
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.food.domain.entity.Recipe
import com.maksimowiczm.foodyou.food.domain.usecase.ObserveFoodUseCase
import com.maksimowiczm.foodyou.food.search.domain.FoodSearch
import com.maksimowiczm.foodyou.settings.domain.entity.NutrientsOrder
import com.valentinilk.shimmer.Shimmer
import foodyou.app.generated.resources.*
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.mapNotNull
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
internal fun FoodSearchListItem(
    food: FoodSearch.Product,
    measurement: Measurement,
    query: String?,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val weight = food.weight(measurement)
    val factor = weight?.div(100)

    if (factor == null) {
        return FoodErrorListItem(
            headline = food.headline,
            errorMessage = stringResource(Res.string.error_measurement_error),
            modifier = modifier,
            onClick = onClick,
        )
    }

    val measurementFacts = food.nutritionFacts * factor
    val proteins = measurementFacts.proteins.value
    val carbohydrates = measurementFacts.carbohydrates.value
    val fats = measurementFacts.fats.value
    val energy = measurementFacts.energy.value
    val measurementString =
        measurement.stringResourceWithWeight(
            totalWeight = food.totalWeight,
            servingWeight = food.servingWeight,
            isLiquid = food.isLiquid,
            servingUnit = ServingUnit.Piece,
        )

    if (
        proteins == null ||
            carbohydrates == null ||
            fats == null ||
            energy == null ||
            measurementString == null
    ) {
        return FoodErrorListItem(
            headline = food.headline,
            modifier = modifier,
            onClick = onClick,
            errorMessage = stringResource(Res.string.error_food_is_missing_required_fields),
        )
    }

    FoodSearchListItem(
        name = food.name,
        brand = food.brand,
        query = query,
        proteins = proteins,
        carbohydrates = carbohydrates,
        fats = fats,
        energy = energy,
        measurement = measurementString,
        isRecipe = false,
        isFavorite = food.isFavorite,
        onClick = onClick,
        onLongClick = onFavoriteToggle,
        onLongClickLabel =
            stringResource(
                if (food.isFavorite) Res.string.action_remove_from_favorites
                else Res.string.action_mark_as_favorite
            ),
        modifier = modifier,
    )
}

/** Recipe has to be lazy loaded, so we use [ObserveFoodUseCase] to observe the recipe. */
@Composable
internal fun FoodSearchListItem(
    food: FoodSearch.Recipe,
    measurement: Measurement,
    query: String?,
    onClick: () -> Unit,
    shimmer: Shimmer,
    modifier: Modifier = Modifier,
) {
    val observeRecipeUseCase: ObserveFoodUseCase = koinInject()

    val recipe =
        observeRecipeUseCase
            .observe(food.id)
            .mapNotNull { it as? Recipe }
            .collectAsStateWithLifecycle(null)
            .value

    if (recipe == null) {
        return FoodListItemSkeleton(shimmer)
    }

    val factor = recipe.weight(measurement) / 100
    val measurementFacts = recipe.nutritionFacts * factor
    val proteins = measurementFacts.proteins.value
    val carbohydrates = measurementFacts.carbohydrates.value
    val fats = measurementFacts.fats.value
    val energy = measurementFacts.energy.value

    val measurementString =
        measurement.stringResourceWithWeight(
            totalWeight = recipe.totalWeight,
            servingWeight = recipe.servingWeight,
            isLiquid = recipe.isLiquid,
            servingUnit = ServingUnit.Serving,
        )

    if (
        (proteins == null || proteins.isNaN()) ||
            (carbohydrates == null || carbohydrates.isNaN()) ||
            (fats == null || fats.isNaN()) ||
            (energy == null || energy.isNaN()) ||
            measurementString == null
    ) {
        return FoodErrorListItem(
            headline = food.headline,
            modifier = modifier,
            onClick = onClick,
            errorMessage = stringResource(Res.string.error_food_is_missing_required_fields),
        )
    }

    FoodSearchListItem(
        name = food.name,
        brand = null,
        query = query,
        proteins = proteins,
        carbohydrates = carbohydrates,
        fats = fats,
        energy = energy,
        measurement = measurementString,
        isRecipe = true,
        isFavorite = false,
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun FoodSearchListItem(
    name: String,
    brand: String?,
    query: String?,
    proteins: Double,
    carbohydrates: Double,
    fats: Double,
    energy: Double,
    measurement: String,
    isRecipe: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
) {
    val queryTokens = remember(query) { query.highlightTokens() }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    onLongClickLabel = onLongClickLabel,
                )
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = name.highlight(queryTokens),
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isFavorite) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = stringResource(Res.string.headline_favorite),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                if (isRecipe) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_skillet_filled),
                        contentDescription = stringResource(Res.string.headline_recipe),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Text(
                text =
                    buildAnnotatedString {
                        if (brand != null) {
                            append(brand.highlight(queryTokens))
                            append(" · ")
                        }
                        append(measurement)
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            NutrientsRow(
                proteins = proteins,
                carbohydrates = carbohydrates,
                fats = fats,
                energy = energy,
            )
        }

        FilledTonalIconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(Res.string.action_add),
            )
        }
    }
}

@Composable
private fun NutrientsRow(proteins: Double, carbohydrates: Double, fats: Double, energy: Double) {
    val palette = LocalNutrientsPalette.current
    val g = stringResource(Res.string.unit_gram_short)
    val proteinsLabel = stringResource(Res.string.nutriment_proteins_short)
    val carbohydratesLabel = stringResource(Res.string.nutriment_carbohydrates_short)
    val fatsLabel = stringResource(Res.string.nutriment_fats_short)

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = LocalEnergyFormatter.current.formatEnergy(energy.roundToInt()),
            style = MaterialTheme.typography.bodyMedium,
        )

        LocalNutrientsOrder.current.forEach { field ->
            val (label, value, color) =
                when (field) {
                    NutrientsOrder.Proteins ->
                        Triple(proteinsLabel, proteins, palette.proteinsOnSurfaceContainer)
                    NutrientsOrder.Carbohydrates ->
                        Triple(
                            carbohydratesLabel,
                            carbohydrates,
                            palette.carbohydratesOnSurfaceContainer,
                        )
                    NutrientsOrder.Fats -> Triple(fatsLabel, fats, palette.fatsOnSurfaceContainer)
                    NutrientsOrder.Other,
                    NutrientsOrder.Vitamins,
                    NutrientsOrder.Minerals -> return@forEach
                }

            NutrientText(label = label, value = "${value.formatLocalized()} $g", color = color)
        }
    }
}

@Composable
private fun NutrientText(label: String, value: String, color: Color) {
    Text(
        text = "$label $value",
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        maxLines = 1,
    )
}

private val HighlightStyle = SpanStyle(fontWeight = FontWeight.ExtraBold)

private fun String?.highlightTokens(): List<String> =
    this?.split(Regex("\\s+"))?.filter { it.isNotBlank() }.orEmpty()

/** Marks every case-insensitive occurrence of [tokens] in bold. */
internal fun String.highlight(tokens: List<String>): AnnotatedString {
    if (tokens.isEmpty()) return AnnotatedString(this)

    val ranges =
        tokens
            .flatMap { token ->
                generateSequence(indexOf(token, ignoreCase = true).takeIf { it >= 0 }) { start ->
                        indexOf(token, start + token.length, ignoreCase = true).takeIf {
                            it >= 0
                        }
                    }
                    .map { start -> start until start + token.length }
                    .toList()
            }
            .sortedBy { it.first }

    return buildAnnotatedString {
        append(this@highlight)
        var mergedStart = -1
        var mergedEnd = -1
        for (range in ranges) {
            if (range.first <= mergedEnd) {
                mergedEnd = maxOf(mergedEnd, range.last + 1)
            } else {
                if (mergedStart >= 0) addStyle(HighlightStyle, mergedStart, mergedEnd)
                mergedStart = range.first
                mergedEnd = range.last + 1
            }
        }
        if (mergedStart >= 0) addStyle(HighlightStyle, mergedStart, mergedEnd)
    }
}

package com.maksimowiczm.foodyou.app.ui.home.meals.card

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.maksimowiczm.foodyou.app.ui.common.theme.LocalNutrientsPalette
import com.maksimowiczm.foodyou.app.ui.common.utility.LocalEnergyFormatter
import com.maksimowiczm.foodyou.app.ui.home.shared.FoodYouHomeCard
import com.maksimowiczm.foodyou.common.compose.utility.LocalDateFormatter
import com.maksimowiczm.foodyou.common.compose.utility.formatClipZeros
import foodyou.app.generated.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MealCard(
    meal: MealModel,
    onAddFood: () -> Unit,
    onQuickAdd: () -> Unit,
    onEditEntry: (MealEntryModel) -> Unit,
    onDeleteEntry: (MealEntryModel) -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateFormatter = LocalDateFormatter.current
    val enDash = stringResource(Res.string.en_dash)
    val allDayString = stringResource(Res.string.headline_all_day)

    val timeString =
        remember(dateFormatter, meal, enDash, allDayString) {
            if (meal.isAllDay) {
                allDayString
            } else {
                buildString {
                    append(dateFormatter.formatTime(meal.from))
                    append(" $enDash ")
                    append(dateFormatter.formatTime(meal.to))
                }
            }
        }

    FoodYouHomeCard(
        modifier = modifier,
        color = Color.White,
        onClick = onAddFood,
        onLongClick = onLongClick,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = meal.name,
                            modifier = Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        FilledTonalIconButton(
                            onClick = onQuickAdd,
                            modifier = Modifier.size(16.dp),
                            shapes =
                                IconButtonDefaults.shapes(
                                    MaterialTheme.shapes.small,
                                    MaterialTheme.shapes.extraSmall,
                                ),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Bolt,
                                contentDescription =
                                    stringResource(Res.string.headline_quick_add),
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }
                    Text(
                        text = timeString,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                MealNutritionSummary(
                    meal = meal,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            Spacer(Modifier.height(16.dp))

            FoodContainer(
                foods = meal.foods,
                onEditEntry = onEditEntry,
                onDeleteEntry = onDeleteEntry,
                modifier =
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium),
            )

            if (meal.foods.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                FilledIconButton(
                    onClick = onAddFood,
                    shapes =
                        IconButtonDefaults.shapes(
                            MaterialTheme.shapes.medium,
                            MaterialTheme.shapes.extraSmall,
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(Res.string.action_add),
                    )
                }
            }
        }
    }
}

@Composable
private fun MealNutritionSummary(
    meal: MealModel,
    modifier: Modifier = Modifier,
) {
    val nutrientsPalette = LocalNutrientsPalette.current
    val energyFormatter = LocalEnergyFormatter.current
    val gram = stringResource(Res.string.unit_gram_short)

    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        Text(
            text = energyFormatter.formatEnergy(meal.energy, withSuffix = true),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.End,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            val macros =
                listOf(
                    MacroSummary(
                        label = stringResource(Res.string.nutriment_fats_short),
                        value = meal.fats,
                        suffix = gram,
                        color = nutrientsPalette.fatsOnSurfaceContainer,
                    ),
                    MacroSummary(
                        label = stringResource(Res.string.nutriment_carbohydrates_short),
                        value = meal.carbohydrates,
                        suffix = gram,
                        color = nutrientsPalette.carbohydratesOnSurfaceContainer,
                    ),
                    MacroSummary(
                        label = stringResource(Res.string.nutriment_proteins_short),
                        value = meal.proteins,
                        suffix = gram,
                        color = nutrientsPalette.proteinsOnSurfaceContainer,
                    ),
                )

            macros.forEachIndexed { index, macro ->
                MacroSummaryText(macro)
                if (index != macros.lastIndex) {
                    Text(
                        text = ", ",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
        }
    }
}

@Composable
private fun MacroSummaryText(macro: MacroSummary) {
    Text(
        text = "${macro.value.formatClipZeros("%.1f")}${macro.suffix} ${macro.label}",
        color = macro.color,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        overflow = TextOverflow.Clip,
    )
}

private data class MacroSummary(
    val label: String,
    val value: Double,
    val suffix: String,
    val color: Color,
)

@Composable
private fun FoodContainer(
    foods: List<MealEntryModel>,
    onEditEntry: (MealEntryModel) -> Unit,
    onDeleteEntry: (MealEntryModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        HorizontalDivider(Modifier.padding(horizontal = 8.dp))
        foods.forEachIndexed { i, entry ->
            val key =
                remember(entry) {
                    when (entry) {
                        is FoodMealEntryModel -> entry.id.toString()
                        is ManualMealEntryModel -> entry.id.toString()
                    }
                }

            key(key) {
                FoodContainerItem(
                    entry = entry,
                    onEditEntry = onEditEntry,
                    onDeleteEntry = onDeleteEntry,
                    shape = foodItemShape(index = i, lastIndex = foods.lastIndex),
                )
            }
            if (i != foods.lastIndex) {
                HorizontalDivider(Modifier.padding(horizontal = 8.dp))
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = 8.dp))
    }
}

private fun foodItemShape(index: Int, lastIndex: Int): Shape {
    val topRadius = if (index == 0) 12.dp else 0.dp
    val bottomRadius = if (index == lastIndex) 12.dp else 0.dp
    return RoundedCornerShape(topRadius, topRadius, bottomRadius, bottomRadius)
}

@Composable
private fun FoodContainerItem(
    entry: MealEntryModel,
    onEditEntry: (MealEntryModel) -> Unit,
    onDeleteEntry: (MealEntryModel) -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    var showBottomSheet by rememberSaveable { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    if (showBottomSheet) {
        val sheetState = rememberModalBottomSheetState()

        ModalBottomSheet(onDismissRequest = { showBottomSheet = false }, sheetState = sheetState) {
            BottomSheetContent(
                entry = entry,
                onEdit = {
                    coroutineScope.launch {
                        onEditEntry(entry)
                        sheetState.hide()
                        showBottomSheet = false
                    }
                },
                onDelete = {
                    coroutineScope.launch {
                        sheetState.hide()
                        onDeleteEntry(entry)
                        showBottomSheet = false
                    }
                },
            )
        }
    }

    MealFoodListItem(
        entry = entry,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = shape,
        modifier = modifier.clickable { showBottomSheet = true },
    )
}

@Composable
private fun BottomSheetContent(
    entry: MealEntryModel,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    if (showDeleteDialog) {
        DeleteDialog(
            onDismissRequest = { showDeleteDialog = false },
            onDeleteEntry = {
                onDelete()
                showDeleteDialog = false
            },
        )
    }

    Column(modifier = modifier) {
        MealFoodListItem(
            entry = entry,
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RectangleShape,
        )
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        ListItem(
            headlineContent = { Text(stringResource(Res.string.action_edit_entry)) },
            modifier = Modifier.clickable { onEdit() },
            leadingContent = { Icon(imageVector = Icons.Default.Edit, contentDescription = null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        ListItem(
            headlineContent = { Text(stringResource(Res.string.action_delete_entry)) },
            modifier = Modifier.clickable { showDeleteDialog = true },
            leadingContent = {
                Icon(imageVector = Icons.Default.Delete, contentDescription = null)
            },
            colors =
                ListItemDefaults.colors(
                    headlineColor = MaterialTheme.colorScheme.error,
                    leadingIconColor = MaterialTheme.colorScheme.error,
                    containerColor = Color.Transparent,
                ),
        )
    }
}

@Composable
private fun DeleteDialog(onDismissRequest: () -> Unit, onDeleteEntry: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = onDeleteEntry,
                colors =
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(Res.string.action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
        title = { Text(stringResource(Res.string.action_delete_entry)) },
        text = { Text(stringResource(Res.string.description_delete_product_entry)) },
    )
}

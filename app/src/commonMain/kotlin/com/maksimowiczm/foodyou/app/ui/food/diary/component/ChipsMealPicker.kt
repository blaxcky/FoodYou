package com.maksimowiczm.foodyou.app.ui.food.diary.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DinnerDining
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.FreeBreakfast
import androidx.compose.material.icons.filled.LunchDining
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun ChipsMealPicker(state: ChipsMealPickerState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.meals.forEachIndexed { i, meal ->
            MealIconButton(
                selected = meal == state.selectedMeal,
                onClick = { state.selectedMeal = meal },
                icon = mealIcon(i),
                contentDescription = meal,
            )
        }
    }
}

private fun mealIcon(index: Int): ImageVector =
    when (index) {
        0 -> Icons.Filled.FreeBreakfast
        1 -> Icons.Filled.LunchDining
        2 -> Icons.Filled.DinnerDining
        3 -> Icons.Filled.Fastfood
        else -> Icons.Filled.Restaurant
    }

@Composable
private fun MealIconButton(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        colors =
            IconButtonDefaults.iconButtonColors(
                containerColor =
                    if (selected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        Color.Transparent
                    },
                contentColor =
                    if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            ),
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription)
    }
}

@Composable
fun rememberChipsMealPickerState(meals: List<String>, selectedMeal: String?): ChipsMealPickerState {
    return rememberSaveable(
        meals,
        selectedMeal,
        saver =
            Saver(
                save = { listOf(it.selectedMeal, it.meals) },
                restore = {
                    @Suppress("UNCHECKED_CAST")
                    ChipsMealPickerState(
                        initialMeals = it[1] as List<String>,
                        selectedMeal = it[0] as String?,
                    )
                },
            ),
    ) {
        ChipsMealPickerState(initialMeals = meals, selectedMeal = selectedMeal)
    }
}

@Stable
class ChipsMealPickerState(initialMeals: List<String>, selectedMeal: String?) {
    init {
        require(initialMeals.isNotEmpty()) { "Meals list cannot be empty" }
    }

    val meals = initialMeals

    var selectedMeal by mutableStateOf(selectedMeal)
}

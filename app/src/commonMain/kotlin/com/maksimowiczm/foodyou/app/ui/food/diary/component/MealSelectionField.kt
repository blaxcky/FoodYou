package com.maksimowiczm.foodyou.app.ui.food.diary.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** IDs keep preferences stable when meal names or order change. */
@Composable
internal fun MealSelectionField(
    meals: List<Pair<Long, String>>,
    selectedMealId: Long?,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    allowAutomatic: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    val automatic = stringResource(Res.string.label_meal_automatic)
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(if (allowAutomatic) Res.string.label_preferred_meal else Res.string.label_entry_meal) + ": " +
                    (meals.firstOrNull { it.first == selectedMealId }?.second ?: automatic)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (allowAutomatic) {
                DropdownMenuItem(text = { Text(automatic) }, onClick = {
                    onSelect(null)
                    expanded = false
                })
            }
            meals.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = {
                    onSelect(id)
                    expanded = false
                })
            }
        }
    }
}

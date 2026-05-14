package com.maksimowiczm.foodyou.app.ui.home.shared

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

@Composable
internal fun FoodYouHomeCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    shape: Shape = MaterialTheme.shapes.medium,
    content: @Composable () -> Unit,
) {
    Surface(modifier = modifier, color = color, shape = shape) { content() }
}

@Composable
internal fun FoodYouHomeCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = FoodYouHomeCardDefaults.color,
    shape: Shape = MaterialTheme.shapes.medium,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(modifier = modifier, color = color, shape = shape) {
        Box(modifier = Modifier.combinedClickable(onLongClick = onLongClick, onClick = onClick)) {
            content()
        }
    }
}

internal object FoodYouHomeCardDefaults {

    val color: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceContainerLow
}

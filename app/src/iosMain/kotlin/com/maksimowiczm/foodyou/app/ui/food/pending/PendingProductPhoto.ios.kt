package com.maksimowiczm.foodyou.app.ui.food.pending

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal actual fun PendingProductPhoto(
    photoPath: String,
    modifier: Modifier,
    rotationDegrees: Float,
) {
    Text(photoPath, modifier = modifier)
}

@Composable
internal actual fun PendingProductPhotoPager(photoPaths: List<String>, modifier: Modifier) {
    Text(photoPaths.firstOrNull().orEmpty(), modifier = modifier)
}

@Composable
internal actual fun TakeNutritionPhotoButton(
    onPhotoTaken: (String) -> Unit,
    modifier: Modifier,
) {
    Text(stringResource(Res.string.neutral_take_nutrition_photo), modifier = modifier)
}

@Composable
internal actual fun TakeNutritionPhotoIconButton(onPhotoTaken: (String) -> Unit) = Unit

@Composable
internal actual fun rememberSharePendingProductPhotosAction(
    photoPaths: List<String>,
    prompt: String,
): () -> Unit = {}

@Composable
internal actual fun PendingProductPhotoCapture(
    photoCount: Int,
    onPhotoTaken: (String) -> Unit,
    modifier: Modifier,
) {
    Text(stringResource(Res.string.neutral_take_nutrition_photo), modifier = modifier)
}

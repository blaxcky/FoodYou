package com.maksimowiczm.foodyou.app.ui.food.pending

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal expect fun PendingProductPhoto(
    photoPath: String,
    modifier: Modifier = Modifier,
    rotationDegrees: Float = 0f,
)

@Composable
internal expect fun PendingProductPhotoPager(
    photoPaths: List<String>,
    modifier: Modifier = Modifier,
)

@Composable
internal expect fun TakeNutritionPhotoButton(
    onPhotoTaken: (String) -> Unit,
    modifier: Modifier = Modifier,
)

@Composable
internal expect fun TakeNutritionPhotoIconButton(onPhotoTaken: (String) -> Unit)

@Composable
internal expect fun PendingProductPhotoCapture(
    photoCount: Int,
    onPhotoTaken: (String) -> Unit,
    modifier: Modifier = Modifier,
)

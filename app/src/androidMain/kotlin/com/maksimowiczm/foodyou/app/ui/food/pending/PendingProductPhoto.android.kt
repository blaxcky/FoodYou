package com.maksimowiczm.foodyou.app.ui.food.pending

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.RotateLeft
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.core.content.FileProvider
import com.maksimowiczm.foodyou.food.infrastructure.PENDING_PRODUCT_PHOTO_DIRECTORY
import foodyou.app.generated.resources.*
import java.io.File
import java.util.UUID
import org.jetbrains.compose.resources.stringResource

@Composable
internal actual fun PendingProductPhoto(
    photoPath: String,
    modifier: Modifier,
    rotationDegrees: Float,
) {
    val context = LocalContext.current
    val file = remember(photoPath) {
        context.filesDir.resolve(PENDING_PRODUCT_PHOTO_DIRECTORY).resolve(photoPath)
    }
    val bitmap = remember(file.absolutePath) {
        BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
    }

    Box(modifier = modifier.clipToBounds()) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer { rotationZ = rotationDegrees },
            )
        }
    }
}

@Composable
internal actual fun PendingProductPhotoPager(photoPaths: List<String>, modifier: Modifier) {
    val pagerState = rememberPagerState(pageCount = { photoPaths.size })
    var rotation by rememberSaveable(photoPaths) { mutableIntStateOf(0) }

    Box(modifier = modifier.clipToBounds()) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            ZoomablePendingProductPhoto(
                photoPath = photoPaths[page],
                rotationDegrees = rotation.toFloat(),
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (photoPaths.size > 1) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.88f),
                shape = CircleShape,
                modifier = Modifier.align(Alignment.BottomStart),
            ) {
                Text(
                    text = "${pagerState.currentPage + 1}/${photoPaths.size}",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.88f),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.align(Alignment.BottomEnd),
        ) {
            Row(modifier = Modifier.padding(horizontal = 4.dp)) {
                IconButton(onClick = { rotation = (rotation + 270) % 360 }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.RotateLeft,
                        contentDescription = stringResource(Res.string.action_rotate_photo_left),
                    )
                }
                IconButton(onClick = { rotation = (rotation + 90) % 360 }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.RotateRight,
                        contentDescription = stringResource(Res.string.action_rotate_photo_right),
                    )
                }
            }
        }
    }
}

@Composable
private fun ZoomablePendingProductPhoto(
    photoPath: String,
    rotationDegrees: Float,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val file = remember(photoPath) {
        context.filesDir.resolve(PENDING_PRODUCT_PHOTO_DIRECTORY).resolve(photoPath)
    }
    val bitmap = remember(file.absolutePath) {
        BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
    }
    var scale by remember(photoPath) { mutableFloatStateOf(1f) }
    var offset by remember(photoPath) { mutableStateOf(Offset.Zero) }
    val transformableState =
        rememberTransformableState { zoomChange, panChange, _ ->
            val newScale = (scale * zoomChange).coerceIn(1f, 5f)
            scale = newScale
            offset = if (newScale == 1f) Offset.Zero else offset + panChange
        }

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier =
                modifier
                    .transformable(transformableState)
                    .clipToBounds()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                        rotationZ = rotationDegrees
                    },
        )
    }
}

@Composable
internal actual fun TakeNutritionPhotoButton(
    onPhotoTaken: (String) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    var pendingFile by remember { mutableStateOf<File?>(null) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val file = pendingFile
            pendingFile = null
            if (success && file != null) {
                onPhotoTaken(file.name)
            } else {
                file?.delete()
            }
        }

    FilledTonalButton(
        onClick = {
            val directory = context.filesDir.resolve(PENDING_PRODUCT_PHOTO_DIRECTORY)
            directory.mkdirs()
            val file = directory.resolve("${UUID.randomUUID()}.jpg")
            pendingFile = file
            val uri: Uri =
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            launcher.launch(uri)
        },
        modifier = modifier,
    ) {
        Icon(
            imageVector = Icons.Outlined.PhotoCamera,
            contentDescription = null,
            modifier = Modifier.padding(end = 8.dp).size(18.dp),
        )
        Text(stringResource(Res.string.neutral_take_nutrition_photo))
    }
}

@Composable
internal actual fun TakeNutritionPhotoIconButton(onPhotoTaken: (String) -> Unit) {
    val launcher = rememberPendingPhotoLauncher(onPhotoTaken)
    IconButton(onClick = launcher) {
        Icon(
            imageVector = Icons.Outlined.PhotoCamera,
            contentDescription = stringResource(Res.string.neutral_take_nutrition_photo),
        )
    }
}

@Composable
private fun rememberPendingPhotoLauncher(onPhotoTaken: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    var pendingFile by remember { mutableStateOf<File?>(null) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val file = pendingFile
            pendingFile = null
            if (success && file != null) {
                onPhotoTaken(file.name)
            } else {
                file?.delete()
            }
        }

    return {
        val directory = context.filesDir.resolve(PENDING_PRODUCT_PHOTO_DIRECTORY)
        directory.mkdirs()
        val file = directory.resolve("${UUID.randomUUID()}.jpg")
        pendingFile = file
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        launcher.launch(uri)
    }
}

package com.maksimowiczm.foodyou.app.ui.food.pending

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import foodyou.app.generated.resources.Res
import foodyou.app.generated.resources.action_close
import foodyou.app.generated.resources.neutral_pending_product_photo_count
import foodyou.app.generated.resources.neutral_photo_save_failed
import foodyou.app.generated.resources.neutral_take_nutrition_photo
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PendingProductCameraOverlay(
    photoCount: Int,
    photoSaving: Boolean,
    photoSaveError: Boolean,
    shutterVisible: Boolean,
    showCapturePreview: Boolean,
    previewBitmap: ImageBitmap?,
    previewVisible: Boolean,
    onDismissPreview: () -> Unit,
    onCapture: () -> Unit,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        if (showCapturePreview && previewBitmap != null) {
            val visibility = remember(previewBitmap) { MutableTransitionState(false) }
            visibility.targetState = previewVisible
            AnimatedVisibility(
                visibleState = visibility,
                enter = fadeIn(tween(120)) + scaleIn(tween(120), initialScale = 0.96f),
                exit = fadeOut(tween(180)),
                modifier = Modifier.fillMaxSize().padding(
                    start = 24.dp, end = 24.dp, top = 64.dp, bottom = 144.dp,
                ),
            ) {
                Surface(
                    onClick = onDismissPreview,
                    shape = MaterialTheme.shapes.large,
                    color = Color.Black,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxSize().testTag("capture-photo-preview"),
                ) {
                    Image(
                        bitmap = previewBitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.88f),
            shape = CircleShape,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
        ) {
            Text(
                text = stringResource(Res.string.neutral_pending_product_photo_count, photoCount),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        LargeFloatingActionButton(
            onClick = { if (!photoSaving) onCapture() },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
                .testTag("camera-shutter")
                .semantics { if (photoSaving) disabled() },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.PhotoCamera,
                    contentDescription = stringResource(Res.string.neutral_take_nutrition_photo),
                )
                if (showCapturePreview && photoSaving) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(56.dp).testTag("capture-photo-saving"),
                    )
                }
            }
        }

        if (onClose != null) {
            FilledTonalIconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
                    .offset(x = 48.dp + 16.dp + 24.dp)
                    .size(48.dp).testTag("camera-close"),
            ) {
                Icon(Icons.Outlined.Close, stringResource(Res.string.action_close))
            }
        }

        if (photoSaveError) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.96f),
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 104.dp),
            ) {
                Text(
                    text = stringResource(Res.string.neutral_photo_save_failed),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }

        if (shutterVisible) {
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.42f)))
        }
    }
}

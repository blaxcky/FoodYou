package com.maksimowiczm.foodyou.app.ui.food.pending

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import foodyou.app.generated.resources.Res
import foodyou.app.generated.resources.action_close
import foodyou.app.generated.resources.action_show_last_photo
import foodyou.app.generated.resources.neutral_pending_product_photo_count
import foodyou.app.generated.resources.neutral_photo_save_failed
import foodyou.app.generated.resources.neutral_take_nutrition_photo
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PendingProductCameraOverlay(
    photoCount: Int,
    photoSaving: Boolean,
    photoSaveError: Boolean,
    shutterVisible: Boolean,
    showCapturePreview: Boolean,
    flight: CaptureFlight<ImageBitmap>?,
    thumbnail: ImageBitmap?,
    expandedPhoto: ImageBitmap?,
    expanded: Boolean,
    onFlightFinished: (Int) -> Unit,
    onThumbnailClick: () -> Unit,
    onCollapse: () -> Unit,
    onCapture: () -> Unit,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val dim = remember { Animatable(0f) }
    var thumbnailBounds by remember { mutableStateOf<Rect?>(null) }

    Box(modifier) {
        if (showCapturePreview && flight != null) {
            val target = thumbnailBounds
            if (target != null) {
                CaptureFlightImage(
                    flight = flight,
                    target = target,
                    onFinished = onFlightFinished,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Box(Modifier.fillMaxSize().graphicsLayer { alpha = dim.value }.background(Color.Black))

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.88f),
            shape = CircleShape,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
        ) {
            AnimatedContent(
                targetState = photoCount,
                transitionSpec = {
                    val direction = if (targetState >= initialState) 1 else -1
                    (slideInVertically { it * direction } + fadeIn())
                        .togetherWith(slideOutVertically { -it * direction } + fadeOut())
                        .using(SizeTransform(clip = false))
                },
                label = "photo-count",
            ) { count ->
                Text(
                    text = stringResource(Res.string.neutral_pending_product_photo_count, count),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        val shutterInteraction = remember { MutableInteractionSource() }
        val shutterPressed by shutterInteraction.collectIsPressedAsState()
        val shutterScale by
            animateFloatAsState(
                targetValue = if (shutterPressed) 0.9f else 1f,
                animationSpec =
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                label = "shutter-scale",
            )
        LargeFloatingActionButton(
            onClick = {
                if (!photoSaving) {
                    if (showCapturePreview) {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        scope.launch {
                            dim.snapTo(0f)
                            dim.animateTo(0.45f, tween(60))
                            dim.animateTo(0f, tween(160))
                        }
                    }
                    onCapture()
                }
            },
            interactionSource = shutterInteraction,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
                .graphicsLayer {
                    scaleX = shutterScale
                    scaleY = shutterScale
                }
                .testTag("camera-shutter")
                .semantics { if (photoSaving) disabled() },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.PhotoCamera,
                    contentDescription = stringResource(Res.string.neutral_take_nutrition_photo),
                )
                if (showCapturePreview) {
                    SlowSaveIndicator(photoSaving)
                }
            }
        }

        if (showCapturePreview) {
            CaptureThumbnail(
                thumbnail = thumbnail,
                flying = flight != null,
                onClick = onThumbnailClick,
                modifier = Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = 44.dp)
                    .offset(x = -(48.dp + 16.dp + 24.dp))
                    .size(56.dp)
                    .onPlaced { thumbnailBounds = it.boundsInParent() },
            )
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

        if (showCapturePreview && expandedPhoto != null) {
            ExpandedCapturePhoto(
                photo = expandedPhoto,
                visible = expanded,
                onCollapse = onCollapse,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Shrinks the frozen viewfinder frame from full screen into [target] with a spring. */
@Composable
private fun CaptureFlightImage(
    flight: CaptureFlight<ImageBitmap>,
    target: Rect,
    onFinished: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = remember(flight.id) { Animatable(0f) }
    val latestOnFinished by rememberUpdatedState(onFinished)
    LaunchedEffect(flight.id) {
        progress.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 380f))
        latestOnFinished(flight.id)
    }
    val borderColor = Color.White
    Image(
        bitmap = flight.frame,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .layout { measurable, constraints ->
                val full = Rect(0f, 0f, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
                val bounds = lerp(full, target, progress.value)
                val placeable = measurable.measure(
                    Constraints.fixed(
                        bounds.width.roundToInt().coerceAtLeast(1),
                        bounds.height.roundToInt().coerceAtLeast(1),
                    )
                )
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(bounds.left.roundToInt(), bounds.top.roundToInt())
                }
            }
            .graphicsLayer {
                val fraction = progress.value.coerceIn(0f, 1f)
                shape = RoundedCornerShape(CornerSize(50f * fraction))
                clip = true
                shadowElevation = 8.dp.toPx() * fraction
            }
            .drawWithContent {
                drawContent()
                val fraction = ((progress.value - 0.5f) * 2f).coerceIn(0f, 1f)
                if (fraction > 0f) {
                    val shape = RoundedCornerShape(CornerSize(50f * progress.value.coerceIn(0f, 1f)))
                    drawOutline(
                        outline = shape.createOutline(size, layoutDirection, this),
                        color = borderColor.copy(alpha = fraction),
                        style = Stroke(width = 2.dp.toPx() * 2),
                    )
                }
            },
    )
}

@Composable
private fun CaptureThumbnail(
    thumbnail: ImageBitmap?,
    flying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bounce = remember { Animatable(1f) }
    var wasFlying by remember { mutableStateOf(false) }
    LaunchedEffect(flying) {
        if (wasFlying && !flying) {
            bounce.snapTo(1.12f)
            bounce.animateTo(
                1f,
                spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow),
            )
        }
        wasFlying = flying
    }
    // The slot stays laid out while empty so the first capture already has a flight target.
    Box(modifier) {
        val visibility = remember { MutableTransitionState(false) }
        visibility.targetState = thumbnail != null
        // Keeps the last image while the exit animation runs.
        val lastThumbnail = remember { arrayOfNulls<ImageBitmap>(1) }
        if (thumbnail != null) lastThumbnail[0] = thumbnail
        AnimatedVisibility(
            visibleState = visibility,
            enter = fadeIn() +
                scaleIn(spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            val image = lastThumbnail[0] ?: return@AnimatedVisibility
            val description = stringResource(Res.string.action_show_last_photo)
            Crossfade(
                targetState = image,
                animationSpec = tween(220),
                label = "capture-thumbnail",
                modifier = Modifier.fillMaxSize()
                    .graphicsLayer {
                        alpha = if (flying) 0f else 1f
                        scaleX = bounce.value
                        scaleY = bounce.value
                        shadowElevation = 6.dp.toPx()
                        shape = CircleShape
                        clip = true
                    }
                    .border(2.dp, Color.White, CircleShape)
                    .clickable(onClick = onClick, onClickLabel = description)
                    .testTag("capture-thumbnail"),
            ) { bitmap ->
                Image(
                    bitmap = bitmap,
                    contentDescription = description,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun ExpandedCapturePhoto(
    photo: ImageBitmap,
    visible: Boolean,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visibility = remember(photo) { MutableTransitionState(false) }
    visibility.targetState = visible
    Box(modifier) {
        AnimatedVisibility(
            visibleState = visibility,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(250)),
        ) {
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.72f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onCollapse,
                    )
            )
        }
        // Grow out of and shrink back into the thumbnail next to the shutter.
        val origin = TransformOrigin(0.25f, 0.95f)
        AnimatedVisibility(
            visibleState = visibility,
            enter = fadeIn(tween(120)) +
                scaleIn(
                    spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
                    initialScale = 0.15f,
                    transformOrigin = origin,
                ),
            exit = fadeOut(tween(250)) +
                scaleOut(tween(250), targetScale = 0.15f, transformOrigin = origin),
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 32.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Surface(
                    onClick = onCollapse,
                    shape = MaterialTheme.shapes.extraLarge,
                    color = Color.Black,
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .aspectRatio(photo.width.toFloat() / photo.height.coerceAtLeast(1))
                        .testTag("capture-photo-preview"),
                ) {
                    Image(
                        bitmap = photo,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

/** Shows progress only when saving takes noticeably long, so normal captures stay calm. */
@Composable
private fun SlowSaveIndicator(photoSaving: Boolean) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(photoSaving) {
        visible = false
        if (photoSaving) {
            delay(400)
            visible = true
        }
    }
    if (visible) {
        CircularProgressIndicator(
            strokeWidth = 2.dp,
            modifier = Modifier.size(56.dp).testTag("capture-photo-saving"),
        )
    }
}

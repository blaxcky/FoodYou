package com.maksimowiczm.foodyou.app.ui.food.pending

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.LargeFloatingActionButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.maksimowiczm.foodyou.food.infrastructure.PENDING_PRODUCT_PHOTO_DIRECTORY
import foodyou.app.generated.resources.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.delay
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

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier =
                modifier
                    .clipToBounds()
                    .pointerInput(photoPath) {
                        detectTapGestures(
                            onDoubleTap = {
                                scale = 1f
                                offset = Offset.Zero
                            }
                        )
                    }
                    .pointerInput(photoPath) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val pressed = event.changes.filter { it.pressed }
                                val shouldHandleZoom = pressed.size > 1 || scale > 1f
                                if (shouldHandleZoom) {
                                    val newScale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                                    scale = newScale
                                    offset =
                                        if (newScale == 1f) {
                                            Offset.Zero
                                        } else {
                                            offset + event.calculatePan()
                                        }
                                    event.changes.forEach { if (it.pressed) it.consume() }
                                }
                            } while (pressed.isNotEmpty())
                        }
                    }
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
internal actual fun rememberSharePendingProductPhotosAction(
    photoPaths: List<String>,
    prompt: String,
): () -> Unit {
    val context = LocalContext.current
    val currentPhotoPaths by rememberUpdatedState(photoPaths)
    val currentPrompt by rememberUpdatedState(prompt)

    return remember(context) {
        {
            val uris =
                currentPhotoPaths
                    .map { context.filesDir.resolve(PENDING_PRODUCT_PHOTO_DIRECTORY).resolve(it) }
                    .filter { it.exists() }
                    .map {
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            it,
                        )
                    }

            if (uris.isNotEmpty()) {
                val sendIntent =
                    Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                        type = "image/jpeg"
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                        putExtra(Intent.EXTRA_TEXT, currentPrompt)
                        clipData =
                            ClipData.newUri(context.contentResolver, "Product photo", uris.first())
                                .apply {
                                    uris.drop(1).forEach {
                                        addItem(ClipData.Item(it))
                                    }
                                }
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                context.startActivity(Intent.createChooser(sendIntent, null))
            } else {
                val sendIntent =
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, currentPrompt)
                    }
                context.startActivity(Intent.createChooser(sendIntent, null))
            }
        }
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

@Composable
internal actual fun PendingProductPhotoCapture(
    photoCount: Int,
    onPhotoTaken: (String) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasCameraPermission = granted
        }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasCameraPermission) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(Res.string.neutral_camera_request))
                FilledTonalButton(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Text(stringResource(Res.string.neutral_take_nutrition_photo))
                }
            }
        }
        return
    }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraBound by remember { mutableStateOf(false) }
    var shutterVisible by remember { mutableStateOf(false) }
    val latestOnPhotoTaken by rememberUpdatedState(onPhotoTaken)

    LaunchedEffect(shutterVisible) {
        if (shutterVisible) {
            delay(90)
            shutterVisible = false
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { viewContext ->
                PreviewView(viewContext).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { previewView ->
                if (cameraBound) return@AndroidView
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener(
                    {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview =
                            Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }
                        val capture =
                            ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                .build()

                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            capture,
                        )
                        imageCapture = capture
                        cameraBound = true
                    },
                    ContextCompat.getMainExecutor(context),
                )
            },
        )

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.88f),
            shape = CircleShape,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
        ) {
            Text(
                text =
                    stringResource(
                        Res.string.neutral_pending_product_photo_count,
                        photoCount,
                    ),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        LargeFloatingActionButton(
            onClick = {
                imageCapture?.takePendingProductPhoto(context) {
                    latestOnPhotoTaken(it)
                    shutterVisible = true
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.PhotoCamera,
                contentDescription = stringResource(Res.string.neutral_take_nutrition_photo),
            )
        }

        if (shutterVisible) {
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.42f)))
        }
    }
}

private fun ImageCapture.takePendingProductPhoto(
    context: android.content.Context,
    onSaved: (String) -> Unit,
) {
    val directory = context.filesDir.resolve(PENDING_PRODUCT_PHOTO_DIRECTORY)
    directory.mkdirs()
    val file = directory.resolve("${UUID.randomUUID()}.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

    takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                onSaved(file.name)
            }

            override fun onError(exception: ImageCaptureException) {
                file.delete()
            }
        },
    )
}

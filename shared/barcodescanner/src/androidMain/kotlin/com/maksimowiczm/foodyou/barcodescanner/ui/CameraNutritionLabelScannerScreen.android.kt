package com.maksimowiczm.foodyou.barcodescanner.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.util.Size
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.safeGesturesPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import foodyou.app.generated.resources.Res
import foodyou.app.generated.resources.action_cancel
import foodyou.app.generated.resources.action_close
import foodyou.app.generated.resources.action_go_to_settings
import foodyou.app.generated.resources.action_retry
import foodyou.app.generated.resources.action_tap_to_allow_access
import foodyou.app.generated.resources.headline_permission_required
import foodyou.app.generated.resources.neutral_barcode_scanner_camera_request_rationale
import foodyou.app.generated.resources.neutral_barcode_scanner_camera_request_redirect_to_settings
import foodyou.app.generated.resources.neutral_nutrition_label_camera_failed
import foodyou.app.generated.resources.neutral_nutrition_label_camera_request
import foodyou.app.generated.resources.neutral_nutrition_label_camera_starting
import foodyou.app.generated.resources.neutral_nutrition_label_scan_failed
import foodyou.app.generated.resources.neutral_take_nutrition_label_photo
import java.util.concurrent.Executors
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

private const val TAG = "NutritionLabelScanner"
private const val CAMERA_START_TIMEOUT_MS = 4_000L

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
actual fun CameraNutritionLabelScannerScreen(
    onTextRecognized: (List<RecognizedTextLine>) -> Unit,
    onClose: () -> Unit,
    captureEnabled: Boolean,
    modifier: Modifier,
) {
    val permissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val activity = LocalActivity.current
    val context = LocalContext.current

    var requestInSettings by remember { mutableStateOf(false) }
    var lifecycleOwnerRetry by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) { Log.d(TAG, "CameraNutritionLabelScannerScreen composed") }

    LaunchedEffect(permissionState.status, activity) {
        Log.d(
            TAG,
            "Camera permission status: granted=${permissionState.status.isGranted}, " +
                "shouldShowRationale=${permissionState.status.shouldShowRationale}, " +
                "activity=${activity?.javaClass?.name}",
        )
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) {
            isGranted ->
            Log.d(TAG, "Camera permission result: granted=$isGranted")
            if (
                activity != null &&
                    !isGranted &&
                    !shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
            ) {
                requestInSettings = true
            }
        }

    if (requestInSettings && !permissionState.status.isGranted) {
        RedirectToSettingsAlertDialog(
            onDismissRequest = { requestInSettings = false },
            onConfirm = {
                redirectToSettings(context)
                requestInSettings = false
            },
        )
    }

    Box(modifier = modifier) {
        Box(modifier = Modifier.safeGesturesPadding().align(Alignment.TopEnd).zIndex(1f)) {
            FilledIconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(Res.string.action_close),
                )
            }
        }

        val lifecycleOwner = activity as? LifecycleOwner
        if (permissionState.status.isGranted && lifecycleOwner != null) {
            NutritionLabelPhotoCameraScreen(
                lifecycleOwner = lifecycleOwner,
                onTextRecognized = onTextRecognized,
                captureEnabled = captureEnabled,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (permissionState.status.isGranted) {
            LaunchedEffect(activity, lifecycleOwnerRetry) {
                Log.e(
                    TAG,
                    "Camera permission granted but LocalActivity is not a LifecycleOwner: " +
                        "${activity?.javaClass?.name}",
                )
            }
            CameraStartErrorScreen(
                onRetry = {
                    Log.d(TAG, "Retry requested after missing LifecycleOwner")
                    lifecycleOwnerRetry += 1
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            RequestCameraPermissionScreen(
                onRequest = {
                    Log.d(TAG, "Requesting camera permission")
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                },
                shouldShowRationale = permissionState.status.shouldShowRationale,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private enum class CameraStartState {
    Waiting,
    Starting,
    Ready,
    Failed,
}

private enum class NutritionLabelCaptureState {
    Ready,
    Capturing,
    Analyzing,
    AnalyzeFailed,
}

@Composable
private fun NutritionLabelPhotoCameraScreen(
    lifecycleOwner: LifecycleOwner,
    onTextRecognized: (List<RecognizedTextLine>) -> Unit,
    captureEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val latestOnTextRecognized by rememberUpdatedState(onTextRecognized)
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var startState by remember { mutableStateOf(CameraStartState.Waiting) }
    var captureState by remember { mutableStateOf(NutritionLabelCaptureState.Ready) }
    var startAttempt by remember { mutableStateOf(0) }
    var startCamera by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { Log.d(TAG, "Entered NutritionLabelPhotoCameraScreen") }

    LaunchedEffect(startAttempt) {
        Log.d(TAG, "Starting nutrition label camera attempt=$startAttempt")
        startState = CameraStartState.Starting
        captureState = NutritionLabelCaptureState.Ready
        startCamera = false
        delay(1)
        startCamera = true
    }

    LaunchedEffect(startState, startAttempt) {
        if (startState == CameraStartState.Starting) {
            delay(CAMERA_START_TIMEOUT_MS)
            if (startState == CameraStartState.Starting) {
                Log.e(TAG, "Nutrition label camera start timed out")
                startState = CameraStartState.Failed
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            recognizer.close()
        }
    }

    DisposableEffect(previewView, lifecycleOwner, startAttempt) {
        val view = previewView ?: return@DisposableEffect onDispose {}
        if (!startCamera) {
            return@DisposableEffect onDispose {}
        }

        Log.d(TAG, "Requesting ProcessCameraProvider.getInstance")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        var cameraProvider: ProcessCameraProvider? = null
        var preview: Preview? = null
        var capture: ImageCapture? = null
        var disposed = false

        cameraProviderFuture.addListener(
            {
                if (disposed) return@addListener

                try {
                    val provider = cameraProviderFuture.get()
                    cameraProvider = provider
                    val scannerPreview =
                        Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                    val scannerImageCapture =
                        ImageCapture.Builder()
                            .setResolutionSelector(
                                ResolutionSelector.Builder()
                                    .setResolutionStrategy(
                                        ResolutionStrategy(
                                            Size(1920, 1080),
                                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                                        )
                                    )
                                    .build()
                            )
                            .build()

                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        scannerPreview,
                        scannerImageCapture,
                    )
                    Log.d(TAG, "bindToLifecycle succeeded for nutrition label camera")
                    preview = scannerPreview
                    capture = scannerImageCapture
                    if (!disposed) {
                        imageCapture = scannerImageCapture
                        startState = CameraStartState.Ready
                    }
                } catch (error: Exception) {
                    Log.e(TAG, "Failed to bind nutrition label camera", error)
                    if (!disposed) {
                        startState = CameraStartState.Failed
                    }
                }
            },
            mainExecutor,
        )

        onDispose {
            disposed = true
            val scannerPreview = preview
            val scannerImageCapture = capture
            if (scannerPreview != null && scannerImageCapture != null) {
                cameraProvider?.unbind(scannerPreview, scannerImageCapture)
            } else if (scannerPreview != null) {
                cameraProvider?.unbind(scannerPreview)
            } else if (scannerImageCapture != null) {
                cameraProvider?.unbind(scannerImageCapture)
            }
            imageCapture = null
        }
    }

    Box(modifier = modifier) {
        if (startCamera) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    PreviewView(viewContext).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        Log.d(TAG, "PreviewView created for nutrition label camera")
                        previewView = this
                    }
                },
            )
        }

        when (startState) {
            CameraStartState.Waiting,
            CameraStartState.Starting -> CameraStartingScreen(modifier = Modifier.fillMaxSize())

            CameraStartState.Failed ->
                CameraStartErrorScreen(
                    onRetry = {
                        previewView = null
                        startAttempt += 1
                    },
                    modifier = Modifier.fillMaxSize(),
                )

            CameraStartState.Ready -> Unit
        }

        if (captureEnabled && startState == CameraStartState.Ready) {
            val canCapture =
                captureState == NutritionLabelCaptureState.Ready ||
                    captureState == NutritionLabelCaptureState.AnalyzeFailed
            CaptureControls(
                state = captureState,
                onCapture = {
                    val capture = imageCapture ?: return@CaptureControls
                    captureState = NutritionLabelCaptureState.Capturing
                    capture.takePicture(
                        cameraExecutor,
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                mainExecutor.execute {
                                    captureState = NutritionLabelCaptureState.Analyzing
                                }
                                recognizeNutritionLabelText(
                                    imageProxy = image,
                                    recognizer = recognizer,
                                    mainExecutor = mainExecutor,
                                    onTextRecognized = {
                                        latestOnTextRecognized(it)
                                        captureState = NutritionLabelCaptureState.Ready
                                    },
                                    onFailure = {
                                        Log.d(TAG, "Nutrition label text recognition failed", it)
                                        captureState = NutritionLabelCaptureState.AnalyzeFailed
                                    },
                                )
                            }

                            override fun onError(exception: ImageCaptureException) {
                                Log.d(TAG, "Nutrition label image capture failed", exception)
                                mainExecutor.execute {
                                    captureState = NutritionLabelCaptureState.AnalyzeFailed
                                }
                            }
                        },
                    )
                },
                enabled = canCapture,
                modifier =
                    Modifier.align(Alignment.BottomCenter)
                        .safeGesturesPadding()
                        .padding(bottom = 24.dp),
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun CaptureControls(
    state: NutritionLabelCaptureState,
    onCapture: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (state) {
            NutritionLabelCaptureState.Capturing,
            NutritionLabelCaptureState.Analyzing -> CircularProgressIndicator(color = Color.White)

            NutritionLabelCaptureState.AnalyzeFailed ->
                Text(
                    text = stringResource(Res.string.neutral_nutrition_label_scan_failed),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )

            NutritionLabelCaptureState.Ready ->
                Text(
                    text = stringResource(Res.string.neutral_take_nutrition_label_photo),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
        }
        FilledIconButton(
            onClick = onCapture,
            enabled = enabled,
            modifier =
                Modifier.size(72.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f), CircleShape)
                    .border(4.dp, Color.White, CircleShape),
            shapes = IconButtonDefaults.shapes(),
        ) {
            Icon(
                imageVector = Icons.Default.PhotoCamera,
                contentDescription = stringResource(Res.string.neutral_take_nutrition_label_photo),
            )
        }
    }
}

private fun recognizeNutritionLabelText(
    imageProxy: ImageProxy,
    recognizer: com.google.mlkit.vision.text.TextRecognizer,
    mainExecutor: java.util.concurrent.Executor,
    onTextRecognized: (List<RecognizedTextLine>) -> Unit,
    onFailure: (Exception) -> Unit,
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        mainExecutor.execute { onFailure(IllegalStateException("Captured image is unavailable")) }
        return
    }

    Log.d(
        TAG,
        "Nutrition label captured image size=${imageProxy.width}x${imageProxy.height}, " +
            "rotation=${imageProxy.imageInfo.rotationDegrees}",
    )

    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    recognizer
        .process(image)
        .addOnSuccessListener(mainExecutor) { text ->
            val lines =
                text.textBlocks.flatMap { block ->
                    block.lines.mapNotNull { line ->
                        val lineBounds = line.boundingBox?.toTextBounds() ?: return@mapNotNull null
                        RecognizedTextLine(
                            text = line.text,
                            bounds = lineBounds,
                            elements =
                                line.elements.mapNotNull { element ->
                                    val bounds =
                                        element.boundingBox?.toTextBounds()
                                            ?: return@mapNotNull null
                                    RecognizedTextElement(text = element.text, bounds = bounds)
                                },
                        )
                    }
                }
            onTextRecognized(lines)
        }
        .addOnFailureListener(mainExecutor, onFailure)
        .addOnCompleteListener(mainExecutor) { imageProxy.close() }
}

@Composable
private fun CameraStartingScreen(modifier: Modifier = Modifier) {
    Surface(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize().safeContentPadding(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(Res.string.neutral_nutrition_label_camera_starting),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun CameraStartErrorScreen(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize().safeContentPadding().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.neutral_nutrition_label_camera_failed),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onRetry) {
                Text(stringResource(Res.string.action_retry))
            }
        }
    }
}

@Composable
private fun RequestCameraPermissionScreen(
    onRequest: () -> Unit,
    shouldShowRationale: Boolean,
    modifier: Modifier = Modifier,
) {
    val text =
        if (shouldShowRationale) {
            stringResource(Res.string.neutral_barcode_scanner_camera_request_rationale)
        } else {
            stringResource(Res.string.neutral_nutrition_label_camera_request)
        }

    Surface(modifier = modifier, onClick = onRequest) {
        Column(
            modifier = Modifier.fillMaxSize().safeContentPadding(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = text,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(Res.string.action_tap_to_allow_access),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun RedirectToSettingsAlertDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(Res.string.action_go_to_settings))
            }
        },
        modifier = modifier,
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
        title = { Text(stringResource(Res.string.headline_permission_required)) },
        text = {
            Text(
                stringResource(
                    Res.string.neutral_barcode_scanner_camera_request_redirect_to_settings
                )
            )
        },
    )
}

private fun Rect.toTextBounds(): TextBounds =
    TextBounds(left = left, top = top, right = right, bottom = bottom)

private fun redirectToSettings(context: Context) {
    val intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    context.startActivity(intent)
}

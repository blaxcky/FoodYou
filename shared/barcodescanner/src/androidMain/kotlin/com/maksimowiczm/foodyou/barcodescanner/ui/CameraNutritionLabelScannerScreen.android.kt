package com.maksimowiczm.foodyou.barcodescanner.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.safeGesturesPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import foodyou.app.generated.resources.*
import foodyou.app.generated.resources.Res
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import org.jetbrains.compose.resources.stringResource

private const val TAG = "NutritionLabelScanner"
private const val OCR_INTERVAL_MS = 800L

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
actual fun CameraNutritionLabelScannerScreen(
    onTextRecognized: (List<RecognizedTextLine>) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    val permissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val activity = LocalActivity.current
    val context = LocalContext.current

    var requestInSettings by remember { mutableStateOf(false) }

    val permissionLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) {
            isGranted ->
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
            NutritionLabelLiveCameraScreen(
                lifecycleOwner = lifecycleOwner,
                onTextRecognized = onTextRecognized,
            )
        } else {
            RequestCameraPermissionScreen(
                onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                shouldShowRationale = permissionState.status.shouldShowRationale,
            )
        }
    }
}

@Composable
private fun NutritionLabelLiveCameraScreen(
    lifecycleOwner: LifecycleOwner,
    onTextRecognized: (List<RecognizedTextLine>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val latestOnTextRecognized by rememberUpdatedState(onTextRecognized)
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val analyzer =
        remember {
            NutritionLabelTextAnalyzer(
                recognizer = recognizer,
                onTextRecognized = { latestOnTextRecognized(it) },
            )
        }

    DisposableEffect(Unit) {
        onDispose {
            analyzerExecutor.shutdown()
            recognizer.close()
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { viewContext ->
            PreviewView(viewContext).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
        },
        update = { previewView ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener(
                {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview =
                        Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                    val imageAnalysis =
                        ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { it.setAnalyzer(analyzerExecutor, analyzer) }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis,
                        )
                    } catch (error: Exception) {
                        Log.e(TAG, "Failed to bind nutrition label camera", error)
                    }
                },
                ContextCompat.getMainExecutor(context),
            )
        },
    )
}

private class NutritionLabelTextAnalyzer(
    private val recognizer: com.google.mlkit.vision.text.TextRecognizer,
    private val onTextRecognized: (List<RecognizedTextLine>) -> Unit,
) : ImageAnalysis.Analyzer {
    private val lastAnalyzedAt = AtomicLong(0L)
    private val isRecognizing = AtomicBoolean(false)

    override fun analyze(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        if (
            now - lastAnalyzedAt.get() < OCR_INTERVAL_MS ||
                !isRecognizing.compareAndSet(false, true)
        ) {
            imageProxy.close()
            return
        }
        lastAnalyzedAt.set(now)

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            isRecognizing.set(false)
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        recognizer
            .process(image)
            .addOnSuccessListener { text ->
                val lines =
                    text.textBlocks.flatMap { block ->
                        block.lines.mapNotNull { line ->
                            val lineBounds =
                                line.boundingBox?.toTextBounds() ?: return@mapNotNull null
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
            .addOnFailureListener { error ->
                Log.d(TAG, "Nutrition label text recognition failed", error)
            }
            .addOnCompleteListener {
                isRecognizing.set(false)
                imageProxy.close()
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

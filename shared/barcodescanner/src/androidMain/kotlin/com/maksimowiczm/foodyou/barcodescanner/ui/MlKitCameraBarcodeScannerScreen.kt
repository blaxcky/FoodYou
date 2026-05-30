package com.maksimowiczm.foodyou.barcodescanner.ui

import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeGesturesPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import foodyou.app.generated.resources.*
import foodyou.app.generated.resources.Res
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import org.jetbrains.compose.resources.stringResource

@Composable
fun MlKitCameraBarcodeScannerScreen(
    onBarcodeScan: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val scanner =
        remember {
            BarcodeScanning.getClient(
                BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(
                        Barcode.FORMAT_EAN_13,
                        Barcode.FORMAT_EAN_8,
                        Barcode.FORMAT_UPC_A,
                        Barcode.FORMAT_UPC_E,
                    )
                    .build()
            )
        }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val barcodeReported = remember { AtomicBoolean(false) }

    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var cameraBindingStarted by remember { mutableStateOf(false) }
    var torchOn by rememberSaveable { mutableStateOf(false) }

    val latestOnBarcodeScanned by rememberUpdatedState(onBarcodeScan)

    DisposableEffect(Unit) {
        onDispose {
            cameraProvider?.unbindAll()
            analysisExecutor.shutdown()
            scanner.close()
        }
    }

    Box(modifier) {
        AndroidView(
            factory = { viewContext ->
                PreviewView(viewContext).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
            },
            modifier = Modifier.fillMaxSize(),
            update = { previewView ->
                if (cameraBindingStarted) return@AndroidView

                cameraBindingStarted = true
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener(
                    {
                        val provider = cameraProviderFuture.get()
                        val preview =
                            Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }
                        val analysis =
                            barcodeImageAnalysis(
                                scanner = scanner,
                                analysisExecutor = analysisExecutor,
                                barcodeReported = barcodeReported,
                                onBarcodeScan = { latestOnBarcodeScanned(it) },
                            )

                        provider.unbindAll()
                        val boundCamera =
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                analysis,
                            )
                        cameraProvider = provider
                        camera = boundCamera
                        torchOn = boundCamera.cameraInfo.torchState.value == TorchState.ON
                    },
                    mainExecutor,
                )
            },
        )

        Column(
            modifier = Modifier.safeGesturesPadding().align(Alignment.BottomCenter).zIndex(1f),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FlashlightButton(
                active = torchOn,
                enabled = camera != null,
                onClick = {
                    val boundCamera = camera ?: return@FlashlightButton
                    val nextTorchState = !torchOn
                    val torchFuture = boundCamera.cameraControl.enableTorch(nextTorchState)
                    torchFuture.addListener({ torchOn = nextTorchState }, mainExecutor)
                },
            )
            Surface(
                color = MaterialTheme.colorScheme.scrim,
                modifier = Modifier.navigationBarsPadding().alpha(.5f),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    text = stringResource(Res.string.action_scan_barcode),
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun barcodeImageAnalysis(
    scanner: BarcodeScanner,
    analysisExecutor: ExecutorService,
    barcodeReported: AtomicBoolean,
    onBarcodeScan: (String) -> Unit,
): ImageAnalysis =
    ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setTargetResolution(Size(1280, 720))
        .build()
        .apply {
            setAnalyzer(analysisExecutor) { imageProxy ->
                analyzeBarcodeImage(
                    imageProxy = imageProxy,
                    scanner = scanner,
                    barcodeReported = barcodeReported,
                    onBarcodeScan = onBarcodeScan,
                )
            }
        }

private fun analyzeBarcodeImage(
    imageProxy: ImageProxy,
    scanner: BarcodeScanner,
    barcodeReported: AtomicBoolean,
    onBarcodeScan: (String) -> Unit,
) {
    if (barcodeReported.get()) {
        imageProxy.close()
        return
    }

    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }

    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    scanner
        .process(image)
        .addOnSuccessListener { barcodes ->
            val rawValue = barcodes.firstNotNullOfOrNull { it.rawValue?.takeIf(String::isNotBlank) }
            if (rawValue != null && barcodeReported.compareAndSet(false, true)) {
                onBarcodeScan(rawValue)
            }
        }
        .addOnCompleteListener { imageProxy.close() }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FlashlightButton(
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background by
        animateColorAsState(
            if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
        )
    val content by
        animateColorAsState(
            if (active) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface
        )

    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        shapes = IconButtonDefaults.shapes(),
        modifier = modifier,
        colors =
            IconButtonDefaults.filledIconButtonColors(
                containerColor = background,
                contentColor = content,
            ),
    ) {
        Icon(
            imageVector = if (active) Icons.Default.FlashOn else Icons.Default.FlashOff,
            contentDescription =
                if (active) stringResource(Res.string.action_disable_camera_flash)
                else stringResource(Res.string.action_enable_camera_flash),
        )
    }
}

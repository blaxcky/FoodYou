package com.maksimowiczm.foodyou.barcodescanner.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.safeGesturesPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import foodyou.app.generated.resources.*
import foodyou.app.generated.resources.Res
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
actual fun CameraNutritionLabelScannerScreen(
    onTextRecognized: (List<RecognizedTextLine>) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    val permissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val activity = LocalActivity.current
    val latestOnTextRecognized by rememberUpdatedState(onTextRecognized)

    var requestInSettings by remember { mutableStateOf(false) }
    var isRecognizing by remember { mutableStateOf(false) }
    var recognitionFailed by remember { mutableStateOf(false) }

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

    val imageLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            if (bitmap == null) {
                return@rememberLauncherForActivityResult
            }
            isRecognizing = true
            recognitionFailed = false
            recognizeText(
                bitmap = bitmap,
                onSuccess = {
                    isRecognizing = false
                    latestOnTextRecognized(it)
                },
                onFailure = {
                    isRecognizing = false
                    recognitionFailed = true
                },
            )
        }

    val context = androidx.compose.ui.platform.LocalContext.current
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

        if (permissionState.status.isGranted) {
            NutritionLabelCaptureScreen(
                isRecognizing = isRecognizing,
                recognitionFailed = recognitionFailed,
                onTakePhoto = { imageLauncher.launch(null) },
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
private fun NutritionLabelCaptureScreen(
    isRecognizing: Boolean,
    recognitionFailed: Boolean,
    onTakePhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().safeContentPadding().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.neutral_take_nutrition_label_photo),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(24.dp))
            if (isRecognizing) {
                CircularProgressIndicator()
            } else {
                Button(onClick = onTakePhoto) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.PhotoCamera, contentDescription = null)
                        Text(stringResource(Res.string.action_scan_nutrition_label))
                    }
                }
            }
            if (recognitionFailed) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(Res.string.neutral_nutrition_label_scan_failed),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
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

private fun recognizeText(
    bitmap: Bitmap,
    onSuccess: (List<RecognizedTextLine>) -> Unit,
    onFailure: () -> Unit,
) {
    val image = InputImage.fromBitmap(bitmap, 0)
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    recognizer
        .process(image)
        .addOnSuccessListener { text ->
            val lines =
                text.textBlocks.flatMap { block ->
                    block.lines.map { line -> RecognizedTextLine(line.text) }
                }
            onSuccess(lines)
        }
        .addOnFailureListener { onFailure() }
}

private fun redirectToSettings(context: Context) {
    val intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    context.startActivity(intent)
}

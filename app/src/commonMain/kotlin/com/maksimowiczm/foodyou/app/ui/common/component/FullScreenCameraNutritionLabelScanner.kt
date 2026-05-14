package com.maksimowiczm.foodyou.app.ui.common.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.safeGesturesPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.maksimowiczm.foodyou.app.infrastructure.FoodYouLogger
import com.maksimowiczm.foodyou.barcodescanner.ui.CameraNutritionLabelScannerScreen
import com.maksimowiczm.foodyou.barcodescanner.ui.RecognizedTextLine
import com.maksimowiczm.foodyou.common.compose.component.FullScreenDialog
import foodyou.app.generated.resources.Res
import foodyou.app.generated.resources.action_close
import foodyou.app.generated.resources.neutral_nutrition_label_camera_starting
import org.jetbrains.compose.resources.stringResource

private const val NUTRITION_LABEL_SCANNER_TAG = "NutritionLabelScanner"

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FullScreenCameraNutritionLabelScanner(
    visible: Boolean,
    onTextRecognized: (List<RecognizedTextLine>) -> Unit,
    onClose: () -> Unit,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    if (visible) {
        var scannerContentEnabled by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            FoodYouLogger.d(NUTRITION_LABEL_SCANNER_TAG) {
                "Nutrition label scanner dialog composed"
            }
            withFrameNanos {}
            FoodYouLogger.d(NUTRITION_LABEL_SCANNER_TAG) {
                "Nutrition label scanner first dialog frame completed"
            }
            scannerContentEnabled = true
            FoodYouLogger.d(NUTRITION_LABEL_SCANNER_TAG) {
                "Nutrition label scanner content enabled"
            }
        }

        FullScreenDialog(onDismissRequest = onClose) {
            Box(Modifier.fillMaxSize()) {
                if (scannerContentEnabled) {
                    CameraNutritionLabelScannerScreen(
                        onTextRecognized = onTextRecognized,
                        onClose = onClose,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    NutritionLabelScannerStartingShell(
                        onClose = onClose,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                overlay()
            }
        }
    }
}

@Composable
private fun NutritionLabelScannerStartingShell(onClose: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier) {
        Box(Modifier.fillMaxSize()) {
            Box(modifier = Modifier.safeGesturesPadding().align(Alignment.TopEnd).zIndex(1f)) {
                FilledIconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(Res.string.action_close),
                    )
                }
            }
            Column(
                modifier = Modifier.fillMaxSize().safeContentPadding(),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
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
}

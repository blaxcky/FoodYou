package com.maksimowiczm.foodyou.app.ui.common.component

import androidx.compose.runtime.Composable
import com.maksimowiczm.foodyou.barcodescanner.ui.CameraNutritionLabelScannerScreen
import com.maksimowiczm.foodyou.barcodescanner.ui.RecognizedTextLine
import com.maksimowiczm.foodyou.common.compose.component.FullScreenDialog

@Composable
fun FullScreenCameraNutritionLabelScanner(
    visible: Boolean,
    onTextRecognized: (List<RecognizedTextLine>) -> Unit,
    onClose: () -> Unit,
) {
    if (visible) {
        FullScreenDialog(onDismissRequest = onClose) {
            CameraNutritionLabelScannerScreen(onTextRecognized = onTextRecognized, onClose = onClose)
        }
    }
}

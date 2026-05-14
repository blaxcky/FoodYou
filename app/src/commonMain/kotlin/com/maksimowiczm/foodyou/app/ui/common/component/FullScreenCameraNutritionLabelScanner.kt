package com.maksimowiczm.foodyou.app.ui.common.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.maksimowiczm.foodyou.barcodescanner.ui.CameraNutritionLabelScannerScreen
import com.maksimowiczm.foodyou.barcodescanner.ui.RecognizedTextLine
import com.maksimowiczm.foodyou.common.compose.component.FullScreenDialog

@Composable
fun FullScreenCameraNutritionLabelScanner(
    visible: Boolean,
    onTextRecognized: (List<RecognizedTextLine>) -> Unit,
    onClose: () -> Unit,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    if (visible) {
        FullScreenDialog(onDismissRequest = onClose) {
            Box(Modifier.fillMaxSize()) {
                CameraNutritionLabelScannerScreen(
                    onTextRecognized = onTextRecognized,
                    onClose = onClose,
                    modifier = Modifier.fillMaxSize(),
                )
                overlay()
            }
        }
    }
}

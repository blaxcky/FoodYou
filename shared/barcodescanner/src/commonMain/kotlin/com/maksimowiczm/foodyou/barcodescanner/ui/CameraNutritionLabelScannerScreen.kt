package com.maksimowiczm.foodyou.barcodescanner.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

data class RecognizedTextLine(val text: String)

@Composable
expect fun CameraNutritionLabelScannerScreen(
    onTextRecognized: (List<RecognizedTextLine>) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
)

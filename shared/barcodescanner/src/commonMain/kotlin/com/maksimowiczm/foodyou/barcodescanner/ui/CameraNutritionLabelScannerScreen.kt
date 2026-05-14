package com.maksimowiczm.foodyou.barcodescanner.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

data class TextBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

data class RecognizedTextElement(val text: String, val bounds: TextBounds)

data class RecognizedTextLine(
    val text: String,
    val bounds: TextBounds,
    val elements: List<RecognizedTextElement>,
)

@Composable
expect fun CameraNutritionLabelScannerScreen(
    onTextRecognized: (List<RecognizedTextLine>) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
)

package com.maksimowiczm.foodyou.app.ui.food.product

import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.maksimowiczm.foodyou.barcodescanner.ui.RecognizedTextElement
import com.maksimowiczm.foodyou.barcodescanner.ui.RecognizedTextLine
import com.maksimowiczm.foodyou.barcodescanner.ui.TextBounds
import org.junit.Assert.assertEquals
import org.junit.Test

class NutritionLabelPhotoOcrTest {
    @Test
    fun parsesNutritionValuesFromReferencePhotoRepeatedly() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val bitmap =
            context.assets.open("nutrition-labels/naehrstoffe.jpg").use { input ->
                BitmapFactory.decodeStream(input)
            }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        try {
            repeat(REFERENCE_PHOTO_OCR_RUNS) { index ->
                val image = InputImage.fromBitmap(bitmap, 0)
                val text = Tasks.await(recognizer.process(image))
                val lines = text.toRecognizedTextLines()
                val result = NutritionLabelParser.parse(lines)
                val message = "run ${index + 1}; lines=${lines.joinToString { it.text }}"

                assertEquals("energy value at $message", 66f, result.energy?.value)
                assertEquals(
                    "energy unit at $message",
                    NutritionLabelUnit.Kcal,
                    result.energy?.unit,
                )
                assertEquals("fats at $message", 3.6f, result.fats?.value)
                assertEquals(
                    "carbohydrates at $message",
                    3.9f,
                    result.carbohydrates?.value,
                )
                assertEquals("proteins at $message", 4.4f, result.proteins?.value)
            }
        } finally {
            recognizer.close()
        }
    }
}

private const val REFERENCE_PHOTO_OCR_RUNS = 25

private fun com.google.mlkit.vision.text.Text.toRecognizedTextLines(): List<RecognizedTextLine> =
    textBlocks.flatMap { block ->
        block.lines.mapNotNull { line ->
            val bounds = line.boundingBox?.toTextBounds() ?: return@mapNotNull null
            RecognizedTextLine(
                text = line.text,
                bounds = bounds,
                elements =
                    line.elements.mapNotNull { element ->
                        RecognizedTextElement(
                            text = element.text,
                            bounds = element.boundingBox?.toTextBounds() ?: return@mapNotNull null,
                        )
                    },
            )
        }
    }

private fun android.graphics.Rect.toTextBounds(): TextBounds =
    TextBounds(left = left, top = top, right = right, bottom = bottom)

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
        assertReferencePhotoRepeatedly(
            assetPath = "nutrition-labels/naehrstoffe.jpg",
            expectedEnergyKcal = 66f,
            expectedFats = 3.6f,
            expectedCarbohydrates = 3.9f,
            expectedProteins = 4.4f,
        )
    }

    @Test
    fun parsesNutellaNutritionValuesFromReferencePhotoRepeatedly() {
        assertReferencePhotoRepeatedly(
            assetPath = "nutrition-labels/nutella.jpg",
            expectedEnergyKcal = 539f,
            expectedFats = 10.6f,
            expectedCarbohydrates = 57.5f,
            expectedProteins = 6.3f,
        )
    }

    private fun assertReferencePhotoRepeatedly(
        assetPath: String,
        expectedEnergyKcal: Float,
        expectedFats: Float,
        expectedCarbohydrates: Float,
        expectedProteins: Float,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().context
        val bitmap =
            context.assets.open(assetPath).use { input ->
                BitmapFactory.decodeStream(input)
            }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        try {
            repeat(REFERENCE_PHOTO_OCR_RUNS) { index ->
                val image = InputImage.fromBitmap(bitmap, 0)
                val text = Tasks.await(recognizer.process(image))
                val lines = text.toRecognizedTextLines()
                val result = NutritionLabelParser.parse(lines)
                val message = "$assetPath run ${index + 1}; lines=${lines.joinToString { it.text }}"

                assertEquals("energy value at $message", expectedEnergyKcal, result.energy?.value)
                assertEquals(
                    "energy unit at $message",
                    NutritionLabelUnit.Kcal,
                    result.energy?.unit,
                )
                assertEquals("fats at $message", expectedFats, result.fats?.value)
                assertEquals(
                    "carbohydrates at $message",
                    expectedCarbohydrates,
                    result.carbohydrates?.value,
                )
                assertEquals("proteins at $message", expectedProteins, result.proteins?.value)
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

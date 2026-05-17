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
    fun parsesNutritionValuesFromReferencePhoto() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val bitmap =
            context.assets.open("nutrition-labels/naehrstoffe.jpg").use { input ->
                BitmapFactory.decodeStream(input)
            }
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        val text =
            try {
                Tasks.await(recognizer.process(image))
            } finally {
                recognizer.close()
            }
        val result = NutritionLabelParser.parse(text.toRecognizedTextLines())

        assertEquals(66f, result.energy?.value)
        assertEquals(NutritionLabelUnit.Kcal, result.energy?.unit)
        assertEquals(3.6f, result.fats?.value)
        assertEquals(3.9f, result.carbohydrates?.value)
        assertEquals(4.4f, result.proteins?.value)
    }
}

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

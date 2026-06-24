package com.maksimowiczm.foodyou.app.ui.food.pending

import kotlin.test.assertEquals
import org.junit.Test

class PhotoTransformTest {
    @Test
    fun `fits portrait photo in portrait container at zero and 180 degrees`() {
        assertSizeEquals(
            PhotoTransformSize(width = 600f, height = 900f),
            PhotoTransform.layout(
                photoSize = PhotoTransformSize(width = 1200f, height = 1800f),
                containerSize = PhotoTransformSize(width = 1000f, height = 900f),
                rotationDegrees = 0f,
            ).displayedImageSize,
        )
        assertSizeEquals(
            PhotoTransformSize(width = 600f, height = 900f),
            PhotoTransform.layout(
                photoSize = PhotoTransformSize(width = 1200f, height = 1800f),
                containerSize = PhotoTransformSize(width = 1000f, height = 900f),
                rotationDegrees = 180f,
            ).displayedImageSize,
        )
    }

    @Test
    fun `fits rotated portrait photo completely and swaps pre-rotation dimensions`() {
        val layout =
            PhotoTransform.layout(
                photoSize = PhotoTransformSize(width = 1200f, height = 1800f),
                containerSize = PhotoTransformSize(width = 1000f, height = 900f),
                rotationDegrees = 90f,
            )

        assertSizeEquals(PhotoTransformSize(width = 1000f, height = 666.6667f), layout.displayedImageSize)
        assertSizeEquals(
            PhotoTransformSize(width = 666.6667f, height = 1000f),
            layout.imageSizeBeforeRotation,
        )
    }

    @Test
    fun `fits landscape photo in landscape container at 270 degrees`() {
        val layout =
            PhotoTransform.layout(
                photoSize = PhotoTransformSize(width = 1800f, height = 1200f),
                containerSize = PhotoTransformSize(width = 1000f, height = 600f),
                rotationDegrees = 270f,
            )

        assertSizeEquals(PhotoTransformSize(width = 400f, height = 600f), layout.displayedImageSize)
        assertSizeEquals(PhotoTransformSize(width = 600f, height = 400f), layout.imageSizeBeforeRotation)
    }

    @Test
    fun `translation is restricted to axes with image overhang`() {
        val bounds =
            PhotoTransform.translationBounds(
                displayedImageSize = PhotoTransformSize(width = 1000f, height = 666.6667f),
                containerSize = PhotoTransformSize(width = 1000f, height = 900f),
                scale = 2f,
            )

        assertBoundsEquals(PhotoTranslationBounds(maxX = 500f, maxY = 216.6667f), bounds)
        assertOffsetEquals(
            PhotoTransformOffset(x = 500f, y = -216.6667f),
            PhotoTransform.clampOffset(PhotoTransformOffset(x = 800f, y = -500f), bounds),
        )
    }

    @Test
    fun `one times zoom keeps the photo centered`() {
        val bounds =
            PhotoTransform.translationBounds(
                displayedImageSize = PhotoTransformSize(width = 600f, height = 900f),
                containerSize = PhotoTransformSize(width = 1000f, height = 900f),
                scale = 1f,
            )

        assertBoundsEquals(PhotoTranslationBounds(maxX = 0f, maxY = 0f), bounds)
        assertOffsetEquals(
            PhotoTransformOffset(x = 0f, y = 0f),
            PhotoTransform.clampOffset(PhotoTransformOffset(x = 100f, y = -100f), bounds),
        )
    }

    private fun assertSizeEquals(expected: PhotoTransformSize, actual: PhotoTransformSize) {
        assertEquals(expected.width, actual.width, absoluteTolerance = 0.001f)
        assertEquals(expected.height, actual.height, absoluteTolerance = 0.001f)
    }

    private fun assertBoundsEquals(expected: PhotoTranslationBounds, actual: PhotoTranslationBounds) {
        assertEquals(expected.maxX, actual.maxX, absoluteTolerance = 0.001f)
        assertEquals(expected.maxY, actual.maxY, absoluteTolerance = 0.001f)
    }

    private fun assertOffsetEquals(expected: PhotoTransformOffset, actual: PhotoTransformOffset) {
        assertEquals(expected.x, actual.x, absoluteTolerance = 0.001f)
        assertEquals(expected.y, actual.y, absoluteTolerance = 0.001f)
    }
}

package com.maksimowiczm.foodyou.app.ui.food.pending

import kotlin.math.abs
import kotlin.math.min

internal data class PhotoTransformSize(
    val width: Float,
    val height: Float,
)

internal data class PhotoTransformOffset(
    val x: Float,
    val y: Float,
)

internal data class PhotoTransformLayout(
    val imageSizeBeforeRotation: PhotoTransformSize,
    val displayedImageSize: PhotoTransformSize,
)

internal data class PhotoTranslationBounds(
    val maxX: Float,
    val maxY: Float,
)

/** Geometry for fitting, rotating, and panning a photo inside its viewport. */
internal object PhotoTransform {
    fun layout(
        photoSize: PhotoTransformSize,
        containerSize: PhotoTransformSize,
        rotationDegrees: Float,
    ): PhotoTransformLayout {
        if (photoSize.width <= 0f ||
            photoSize.height <= 0f ||
            containerSize.width <= 0f ||
            containerSize.height <= 0f
        ) {
            return PhotoTransformLayout(PhotoTransformSize(0f, 0f), PhotoTransformSize(0f, 0f))
        }

        val isQuarterTurn = isQuarterTurn(rotationDegrees)
        val rotatedPhotoSize =
            if (isQuarterTurn) PhotoTransformSize(photoSize.height, photoSize.width) else photoSize
        val fitScale =
            min(
                containerSize.width / rotatedPhotoSize.width,
                containerSize.height / rotatedPhotoSize.height,
            )
        val displayedImageSize =
            PhotoTransformSize(
                width = rotatedPhotoSize.width * fitScale,
                height = rotatedPhotoSize.height * fitScale,
            )
        val imageSizeBeforeRotation =
            if (isQuarterTurn) {
                PhotoTransformSize(displayedImageSize.height, displayedImageSize.width)
            } else {
                displayedImageSize
            }

        return PhotoTransformLayout(imageSizeBeforeRotation, displayedImageSize)
    }

    fun translationBounds(
        displayedImageSize: PhotoTransformSize,
        containerSize: PhotoTransformSize,
        scale: Float,
    ): PhotoTranslationBounds =
        PhotoTranslationBounds(
            maxX = ((displayedImageSize.width * scale - containerSize.width) / 2f).coerceAtLeast(0f),
            maxY = ((displayedImageSize.height * scale - containerSize.height) / 2f).coerceAtLeast(0f),
        )

    fun clampOffset(
        offset: PhotoTransformOffset,
        bounds: PhotoTranslationBounds,
    ): PhotoTransformOffset =
        PhotoTransformOffset(
            x = offset.x.coerceIn(-bounds.maxX, bounds.maxX),
            y = offset.y.coerceIn(-bounds.maxY, bounds.maxY),
        )

    private fun isQuarterTurn(rotationDegrees: Float): Boolean {
        val normalizedRotation = ((rotationDegrees % 360f) + 360f) % 360f
        return abs(normalizedRotation - 90f) < 0.01f || abs(normalizedRotation - 270f) < 0.01f
    }
}

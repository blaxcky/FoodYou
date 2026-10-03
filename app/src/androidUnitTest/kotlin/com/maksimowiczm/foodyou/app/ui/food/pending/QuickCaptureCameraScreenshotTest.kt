package com.maksimowiczm.foodyou.app.ui.food.pending

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import kotlin.test.assertEquals
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "de-rDE-w390dp-h844dp-mdpi", application = Application::class)
class QuickCaptureCameraScreenshotTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ActivityController<ComponentActivity>
    private val bitmaps = mutableListOf<Bitmap>()

    @After
    fun tearDown() {
        if (::activity.isInitialized) activity.close()
        bitmaps.forEach(Bitmap::recycle)
    }

    @Test
    fun thumbnailSitsBesideShutterAndCloseAfterCapture() {
        show()
        compose.onNodeWithTag("capture-thumbnail").assertIsDisplayed()
        compose.onNodeWithTag("camera-shutter").assertIsDisplayed()
        compose.onNodeWithTag("camera-close").assertIsDisplayed()
        compose.onNodeWithTag("capture-photo-preview").assertDoesNotExist()
        capture("thumbnail")
    }

    @Test
    fun tappingThumbnailExpandsPhoto() {
        show()
        compose.onNodeWithTag("capture-thumbnail").performClick()
        compose.onNodeWithTag("capture-photo-preview").assertIsDisplayed()
        capture("portrait-preview")
    }

    @Test
    fun landscapePhotoFitsWithoutCropping() {
        show(landscapePhoto = true, expanded = true)
        capture("landscape-photo")
    }

    @Test
    fun captureInProgressIsVisibleAndRejectsDuplicateTrigger() {
        var captures = 0
        show(saving = true, onCapture = { captures++ })
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag("capture-photo-saving").assertIsDisplayed()
        compose.onNodeWithTag("camera-shutter").assertIsNotEnabled().performClick()
        assertEquals(0, captures)
        capture("saving")
    }

    @Test
    fun quickSaveDoesNotFlashProgress() {
        show(saving = true)
        compose.onNodeWithTag("capture-photo-saving").assertDoesNotExist()
    }

    @Test
    fun shutterAcceptsAnotherCaptureWhileThumbnailIsVisible() {
        var captures = 0
        show(onCapture = { captures++ })
        compose.onNodeWithTag("camera-shutter").performClick()
        assertEquals(1, captures)
    }

    @Test
    fun tappingExpandedPhotoCollapsesWithoutCapturing() {
        var captures = 0
        show(expanded = true, onCapture = { captures++ })
        compose.onNodeWithTag("capture-photo-preview").performClick()
        compose.onNodeWithTag("capture-photo-preview").assertDoesNotExist()
        compose.onNodeWithTag("capture-thumbnail").assertIsDisplayed()
        assertEquals(0, captures)
    }

    @Test
    fun saveErrorDoesNotShowPhotoPreview() {
        show(error = true)
        compose.onNodeWithTag("capture-photo-preview").assertDoesNotExist()
        compose.onNodeWithTag("capture-thumbnail").assertDoesNotExist()
        capture("save-error")
    }

    private fun show(
        landscapePhoto: Boolean = false,
        saving: Boolean = false,
        error: Boolean = false,
        expanded: Boolean = false,
        onCapture: () -> Unit = {},
    ) {
        val bitmap = fixture(landscapePhoto).asImageBitmap()
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        activity.get().setContent {
            MaterialTheme {
                val thumbnail = if (saving || error) null else bitmap
                var isExpanded by remember { mutableStateOf(expanded) }
                Box(Modifier.fillMaxSize().background(Color(0xFF67736A))) {
                    PendingProductCameraOverlay(
                        photoCount = 3,
                        photoSaving = saving,
                        photoSaveError = error,
                        shutterVisible = false,
                        showCapturePreview = true,
                        flight = null,
                        thumbnail = thumbnail,
                        expandedPhoto = thumbnail?.takeIf { isExpanded },
                        expanded = isExpanded,
                        onFlightFinished = {},
                        onThumbnailClick = { isExpanded = true },
                        onCollapse = { isExpanded = false },
                        onCapture = onCapture,
                        onClose = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun fixture(landscape: Boolean): Bitmap {
        val width = if (landscape) 640 else 480
        val height = if (landscape) 480 else 640
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmaps += bitmap
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            canvas.drawColor(android.graphics.Color.rgb(211, 193, 163))
            paint.color = android.graphics.Color.WHITE
            canvas.drawCircle(width / 2f, height / 2f, 180f, paint)
            paint.color = android.graphics.Color.rgb(92, 139, 65)
            canvas.drawCircle(width / 2f, height / 2f, 120f, paint)
            paint.color = android.graphics.Color.rgb(225, 106, 51)
            canvas.drawCircle(width / 2f - 50, height / 2f - 35, 42f, paint)
            canvas.drawCircle(width / 2f + 55, height / 2f + 45, 36f, paint)
            paint.color = android.graphics.Color.DKGRAY
            paint.textSize = 24f
            canvas.drawText("OBEN / TOP", 12f, 32f, paint)
            canvas.drawText("UNTEN / BOTTOM", 12f, height - 12f, paint)
        }
    }

    private fun capture(name: String) {
        compose.onNode(isRoot()).captureRoboImage("QuickCaptureCameraScreenshotTest.$name.png")
    }
}

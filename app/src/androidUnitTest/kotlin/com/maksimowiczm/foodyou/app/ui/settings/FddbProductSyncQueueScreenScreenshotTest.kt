package com.maksimowiczm.foodyou.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.locale
import com.github.takahirom.roborazzi.size
import com.maksimowiczm.foodyou.food.domain.entity.FddbProductSyncQueueItem
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import kotlin.time.Instant
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "de-rDE-w390dp-h844dp-mdpi")
@OptIn(ExperimentalRoborazziApi::class)
class FddbProductSyncQueueScreenScreenshotTest {
    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun failedProductDetails() {
        capture("details", confirmUnlink = false)
    }

    @Test
    fun unlinkConfirmation() {
        capture("unlink-confirmation", confirmUnlink = true)
    }

    private fun capture(name: String, confirmUnlink: Boolean) {
        captureRoboImage(
            filePath = "FddbProductSyncQueueScreenScreenshotTest.$name.png",
            roborazziComposeOptions =
                RoborazziComposeOptions.Builder().size(390, 844).locale("de-rDE").build(),
        ) {
            Golden(confirmUnlink)
        }
    }

    @Composable
    private fun Golden(confirmUnlink: Boolean) {
        MaterialTheme {
            Box(
                modifier =
                    Modifier.requiredSize(390.dp, 844.dp)
                        .background(Color(0xFFF9F9FF))
            ) {
                FddbProductSyncQueueContent(
                    model = FddbProductSyncQueueModel(progress = 2, queue = listOf(Item)),
                    actionState = FddbProductSyncActionState(),
                    onBack = {},
                    onOpenUrl = {},
                    onRetry = {},
                    onUpdateLink = { _, _ -> },
                    onUnlink = {},
                    onClearActionError = {},
                    modifier = Modifier.fillMaxSize(),
                    initialSelectedProductId = Item.productId.id,
                    initialConfirmUnlink = confirmUnlink,
                )
            }
        }
    }

    private companion object {
        val Item =
            FddbProductSyncQueueItem(
                productId = FoodId.Product(42),
                name = "Bio Haferdrink Natur",
                brand = "Beispielmarke",
                sourceUrl =
                    "https://fddb.info/db/de/lebensmittel/beispielmarke_bio_haferdrink/index.html",
                lastSyncedAt = Instant.parse("2026-09-20T08:15:00Z"),
                lastAttemptAt = Instant.parse("2026-09-25T10:30:00Z"),
                lastError = "FDDB page not found (HTTP 404)",
            )
    }
}

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
import com.maksimowiczm.foodyou.food.domain.usecase.FddbProductSyncBatchProgress
import com.maksimowiczm.foodyou.food.domain.usecase.FddbProductSyncManualBatchState
import com.maksimowiczm.foodyou.settings.domain.entity.FddbProductSyncManualFrequency
import com.maksimowiczm.foodyou.settings.domain.entity.FddbProductSyncMode
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

    @Test
    fun overview() {
        capture(name = "overview", selectedProductId = null)
    }

    @Test
    fun overviewRemainingExpanded() {
        capture(name = "overview-expanded", selectedProductId = null, remainingExpanded = true)
    }

    @Test
    fun manualBatchCompleted() {
        capture(
            name = "manual-batch-completed",
            selectedProductId = null,
            batchState =
                FddbProductSyncManualBatchState.Completed(
                    FddbProductSyncBatchProgress(
                        total = 5,
                        processed = 5,
                        synced = 5,
                        failed = 0,
                        blocked = false,
                    )
                ),
        )
    }

    @Test
    fun manualBatchRunning() {
        capture(
            name = "manual-batch-running",
            selectedProductId = null,
            batchState =
                FddbProductSyncManualBatchState.Running(
                    FddbProductSyncBatchProgress(
                        total = 15,
                        processed = 6,
                        synced = 5,
                        failed = 1,
                        blocked = false,
                    )
                ),
        )
    }

    @Test
    fun manualBatchBlocked() {
        capture(
            name = "manual-batch-blocked",
            selectedProductId = null,
            batchState =
                FddbProductSyncManualBatchState.Completed(
                    FddbProductSyncBatchProgress(
                        total = 15,
                        processed = 7,
                        synced = 6,
                        failed = 1,
                        blocked = true,
                    )
                ),
        )
    }

    private fun capture(
        name: String,
        confirmUnlink: Boolean = false,
        selectedProductId: Long? = Item.productId.id,
        batchState: FddbProductSyncManualBatchState = FddbProductSyncManualBatchState.Idle,
        remainingExpanded: Boolean = false,
    ) {
        captureRoboImage(
            filePath = "FddbProductSyncQueueScreenScreenshotTest.$name.png",
            roborazziComposeOptions =
                RoborazziComposeOptions.Builder().size(390, 844).locale("de-rDE").build(),
        ) {
            Golden(confirmUnlink, selectedProductId, batchState, remainingExpanded)
        }
    }

    @Composable
    private fun Golden(
        confirmUnlink: Boolean,
        selectedProductId: Long?,
        batchState: FddbProductSyncManualBatchState,
        remainingExpanded: Boolean,
    ) {
        MaterialTheme {
            Box(
                modifier =
                    Modifier.requiredSize(390.dp, 844.dp)
                        .background(Color(0xFFF9F9FF))
            ) {
                FddbProductSyncQueueContent(
                    model =
                        FddbProductSyncQueueModel(
                            syncMode = FddbProductSyncMode.WithManualFddbSync,
                            manualFrequency = FddbProductSyncManualFrequency.EveryThirdSync,
                            manualTriggerCount = 0,
                            manualBatchState = batchState,
                            queue = Queue,
                        ),
                    actionState = FddbProductSyncActionState(),
                    onBack = {},
                    onOpenUrl = {},
                    onRetry = {},
                    onUpdateLink = { _, _ -> },
                    onUnlink = {},
                    onClearActionError = {},
                    onStartManualBatch = {},
                    onClearManualBatchResult = {},
                    modifier = Modifier.fillMaxSize(),
                    initialSelectedProductId = selectedProductId,
                    initialConfirmUnlink = confirmUnlink,
                    initialRemainingExpanded = remainingExpanded,
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

        val Queue =
            listOf(
                queueItem(1, "Maisgebäck, mit dem Geschmack von sauren Äpfeln", "Chrupki", null),
                Item,
                queueItem(2, "Steinofenbaguette", "Metro", null),
                queueItem(3, "Eiklar", "Beispielmarke", "2026-08-12T07:49:00Z"),
                queueItem(4, "Earth Champ Protein Vanilla", "Earthchamp", "2026-08-12T07:49:00Z"),
                queueItem(5, "Skyr Natur", "Beispielmarke", "2026-08-20T09:00:00Z"),
                queueItem(6, "Vollkorn-Toast", null, "2026-09-01T09:00:00Z"),
                queueItem(7, "Hafer Porridge Zimt", "Beispielmarke", "2026-09-10T09:00:00Z"),
                queueItem(8, "Erdnussbutter Crunchy", "Beispielmarke", "2026-09-18T09:00:00Z"),
            )

        fun queueItem(id: Long, name: String, brand: String?, syncedAt: String?) =
            FddbProductSyncQueueItem(
                productId = FoodId.Product(id),
                name = name,
                brand = brand,
                sourceUrl = "https://fddb.info/db/de/lebensmittel/product_$id/index.html",
                lastSyncedAt = syncedAt?.let(Instant::parse),
                lastAttemptAt = syncedAt?.let(Instant::parse),
                lastError = null,
            )
    }
}

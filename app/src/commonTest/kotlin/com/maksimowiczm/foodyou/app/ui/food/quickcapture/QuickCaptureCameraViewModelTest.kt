package com.maksimowiczm.foodyou.app.ui.food.quickcapture

import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.food.domain.entity.QuickCaptureFoodName
import com.maksimowiczm.foodyou.food.domain.entity.QuickCaptureLogEntry
import com.maksimowiczm.foodyou.food.domain.entity.QuickCaptureWeightMode
import com.maksimowiczm.foodyou.food.domain.repository.QuickCaptureRepository
import com.maksimowiczm.foodyou.food.domain.usecase.CaptureQuickCapturePhotoUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.ObserveQuickCaptureUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

class QuickCaptureCameraViewModelTest {
    @Test
    fun countsPendingPhotosAndCapturesEachPathOnce() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        var viewModel: QuickCaptureCameraViewModel? = null
        try {
            val repository = RecordingQuickCaptureRepository()
            val cameraViewModel =
                QuickCaptureCameraViewModel(
                    observe = ObserveQuickCaptureUseCase(repository),
                    capture = CaptureQuickCapturePhotoUseCase(repository, FixedDateProvider),
                )
            viewModel = cameraViewModel
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                cameraViewModel.photoCount.collect {}
            }

            repository.entries.value =
                listOf(
                    pendingPhoto(id = 1, path = "first.jpg"),
                    pendingPhoto(id = 2, path = "second.jpg"),
                    completedEntry(id = 3),
                )
            advanceUntilIdle()

            assertEquals(2, cameraViewModel.photoCount.value)

            cameraViewModel.capturePhoto("third.jpg")
            advanceUntilIdle()

            assertEquals(listOf("third.jpg"), repository.capturedPaths)
        } finally {
            viewModel?.viewModelScope?.cancel()
            advanceUntilIdle()
            Dispatchers.resetMain()
        }
    }
}

private class RecordingQuickCaptureRepository : QuickCaptureRepository {
    val entries = MutableStateFlow<List<QuickCaptureLogEntry>>(emptyList())
    val capturedPaths = mutableListOf<String>()

    override fun observeFoodNames(): Flow<List<QuickCaptureFoodName>> = flowOf(emptyList())

    override fun observeEntries(): Flow<List<QuickCaptureLogEntry>> = entries

    override fun observeEntry(id: Long): Flow<QuickCaptureLogEntry?> = flowOf(null)

    override suspend fun capturePhoto(photoPath: String, createdAt: Instant): Long {
        capturedPaths += photoPath
        return 42
    }

    override suspend fun createEntry(
        foodName: String,
        weightMode: QuickCaptureWeightMode,
        directWeightInGrams: Double?,
        beforeWeightInGrams: Double?,
        afterWeightInGrams: Double?,
        createdAt: Instant,
    ): Long = error("Not used")

    override suspend fun processPhoto(
        id: Long,
        foodName: String,
        weightInGrams: Double,
        usedAt: Instant,
    ) = error("Not used")

    override suspend fun setAfterWeight(id: Long, afterWeightInGrams: Double) = error("Not used")

    override suspend fun markCompleted(ids: List<Long>, completedAt: Instant) = error("Not used")

    override suspend fun deleteEntries(ids: List<Long>) = error("Not used")

    override suspend fun renameFoodName(id: Long, name: String, usedAt: Instant) = error("Not used")

    override suspend fun deleteFoodName(id: Long) = error("Not used")
}

private object FixedDateProvider : DateProvider {
    private val now = Instant.parse("2026-09-19T12:00:00Z")

    override fun nowInstant(): Instant = now

    override fun observeInstant(interval: Duration): Flow<Instant> = flowOf(now)

    override fun observeDate(timeZone: TimeZone): Flow<LocalDate> = flowOf(LocalDate(2026, 9, 19))
}

private fun pendingPhoto(id: Long, path: String) =
    QuickCaptureLogEntry(
        id = id,
        foodNameId = null,
        foodName = null,
        weightMode = QuickCaptureWeightMode.Direct,
        directWeightInGrams = null,
        beforeWeightInGrams = null,
        afterWeightInGrams = null,
        photoPath = path,
        createdAt = Instant.parse("2026-09-19T12:00:00Z"),
        completedAt = null,
    )

private fun completedEntry(id: Long) =
    QuickCaptureLogEntry(
        id = id,
        foodNameId = 1,
        foodName = "Skyr",
        weightMode = QuickCaptureWeightMode.Direct,
        directWeightInGrams = 200.0,
        beforeWeightInGrams = null,
        afterWeightInGrams = null,
        photoPath = null,
        createdAt = Instant.parse("2026-09-19T12:00:00Z"),
        completedAt = Instant.parse("2026-09-19T12:01:00Z"),
    )

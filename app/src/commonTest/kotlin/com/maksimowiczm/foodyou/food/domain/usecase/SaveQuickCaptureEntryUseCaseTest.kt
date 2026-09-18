package com.maksimowiczm.foodyou.food.domain.usecase

import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.food.domain.entity.QuickCaptureFoodName
import com.maksimowiczm.foodyou.food.domain.entity.QuickCaptureLogEntry
import com.maksimowiczm.foodyou.food.domain.entity.QuickCaptureWeightMode
import com.maksimowiczm.foodyou.food.domain.repository.QuickCaptureRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

class SaveQuickCaptureEntryUseCaseTest {
    @Test
    fun beforeWithoutAfterIsSavedAsPendingEntry() = runTest {
        val repository = RecordingQuickCaptureRepository()
        val useCase = SaveQuickCaptureEntryUseCase(repository, FixedDateProvider)

        val result =
            useCase.save(
                foodName = "  Greek   Yoghurt  ",
                weightMode = QuickCaptureWeightMode.BeforeAfter,
                beforeWeightInGrams = 410.0,
                afterWeightInGrams = null,
            )

        assertEquals(SaveQuickCaptureEntryResult.Saved(42), result)
        assertEquals("Greek Yoghurt", repository.created?.foodName)
        assertEquals(410.0, repository.created?.before)
        assertEquals(null, repository.created?.after)
    }

    @Test
    fun beforeAfterStillRejectsInvalidDifference() = runTest {
        val repository = RecordingQuickCaptureRepository()
        val useCase = SaveQuickCaptureEntryUseCase(repository, FixedDateProvider)

        val result =
            useCase.save(
                foodName = "Skyr",
                weightMode = QuickCaptureWeightMode.BeforeAfter,
                beforeWeightInGrams = 200.0,
                afterWeightInGrams = 200.0,
            )

        assertEquals(SaveQuickCaptureEntryResult.InvalidWeight, result)
        assertEquals(null, repository.created)
    }
}

private data class CreatedEntry(val foodName: String, val before: Double?, val after: Double?)

private class RecordingQuickCaptureRepository : QuickCaptureRepository {
    var created: CreatedEntry? = null

    override fun observeFoodNames(): Flow<List<QuickCaptureFoodName>> = flowOf(emptyList())

    override fun observeEntries(): Flow<List<QuickCaptureLogEntry>> = flowOf(emptyList())

    override fun observeEntry(id: Long): Flow<QuickCaptureLogEntry?> = flowOf(null)

    override suspend fun createEntry(
        foodName: String,
        weightMode: QuickCaptureWeightMode,
        directWeightInGrams: Double?,
        beforeWeightInGrams: Double?,
        afterWeightInGrams: Double?,
        createdAt: Instant,
    ): Long {
        created = CreatedEntry(foodName, beforeWeightInGrams, afterWeightInGrams)
        return 42
    }

    override suspend fun capturePhoto(photoPath: String, createdAt: Instant): Long = error("Not used")

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
    private val instant = Instant.parse("2026-09-18T12:00:00Z")

    override fun nowInstant(): Instant = instant

    override fun observeInstant(interval: Duration): Flow<Instant> = flowOf(instant)

    override fun observeDate(timeZone: TimeZone): Flow<LocalDate> = flowOf(LocalDate(2026, 9, 18))
}

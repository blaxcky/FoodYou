package com.maksimowiczm.foodyou.app.ui.food.quickcapture

import com.maksimowiczm.foodyou.app.ui.food.diary.quickadd.QuickAddCsvData
import com.maksimowiczm.foodyou.common.domain.database.TransactionProvider
import com.maksimowiczm.foodyou.common.domain.database.TransactionScope
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.food.domain.entity.QuickCaptureFoodName
import com.maksimowiczm.foodyou.food.domain.entity.QuickCaptureLogEntry
import com.maksimowiczm.foodyou.food.domain.entity.QuickCaptureWeightMode
import com.maksimowiczm.foodyou.food.domain.repository.QuickCaptureRepository
import com.maksimowiczm.foodyou.fooddiary.domain.entity.ManualDiaryEntry
import com.maksimowiczm.foodyou.fooddiary.domain.entity.ManualDiaryEntryId
import com.maksimowiczm.foodyou.fooddiary.domain.entity.Meal
import com.maksimowiczm.foodyou.fooddiary.domain.repository.ManualDiaryEntryRepository
import com.maksimowiczm.foodyou.fooddiary.domain.repository.MealRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone

class QuickCaptureCsvImporterTest {
    @Test
    fun createsOneQuickAddAndCompletesOnlyCopiedEntries() = runTest {
        val manualEntries = RecordingManualDiaryEntryRepository()
        val quickCapture = CsvImportRecordingQuickCaptureRepository()
        val importer = importer(manualEntries, quickCapture, listOf(Lunch, Breakfast))

        val result = importer.import(CsvData, listOf(8, 3, 8))

        assertEquals(QuickCaptureCsvImportResult.Success, result)
        assertEquals(1, manualEntries.inserted.size)
        assertEquals("Mittagessen", manualEntries.inserted.single().name)
        assertEquals(Lunch.id, manualEntries.inserted.single().mealId)
        assertEquals(640.0, manualEntries.inserted.single().nutritionFacts.energy.value)
        assertEquals(listOf(8L, 3L), quickCapture.completedIds)
    }

    @Test
    fun missingMealChangesNothing() = runTest {
        val manualEntries = RecordingManualDiaryEntryRepository()
        val quickCapture = CsvImportRecordingQuickCaptureRepository()
        val importer = importer(manualEntries, quickCapture, emptyList())

        val result = importer.import(CsvData, listOf(8, 3))

        assertEquals(QuickCaptureCsvImportResult.NoMeal, result)
        assertTrue(manualEntries.inserted.isEmpty())
        assertTrue(quickCapture.completedIds.isEmpty())
    }

    @Test
    fun failedDiaryInsertDoesNotCompleteEntries() = runTest {
        val manualEntries = RecordingManualDiaryEntryRepository(failInsert = true)
        val quickCapture = CsvImportRecordingQuickCaptureRepository()
        val importer = importer(manualEntries, quickCapture, listOf(Lunch))

        assertFailsWith<IllegalStateException> { importer.import(CsvData, listOf(8, 3)) }
        assertTrue(quickCapture.completedIds.isEmpty())
    }

    private fun importer(
        manualEntries: RecordingManualDiaryEntryRepository,
        quickCapture: CsvImportRecordingQuickCaptureRepository,
        meals: List<Meal>,
    ) =
        QuickCaptureCsvImporterImpl(
            dateProvider = CsvImportFixedDateProvider,
            mealRepository = FixedMealRepository(meals),
            manualDiaryEntryRepository = manualEntries,
            quickCaptureRepository = quickCapture,
            transactionProvider = ImmediateTransactionProvider,
        )

    private companion object {
        val Breakfast = Meal(1, "Breakfast", LocalTime(6, 0), LocalTime(10, 0), 0)
        val Lunch = Meal(2, "Lunch", LocalTime(10, 0), LocalTime(18, 0), 1)
        val CsvData = QuickAddCsvData("Mittagessen", 640.0, 42.0, 71.0, 19.0)
    }
}

private data class InsertedManualEntry(
    val name: String,
    val mealId: Long,
    val date: LocalDate,
    val nutritionFacts: NutritionFacts,
    val createdAt: LocalDateTime,
)

private class RecordingManualDiaryEntryRepository(private val failInsert: Boolean = false) :
    ManualDiaryEntryRepository {
    val inserted = mutableListOf<InsertedManualEntry>()

    override fun observe(id: ManualDiaryEntryId): Flow<ManualDiaryEntry?> = flowOf(null)

    override fun observeAll(mealId: Long, date: LocalDate): Flow<List<ManualDiaryEntry>> =
        flowOf(emptyList())

    override suspend fun insert(
        name: String,
        mealId: Long,
        date: LocalDate,
        nutritionFacts: NutritionFacts,
        createdAt: LocalDateTime,
    ): ManualDiaryEntryId {
        if (failInsert) error("insert failed")
        inserted += InsertedManualEntry(name, mealId, date, nutritionFacts, createdAt)
        return ManualDiaryEntryId(inserted.size.toLong())
    }

    override suspend fun update(entry: ManualDiaryEntry) = error("Not used")

    override suspend fun delete(id: ManualDiaryEntryId) = error("Not used")
}

private class CsvImportRecordingQuickCaptureRepository : QuickCaptureRepository {
    val completedIds = mutableListOf<Long>()

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
    ): Long = error("Not used")

    override suspend fun capturePhoto(photoPath: String, createdAt: Instant): Long =
        error("Not used")

    override suspend fun processPhoto(
        id: Long,
        foodName: String,
        weightInGrams: Double,
        usedAt: Instant,
    ) = error("Not used")

    override suspend fun setAfterWeight(id: Long, afterWeightInGrams: Double) = error("Not used")

    override suspend fun markCompleted(ids: List<Long>, completedAt: Instant) {
        completedIds += ids
    }

    override suspend fun deleteEntries(ids: List<Long>) = error("Not used")

    override suspend fun renameFoodName(id: Long, name: String, usedAt: Instant) =
        error("Not used")

    override suspend fun deleteFoodName(id: Long) = error("Not used")
}

private class FixedMealRepository(private val meals: List<Meal>) : MealRepository {
    override fun observeMeal(mealId: Long): Flow<Meal?> =
        flowOf(meals.firstOrNull { it.id == mealId })

    override fun observeMeals(): Flow<List<Meal>> = flowOf(meals)

    override suspend fun insertMealWithLastRank(name: String, from: LocalTime, to: LocalTime) =
        error("Not used")

    override suspend fun deleteMeal(mealId: Long) = error("Not used")

    override suspend fun updateMeal(id: Long, name: String, from: LocalTime, to: LocalTime) =
        error("Not used")

    override suspend fun reorderMeals(order: List<Long>) = error("Not used")
}

private object CsvImportFixedDateProvider : DateProvider {
    private val instant = Instant.parse("2026-09-19T12:00:00Z")

    override fun nowInstant(): Instant = instant

    override fun observeInstant(interval: Duration): Flow<Instant> = flowOf(instant)

    override fun observeDate(timeZone: TimeZone): Flow<LocalDate> =
        flowOf(LocalDate(2026, 9, 19))
}

private object ImmediateTransactionProvider : TransactionProvider {
    override suspend fun <T> withTransaction(block: suspend TransactionScope<T>.() -> T): T =
        block(
            object : TransactionScope<T> {
                override suspend fun rollback(result: T) = error("Not used")
            }
        )
}

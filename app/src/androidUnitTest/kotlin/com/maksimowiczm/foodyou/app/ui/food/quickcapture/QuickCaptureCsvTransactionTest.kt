package com.maksimowiczm.foodyou.app.ui.food.quickcapture

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.maksimowiczm.foodyou.app.infrastructure.room.FoodYouDatabase
import com.maksimowiczm.foodyou.app.ui.food.diary.quickadd.QuickAddCsvData
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.food.domain.entity.QuickCaptureWeightMode
import com.maksimowiczm.foodyou.food.domain.repository.QuickCaptureRepository
import com.maksimowiczm.foodyou.food.infrastructure.repository.RoomQuickCaptureRepository
import com.maksimowiczm.foodyou.fooddiary.domain.entity.ManualDiaryEntryId
import com.maksimowiczm.foodyou.fooddiary.domain.repository.ManualDiaryEntryRepository
import com.maksimowiczm.foodyou.fooddiary.infrastructure.repository.RoomManualDiaryEntryRepository
import com.maksimowiczm.foodyou.fooddiary.infrastructure.repository.RoomMealRepository
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class QuickCaptureCsvTransactionTest {
    private lateinit var database: FoodYouDatabase
    private lateinit var diary: ManualDiaryEntryRepository
    private lateinit var quickCapture: QuickCaptureRepository
    private lateinit var meals: RoomMealRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), FoodYouDatabase::class.java,
        ).allowMainThreadQueries().build()
        diary = RoomManualDiaryEntryRepository(database.manualDiaryEntryDao)
        quickCapture = RoomQuickCaptureRepository(database.quickCaptureDao)
        meals = RoomMealRepository(database.mealDao)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun multipleRowsCommitTogetherAndOnlyCopiedEntriesAreCompleted() = runTest {
        val mealId = createMeal()
        val copied = createEntry("Skyr")
        val later = createEntry("Später hinzugefügt")

        assertEquals(
            QuickCaptureCsvImportResult.Success,
            importer().import(Rows, listOf(copied)),
        )
        val entries = diary.observeAll(mealId, FixedDate.now().date).first()
        assertEquals(Rows.map { it.name }, entries.sortedBy { it.id.value }.map { it.name })
        assertEquals(Rows.map { it.energyKcal }, entries.sortedBy { it.id.value }.map { it.nutritionFacts.energy.value })
        assertEquals(FixedDate.nowInstant(), quickCapture.observeEntry(copied).first()?.completedAt)
        assertNull(quickCapture.observeEntry(later).first()?.completedAt)
    }

    @Test
    fun failureOnSecondInsertRollsBackFirstAndPreservesExistingDiary() = runTest {
        val mealId = createMeal()
        val copied = createEntry("Skyr")
        val existing = diary.insert("Vorhanden", mealId, FixedDate.now().date, NutritionFacts(), FixedDate.now())
        var attempts = 0
        val failingDiary = object : ManualDiaryEntryRepository by diary {
            override suspend fun insert(
                name: String,
                mealId: Long,
                date: LocalDate,
                nutritionFacts: NutritionFacts,
                createdAt: LocalDateTime,
            ): ManualDiaryEntryId {
                attempts += 1
                if (attempts == 2) error("second insert failed")
                return diary.insert(name, mealId, date, nutritionFacts, createdAt)
            }
        }

        assertFailsWith<IllegalStateException> {
            importer(diaryRepository = failingDiary).import(Rows, listOf(copied))
        }
        assertEquals(2, attempts)
        assertEquals(listOf(existing), diary.observeAll(mealId, FixedDate.now().date).first().map { it.id })
        assertNull(quickCapture.observeEntry(copied).first()?.completedAt)
    }

    @Test
    fun completionFailureRollsBackDiaryAndCompletedStatus() = runTest {
        val mealId = createMeal()
        val copied = createEntry("Skyr")
        val failingCompletion = object : QuickCaptureRepository by quickCapture {
            override suspend fun markCompleted(ids: List<Long>, completedAt: Instant) {
                quickCapture.markCompleted(ids, completedAt)
                error("completion failed")
            }
        }

        assertFailsWith<IllegalStateException> {
            importer(captureRepository = failingCompletion).import(Rows, listOf(copied))
        }
        assertEquals(emptyList(), diary.observeAll(mealId, FixedDate.now().date).first())
        assertNull(quickCapture.observeEntry(copied).first()?.completedAt)
    }

    private fun importer(
        diaryRepository: ManualDiaryEntryRepository = diary,
        captureRepository: QuickCaptureRepository = quickCapture,
    ) = QuickCaptureCsvImporterImpl(FixedDate, meals, diaryRepository, captureRepository, database)

    private suspend fun createMeal(): Long {
        meals.insertMealWithLastRank("Mahlzeit", LocalTime(0, 0), LocalTime(23, 59))
        return meals.observeMeals().first().single().id
    }

    private suspend fun createEntry(name: String): Long = quickCapture.createEntry(
        name, QuickCaptureWeightMode.Direct, 200.0, null, null, FixedDate.nowInstant(),
    )

    private companion object {
        val Rows = listOf(
            QuickAddCsvData("Skyr", 126.0, 22.0, 8.0, 0.4),
            QuickAddCsvData("Apfel", 80.0, 0.4, 18.0, 0.2),
        )
        val FixedDate = object : DateProvider {
            override fun nowInstant(): Instant = Instant.parse("2026-09-19T12:00:00Z")
            override fun observeInstant(interval: Duration): Flow<Instant> = flowOf(nowInstant())
            override fun observeDate(timeZone: TimeZone): Flow<LocalDate> = flowOf(now(timeZone).date)
        }
    }
}

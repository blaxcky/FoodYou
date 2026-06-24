package com.maksimowiczm.foodyou.food.domain.usecase

import com.maksimowiczm.foodyou.common.domain.database.TransactionProvider
import com.maksimowiczm.foodyou.common.domain.database.TransactionScope
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.FoodSnapEntry
import com.maksimowiczm.foodyou.food.domain.entity.Product
import com.maksimowiczm.foodyou.food.domain.repository.FoodSnapPhotoStorage
import com.maksimowiczm.foodyou.food.domain.repository.FoodSnapRepository
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFood
import com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntry
import com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntryId
import com.maksimowiczm.foodyou.fooddiary.domain.entity.Meal
import com.maksimowiczm.foodyou.fooddiary.domain.repository.FoodDiaryEntryRepository
import com.maksimowiczm.foodyou.fooddiary.domain.repository.MealRepository
import com.maksimowiczm.foodyou.fooddiary.domain.usecase.CreateFoodDiaryEntryUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone

class CompleteFoodSnapEntryUseCaseTest {
    @Test
    fun createsDiaryEntryAndDeletesSnapAndPhoto() = runBlocking {
        val snapRepository = FakeFoodSnapRepository(entry())
        val diaryRepository = FakeFoodDiaryEntryRepository()
        val photoStorage = FakeFoodSnapPhotoStorage()
        val useCase = useCase(snapRepository, diaryRepository, photoStorage)

        val result = useCase.complete(entry(), food(isLiquid = false), 125.0, MealId, Date)

        assertEquals(CompleteFoodSnapEntryResult.Completed, result)
        assertEquals(1, diaryRepository.inserted.size)
        assertEquals(MealId, diaryRepository.inserted.single().mealId)
        assertEquals(Date, diaryRepository.inserted.single().date)
        assertTrue(diaryRepository.inserted.single().measurement is Measurement.Gram)
        assertEquals(125.0, (diaryRepository.inserted.single().measurement as Measurement.Gram).value)
        assertEquals(listOf(entry()), snapRepository.deleted)
        assertEquals(listOf(entry().photoPath), photoStorage.deleted)
    }

    @Test
    fun usesMilliliterForLiquidFood() = runBlocking {
        val diaryRepository = FakeFoodDiaryEntryRepository()
        val useCase = useCase(diaryRepository = diaryRepository)

        val result = useCase.complete(entry(), food(isLiquid = true), 250.0, MealId, Date)

        assertEquals(CompleteFoodSnapEntryResult.Completed, result)
        assertTrue(diaryRepository.inserted.single().measurement is Measurement.Milliliter)
        assertEquals(250.0, (diaryRepository.inserted.single().measurement as Measurement.Milliliter).value)
    }

    @Test
    fun invalidWeightDoesNotCreateDiaryEntryOrDeleteSnap() = runBlocking {
        val snapRepository = FakeFoodSnapRepository(entry())
        val diaryRepository = FakeFoodDiaryEntryRepository()
        val photoStorage = FakeFoodSnapPhotoStorage()
        val useCase = useCase(snapRepository, diaryRepository, photoStorage)

        val result = useCase.complete(entry(), food(isLiquid = false), 0.0, MealId, Date)

        assertEquals(CompleteFoodSnapEntryResult.InvalidWeight, result)
        assertEquals(emptyList(), diaryRepository.inserted)
        assertEquals(emptyList(), snapRepository.deleted)
        assertEquals(emptyList(), photoStorage.deleted)
    }

    @Test
    fun missingFoodDoesNotCreateDiaryEntryOrDeleteSnap() = runBlocking {
        val snapRepository = FakeFoodSnapRepository(entry())
        val diaryRepository = FakeFoodDiaryEntryRepository()
        val photoStorage = FakeFoodSnapPhotoStorage()
        val useCase = useCase(snapRepository, diaryRepository, photoStorage)

        val result = useCase.complete(entry(), null, 100.0, MealId, Date)

        assertEquals(CompleteFoodSnapEntryResult.MissingFood, result)
        assertEquals(emptyList(), diaryRepository.inserted)
        assertEquals(emptyList(), snapRepository.deleted)
        assertEquals(emptyList(), photoStorage.deleted)
    }

    @Test
    fun missingMealDoesNotCreateDiaryEntryOrDeleteSnap() = runBlocking {
        val snapRepository = FakeFoodSnapRepository(entry())
        val diaryRepository = FakeFoodDiaryEntryRepository()
        val photoStorage = FakeFoodSnapPhotoStorage()
        val useCase =
            useCase(
                snapRepository = snapRepository,
                diaryRepository = diaryRepository,
                photoStorage = photoStorage,
                mealRepository = FakeMealRepository(emptyList()),
            )

        val result = useCase.complete(entry(), food(isLiquid = false), 100.0, MealId, Date)

        assertEquals(CompleteFoodSnapEntryResult.MissingMeal, result)
        assertEquals(emptyList(), diaryRepository.inserted)
        assertEquals(emptyList(), snapRepository.deleted)
        assertEquals(emptyList(), photoStorage.deleted)
    }

    @Test
    fun missingSnapDoesNotCreateDiaryEntryOrDeletePhoto() = runBlocking {
        val snapRepository = FakeFoodSnapRepository()
        val diaryRepository = FakeFoodDiaryEntryRepository()
        val photoStorage = FakeFoodSnapPhotoStorage()
        val useCase = useCase(snapRepository, diaryRepository, photoStorage)

        val result = useCase.complete(entry(), food(isLiquid = false), 100.0, MealId, Date)

        assertEquals(CompleteFoodSnapEntryResult.MissingEntry, result)
        assertEquals(emptyList(), diaryRepository.inserted)
        assertEquals(emptyList(), photoStorage.deleted)
    }

    @Test
    fun snapAndPhotoAreKeptWhenDiaryInsertFails() = runBlocking {
        val snapRepository = FakeFoodSnapRepository(entry())
        val diaryRepository = FakeFoodDiaryEntryRepository()
        val photoStorage = FakeFoodSnapPhotoStorage()
        val useCase =
            useCase(
                snapRepository = snapRepository,
                diaryRepository = diaryRepository,
                photoStorage = photoStorage,
                createMealRepository = FakeMealRepository(emptyList()),
            )

        val result = useCase.complete(entry(), food(isLiquid = false), 100.0, MealId, Date)

        assertEquals(CompleteFoodSnapEntryResult.MissingMeal, result)
        assertEquals(emptyList(), diaryRepository.inserted)
        assertEquals(emptyList(), snapRepository.deleted)
        assertEquals(emptyList(), photoStorage.deleted)
    }

    private fun useCase(
        snapRepository: FakeFoodSnapRepository = FakeFoodSnapRepository(entry()),
        diaryRepository: FakeFoodDiaryEntryRepository = FakeFoodDiaryEntryRepository(),
        photoStorage: FakeFoodSnapPhotoStorage = FakeFoodSnapPhotoStorage(),
        mealRepository: MealRepository = FakeMealRepository(listOf(meal())),
        createMealRepository: MealRepository = mealRepository,
    ) =
        CompleteFoodSnapEntryUseCase(
            repository = snapRepository,
            mealRepository = mealRepository,
            createFoodDiaryEntry =
                CreateFoodDiaryEntryUseCase(
                    mealRepository = createMealRepository,
                    entryRepository = diaryRepository,
                    transactionProvider = ImmediateTransactionProvider,
                    dateProvider = FixedDateProvider,
                    logger = NoopLogger,
                ),
            photoStorage = photoStorage,
            transactionProvider = ImmediateTransactionProvider,
        )

    private class FakeFoodSnapRepository(vararg entries: FoodSnapEntry) : FoodSnapRepository {
        private val entries = entries.associateBy { it.id }.toMutableMap()
        val deleted = mutableListOf<FoodSnapEntry>()

        override fun observeEntries(): Flow<List<FoodSnapEntry>> = flowOf(entries.values.toList())

        override fun observeEntry(id: Long): Flow<FoodSnapEntry?> = flowOf(entries[id])

        override suspend fun insert(photoPath: String, createdAt: Instant): Long = 0

        override suspend fun update(entry: FoodSnapEntry) = Unit

        override suspend fun delete(entry: FoodSnapEntry) {
            entries.remove(entry.id)
            deleted += entry
        }
    }

    private class FakeFoodSnapPhotoStorage : FoodSnapPhotoStorage {
        val deleted = mutableListOf<String>()

        override fun delete(photoPath: String) {
            deleted += photoPath
        }
    }

    private class FakeFoodDiaryEntryRepository : FoodDiaryEntryRepository {
        data class Inserted(
            val measurement: Measurement,
            val mealId: Long,
            val date: LocalDate,
            val food: DiaryFood,
        )

        val inserted = mutableListOf<Inserted>()

        override fun observe(id: FoodDiaryEntryId): Flow<FoodDiaryEntry?> = emptyFlow()

        override fun observeAll(mealId: Long, date: LocalDate): Flow<List<FoodDiaryEntry>> = emptyFlow()

        override suspend fun insert(
            measurement: Measurement,
            mealId: Long,
            date: LocalDate,
            food: DiaryFood,
            createdAt: LocalDateTime,
        ): FoodDiaryEntryId {
            inserted += Inserted(measurement, mealId, date, food)
            return FoodDiaryEntryId(inserted.size.toLong())
        }

        override suspend fun update(entry: FoodDiaryEntry) = Unit

        override suspend fun delete(id: FoodDiaryEntryId) = Unit
    }

    private class FakeMealRepository(private val meals: List<Meal>) : MealRepository {
        override fun observeMeal(mealId: Long): Flow<Meal?> = flowOf(meals.firstOrNull { it.id == mealId })

        override fun observeMeals(): Flow<List<Meal>> = flowOf(meals)

        override suspend fun insertMealWithLastRank(name: String, from: LocalTime, to: LocalTime) = Unit

        override suspend fun deleteMeal(mealId: Long) = Unit

        override suspend fun updateMeal(id: Long, name: String, from: LocalTime, to: LocalTime) = Unit

        override suspend fun reorderMeals(order: List<Long>) = Unit
    }

    private object ImmediateTransactionProvider : TransactionProvider {
        override suspend fun <T> withTransaction(block: suspend TransactionScope<T>.() -> T): T =
            block(
                object : TransactionScope<T> {
                    override suspend fun rollback(result: T) = Unit
                }
            )
    }

    private object FixedDateProvider : DateProvider {
        override fun nowInstant(): Instant = Instant.parse("2026-06-24T10:00:00Z")

        override fun observeInstant(interval: Duration): Flow<Instant> = emptyFlow()

        override fun observeDate(timeZone: TimeZone): Flow<LocalDate> = emptyFlow()
    }

    private object NoopLogger : Logger {
        override fun d(tag: String, throwable: Throwable?, message: () -> String) = Unit

        override fun w(tag: String, throwable: Throwable?, message: () -> String) = Unit

        override fun e(tag: String, throwable: Throwable?, message: () -> String) = Unit

        override fun i(tag: String, throwable: Throwable?, message: () -> String) = Unit
    }

    private companion object {
        const val MealId = 5L
        val Date = LocalDate(2026, 6, 24)

        fun entry() =
            FoodSnapEntry(
                id = 3,
                photoPath = "food.jpg",
                createdAt = Instant.fromEpochMilliseconds(0),
            )

        fun meal() = Meal(MealId, "Mittagessen", LocalTime(11, 0), LocalTime(14, 0), 1)

        fun food(isLiquid: Boolean) =
            Product(
                id = FoodId.Product(7),
                name = if (isLiquid) "Saft" else "Apfel",
                brand = null,
                barcode = null,
                note = null,
                isLiquid = isLiquid,
                packageWeight = null,
                servingWeight = null,
                source = FoodSource(FoodSource.Type.User),
                nutritionFacts = NutritionFacts(),
            )
    }
}

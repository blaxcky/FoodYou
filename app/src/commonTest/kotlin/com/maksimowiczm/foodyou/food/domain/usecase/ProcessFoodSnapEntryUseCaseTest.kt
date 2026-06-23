package com.maksimowiczm.foodyou.food.domain.usecase

import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.food.domain.entity.Food
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.FoodSnapEntry
import com.maksimowiczm.foodyou.food.domain.repository.FoodSnapRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class ProcessFoodSnapEntryUseCaseTest {
    @Test
    fun savesFoodIdNameAndWeightForValidEntry() = runBlocking {
        val repository = FakeFoodSnapRepository()
        val entry = entry()

        val result = ProcessFoodSnapEntryUseCase(repository).process(entry, food, 125.5)

        assertEquals(ProcessFoodSnapEntryResult.Processed, result)
        assertEquals(entry.copy(foodId = food.id, foodName = food.headline, weightInGrams = 125.5), repository.updated)
    }

    @Test
    fun invalidWeightDoesNotChangeEntry() = runBlocking {
        val repository = FakeFoodSnapRepository()

        val result = ProcessFoodSnapEntryUseCase(repository).process(entry(), food, 0.0)

        assertEquals(ProcessFoodSnapEntryResult.InvalidWeight, result)
        assertEquals(null, repository.updated)
    }

    @Test
    fun missingFoodDoesNotChangeEntry() = runBlocking {
        val repository = FakeFoodSnapRepository()

        val result = ProcessFoodSnapEntryUseCase(repository).process(entry(), null, 100.0)

        assertEquals(ProcessFoodSnapEntryResult.MissingFood, result)
        assertEquals(null, repository.updated)
    }

    private class FakeFoodSnapRepository : FoodSnapRepository {
        var updated: FoodSnapEntry? = null

        override fun observeEntries(): Flow<List<FoodSnapEntry>> = emptyFlow()

        override fun observeEntry(id: Long): Flow<FoodSnapEntry?> = emptyFlow()

        override suspend fun insert(photoPath: String, createdAt: Instant): Long = 0

        override suspend fun update(entry: FoodSnapEntry) {
            updated = entry
        }

        override suspend fun delete(entry: FoodSnapEntry) = Unit
    }

    private companion object {
        val food =
            object : Food {
                override val id = FoodId.Product(7)
                override val headline = "Apple"
                override val totalWeight: Double? = null
                override val servingWeight: Double? = null
                override val nutritionFacts = NutritionFacts()
                override val isLiquid = false
            }

        fun entry() =
            FoodSnapEntry(
                id = 3,
                photoPath = "food.jpg",
                createdAt = Instant.fromEpochMilliseconds(0),
            )
    }
}

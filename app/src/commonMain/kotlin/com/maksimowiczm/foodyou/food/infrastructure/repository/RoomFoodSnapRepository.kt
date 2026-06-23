package com.maksimowiczm.foodyou.food.infrastructure.repository

import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.FoodSnapEntry
import com.maksimowiczm.foodyou.food.domain.repository.FoodSnapRepository
import com.maksimowiczm.foodyou.food.infrastructure.room.FoodSnapEntryDao
import com.maksimowiczm.foodyou.food.infrastructure.room.FoodSnapEntryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

internal class RoomFoodSnapRepository(private val dao: FoodSnapEntryDao) : FoodSnapRepository {
    override fun observeEntries(): Flow<List<FoodSnapEntry>> =
        dao.observeEntries().map { entries -> entries.map(FoodSnapEntryEntity::toModel) }

    override fun observeEntry(id: Long): Flow<FoodSnapEntry?> =
        dao.observeEntry(id).map { it?.toModel() }

    override suspend fun insert(photoPath: String, createdAt: Instant): Long =
        dao.insert(
            FoodSnapEntryEntity(
                photoPath = photoPath,
                createdAt = createdAt.toEpochMilliseconds(),
                foodType = null,
                foodId = null,
                foodName = null,
                weightInGrams = null,
            )
        )

    override suspend fun update(entry: FoodSnapEntry) = dao.update(entry.toEntity())

    override suspend fun delete(entry: FoodSnapEntry) = dao.delete(entry.toEntity())
}

private fun FoodSnapEntryEntity.toModel(): FoodSnapEntry =
    FoodSnapEntry(
        id = id,
        photoPath = photoPath,
        createdAt = Instant.fromEpochMilliseconds(createdAt),
        foodId =
            when (foodType) {
                FOOD_TYPE_PRODUCT -> foodId?.let(FoodId::Product)
                FOOD_TYPE_RECIPE -> foodId?.let(FoodId::Recipe)
                null -> null
                else -> error("Unknown FoodSnap food type: $foodType")
            },
        foodName = foodName,
        weightInGrams = weightInGrams,
    )

private fun FoodSnapEntry.toEntity(): FoodSnapEntryEntity =
    FoodSnapEntryEntity(
        id = id,
        photoPath = photoPath,
        createdAt = createdAt.toEpochMilliseconds(),
        foodType = foodId?.foodType,
        foodId = foodId?.value,
        foodName = foodName,
        weightInGrams = weightInGrams,
    )

private val FoodId.value: Long
    get() = when (this) {
        is FoodId.Product -> id
        is FoodId.Recipe -> id
    }

private val FoodId.foodType: Int
    get() = when (this) {
        is FoodId.Product -> FOOD_TYPE_PRODUCT
        is FoodId.Recipe -> FOOD_TYPE_RECIPE
    }

private const val FOOD_TYPE_PRODUCT = 0
private const val FOOD_TYPE_RECIPE = 1

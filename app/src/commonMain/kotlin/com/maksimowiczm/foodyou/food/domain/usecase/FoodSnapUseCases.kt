package com.maksimowiczm.foodyou.food.domain.usecase

import com.maksimowiczm.foodyou.common.domain.database.TransactionProvider
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.result.Result.Error
import com.maksimowiczm.foodyou.common.result.Result.Success
import com.maksimowiczm.foodyou.food.domain.entity.Food
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.FoodSnapEntry
import com.maksimowiczm.foodyou.food.domain.entity.Product
import com.maksimowiczm.foodyou.food.domain.entity.Recipe
import com.maksimowiczm.foodyou.food.domain.entity.RecipeIngredient
import com.maksimowiczm.foodyou.food.domain.repository.FoodSnapPhotoStorage
import com.maksimowiczm.foodyou.food.domain.repository.FoodSnapRepository
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFood
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFoodProduct
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFoodRecipe
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFoodRecipeIngredient
import com.maksimowiczm.foodyou.fooddiary.domain.usecase.CreateFoodDiaryEntryUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate

class ObserveFoodSnapEntriesUseCase(private val repository: FoodSnapRepository) {
    fun observe(): Flow<List<FoodSnapEntry>> = repository.observeEntries()

    fun observe(id: Long): Flow<FoodSnapEntry?> = repository.observeEntry(id)
}

class CaptureFoodSnapPhotoUseCase(
    private val repository: FoodSnapRepository,
    private val dateProvider: DateProvider,
) {
    /** The caller writes the camera result first; this makes it durable immediately afterwards. */
    suspend fun capture(photoPath: String): Long = repository.insert(photoPath, dateProvider.nowInstant())
}

sealed interface ProcessFoodSnapEntryResult {
    data object InvalidWeight : ProcessFoodSnapEntryResult
    data object MissingFood : ProcessFoodSnapEntryResult
    data object Processed : ProcessFoodSnapEntryResult
}

class ProcessFoodSnapEntryUseCase(private val repository: FoodSnapRepository) {
    suspend fun process(
        entry: FoodSnapEntry,
        food: Food?,
        weightInGrams: Double,
    ): ProcessFoodSnapEntryResult {
        if (!weightInGrams.isFinite() || weightInGrams <= 0.0) {
            return ProcessFoodSnapEntryResult.InvalidWeight
        }
        if (food == null) return ProcessFoodSnapEntryResult.MissingFood

        repository.update(
            entry.copy(foodId = food.id, foodName = food.headline, weightInGrams = weightInGrams)
        )
        return ProcessFoodSnapEntryResult.Processed
    }
}

class DeleteFoodSnapEntryUseCase(
    private val repository: FoodSnapRepository,
    private val photoStorage: FoodSnapPhotoStorage,
    private val transactionProvider: TransactionProvider,
) {
    suspend fun delete(entry: FoodSnapEntry) = transactionProvider.withTransaction {
        repository.delete(entry)
        photoStorage.delete(entry.photoPath)
    }
}

data class FoodSnapSummary(val foodId: FoodId, val name: String, val weightInGrams: Double)

fun Iterable<FoodSnapEntry>.foodSnapSummary(): List<FoodSnapSummary> =
    filter(FoodSnapEntry::isProcessed)
        .groupBy { requireNotNull(it.foodId) }
        .map { (foodId, entries) ->
            FoodSnapSummary(
                foodId = foodId,
                name = requireNotNull(entries.first().foodName),
                weightInGrams = entries.sumOf { requireNotNull(it.weightInGrams) },
            )
        }

fun Iterable<FoodSnapSummary>.foodSnapShareText(): String =
    joinToString(separator = "\n") { summary -> "${summary.name}: ${summary.weightInGrams.formatFoodSnapWeight()} g" }

sealed interface CompleteFoodSnapResult {
    data object IncompleteSession : CompleteFoodSnapResult
    data class FoodNoLongerExists(val foodId: FoodId) : CompleteFoodSnapResult
    data object DiaryEntryFailed : CompleteFoodSnapResult
    data object Completed : CompleteFoodSnapResult
}

class CompleteFoodSnapUseCase(
    private val repository: FoodSnapRepository,
    private val observeFood: ObserveFoodUseCase,
    private val createFoodDiaryEntry: CreateFoodDiaryEntryUseCase,
    private val photoStorage: FoodSnapPhotoStorage,
    private val transactionProvider: TransactionProvider,
) {
    suspend fun complete(date: LocalDate, mealId: Long): CompleteFoodSnapResult {
        val entries = repository.observeEntries().first()
        if (entries.isEmpty() || entries.any { !it.isProcessed }) {
            return CompleteFoodSnapResult.IncompleteSession
        }

        val summaries = entries.foodSnapSummary()
        val foods =
            summaries.map { summary ->
                val food = observeFood.observe(summary.foodId).first()
                    ?: return CompleteFoodSnapResult.FoodNoLongerExists(summary.foodId)
                summary to food
            }

        for ((summary, food) in foods) {
            val measurement =
                if (food.isLiquid) Measurement.Milliliter(summary.weightInGrams)
                else Measurement.Gram(summary.weightInGrams)
            when (createFoodDiaryEntry.createDiaryEntry(measurement, mealId, date, food.toDiaryFood())) {
                is Success -> Unit
                is Error -> return CompleteFoodSnapResult.DiaryEntryFailed
            }
        }

        transactionProvider.withTransaction {
            for (entry in entries) repository.delete(entry)
            entries.forEach { photoStorage.delete(it.photoPath) }
        }
        return CompleteFoodSnapResult.Completed
    }
}

private fun Food.toDiaryFood(): DiaryFood =
    when (this) {
        is Product -> DiaryFoodProduct(id, headline, nutritionFacts, servingWeight, totalWeight, isLiquid, source, note)
        is Recipe -> DiaryFoodRecipe(id, headline, servings, ingredients.map(RecipeIngredient::toDiaryIngredient), isLiquid, note)
    }

private fun RecipeIngredient.toDiaryIngredient(): DiaryFoodRecipeIngredient =
    DiaryFoodRecipeIngredient(food.toDiaryFood(), measurement)

private fun Double.formatFoodSnapWeight(): String =
    if (this % 1.0 == 0.0) toLong().toString() else toString()

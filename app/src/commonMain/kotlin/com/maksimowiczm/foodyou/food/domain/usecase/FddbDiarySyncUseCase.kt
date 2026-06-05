package com.maksimowiczm.foodyou.food.domain.usecase

import com.maksimowiczm.foodyou.common.domain.database.TransactionProvider
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.result.isSuccess
import com.maksimowiczm.foodyou.food.domain.entity.FddbDiaryEntry
import com.maksimowiczm.foodyou.food.domain.entity.Product
import com.maksimowiczm.foodyou.food.domain.repository.FddbCredentialsRepository
import com.maksimowiczm.foodyou.food.domain.repository.FddbDiaryGateway
import com.maksimowiczm.foodyou.food.domain.repository.FddbDiarySyncEntryRepository
import com.maksimowiczm.foodyou.food.domain.repository.FddbProductGateway
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFoodProduct
import com.maksimowiczm.foodyou.fooddiary.domain.repository.MealRepository
import com.maksimowiczm.foodyou.fooddiary.domain.usecase.CreateFoodDiaryEntryUseCase
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

class FddbDiarySyncUseCase(
    private val credentialsRepository: FddbCredentialsRepository,
    private val diaryGateway: FddbDiaryGateway,
    private val productGateway: FddbProductGateway,
    private val productRepository: ProductRepository,
    private val syncEntryRepository: FddbDiarySyncEntryRepository,
    private val mealRepository: MealRepository,
    private val createFoodDiaryEntryUseCase: CreateFoodDiaryEntryUseCase,
    private val transactionProvider: TransactionProvider,
    private val dateProvider: DateProvider,
) {
    suspend fun hasCredentials(): Boolean = credentialsRepository.hasCredentials().first()

    suspend fun sync(referenceDate: LocalDate = today()): FddbDiarySyncResult {
        val dates = (0..6).map { referenceDate.minus(it, DateTimeUnit.DAY) }.toSet()
        val meals = mealRepository.observeMeals().first().sortedBy { it.rank }
        val diaryEntries = diaryGateway.getLastSevenDays(referenceDate).filter { it.date in dates }
        var imported = 0
        var skipped = 0
        var failed = 0

        for (entry in diaryEntries) {
            try {
                if (syncEntryRepository.contains(entry.entryId)) {
                    skipped += 1
                    continue
                }
                if (entry.isDummy()) {
                    syncEntryRepository.add(entry.entryId, dateProvider.nowInstant())
                    skipped += 1
                    continue
                }

                val product = resolveProduct(entry) ?: run {
                    failed += 1
                    continue
                }
                val meal = meals.matchFddbMeal(entry.mealName) ?: run {
                    failed += 1
                    continue
                }
                val measurement = entry.measurement.forProduct(product)

                val result =
                    createFoodDiaryEntryUseCase.createDiaryEntry(
                        measurement = measurement,
                        mealId = meal.id,
                        date = entry.date,
                        food = product.toDiaryProduct(),
                    )
                if (result.isSuccess()) {
                    syncEntryRepository.add(entry.entryId, dateProvider.nowInstant())
                    imported += 1
                } else {
                    failed += 1
                }
            } catch (_: Throwable) {
                failed += 1
            }
        }

        return FddbDiarySyncResult(imported = imported, skipped = skipped, failed = failed)
    }

    private suspend fun resolveProduct(entry: FddbDiaryEntry): Product? {
        val fddbProduct = productGateway.getProduct(entry.productUrl)
        return transactionProvider.withTransaction {
            when (val result = productRepository.upsertFddbProduct(entry.productUrl, fddbProduct)) {
                is FddbProductUpsertResult.Inserted -> result.product
                is FddbProductUpsertResult.Updated -> result.product
            }
        }
    }

    private fun today(): LocalDate =
        dateProvider.nowInstant().toLocalDateTime(TimeZone.currentSystemDefault()).date
}

data class FddbDiarySyncResult(val imported: Int, val skipped: Int, val failed: Int)

private fun FddbDiaryEntry.isDummy(): Boolean =
    productName.contains("dummy", ignoreCase = true) || productUrl.contains("dummy", ignoreCase = true)

private fun List<com.maksimowiczm.foodyou.fooddiary.domain.entity.Meal>.matchFddbMeal(name: String) =
    firstOrNull { it.name.equals(name, ignoreCase = true) }
        ?: firstOrNull { meal ->
            val normalized = name.lowercase()
            when {
                normalized.contains("morgen") -> meal.name.contains("breakfast", ignoreCase = true) || meal.name.contains("früh", ignoreCase = true)
                normalized.contains("mittag") -> meal.name.contains("lunch", ignoreCase = true) || meal.name.contains("mittag", ignoreCase = true)
                normalized.contains("abend") -> meal.name.contains("dinner", ignoreCase = true) || meal.name.contains("abend", ignoreCase = true)
                normalized.contains("snack") -> meal.name.contains("snack", ignoreCase = true)
                else -> false
            }
        }
        ?: firstOrNull()

private fun Measurement.forProduct(product: Product): Measurement =
    when (this) {
        is Measurement.Gram -> if (product.isLiquid) Measurement.Milliliter(value) else this
        is Measurement.Milliliter -> if (product.isLiquid) this else Measurement.Gram(value)
        else -> this
    }

private fun Product.toDiaryProduct(): DiaryFoodProduct =
    DiaryFoodProduct(
        name = headline,
        nutritionFacts = nutritionFacts,
        servingWeight = servingWeight,
        totalWeight = totalWeight,
        isLiquid = isLiquid,
        source = source,
        note = note,
    )

package com.maksimowiczm.foodyou.app.ui.food.quickcapture

import com.maksimowiczm.foodyou.app.ui.food.diary.quickadd.QuickAddCsvData
import com.maksimowiczm.foodyou.app.widget.updateCalorieWidgetValues
import com.maksimowiczm.foodyou.common.domain.database.TransactionProvider
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.food.NutrientValue.Companion.toNutrientValue
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.food.domain.repository.QuickCaptureRepository
import com.maksimowiczm.foodyou.fooddiary.domain.repository.ManualDiaryEntryRepository
import com.maksimowiczm.foodyou.fooddiary.domain.repository.MealRepository
import com.maksimowiczm.foodyou.fooddiary.domain.usecase.selectMealForBarcodeShortcut
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

internal sealed interface QuickCaptureCsvImportResult {
    data object Success : QuickCaptureCsvImportResult

    data object NoMeal : QuickCaptureCsvImportResult
}

internal fun interface QuickCaptureCsvImporter {
    suspend fun import(
        data: QuickAddCsvData,
        entryIds: List<Long>,
    ): QuickCaptureCsvImportResult
}

internal class QuickCaptureCsvImporterImpl(
    private val dateProvider: DateProvider,
    private val mealRepository: MealRepository,
    private val manualDiaryEntryRepository: ManualDiaryEntryRepository,
    private val quickCaptureRepository: QuickCaptureRepository,
    private val transactionProvider: TransactionProvider,
) : QuickCaptureCsvImporter {
    override suspend fun import(
        data: QuickAddCsvData,
        entryIds: List<Long>,
    ): QuickCaptureCsvImportResult {
        if (entryIds.isEmpty()) return QuickCaptureCsvImportResult.NoMeal

        val nowInstant = dateProvider.nowInstant()
        val now = nowInstant.toLocalDateTime(TimeZone.currentSystemDefault())
        val meals = mealRepository.observeMeals().first().sortedBy { it.rank }
        val meal =
            selectMealForBarcodeShortcut(meals, now.time)
                ?: meals.firstOrNull()
                ?: return QuickCaptureCsvImportResult.NoMeal

        transactionProvider.withTransaction {
            manualDiaryEntryRepository.insert(
                name = data.name,
                mealId = meal.id,
                date = now.date,
                nutritionFacts =
                    NutritionFacts(
                        energy = data.energyKcal.toNutrientValue(),
                        proteins = data.proteins.toNutrientValue(),
                        carbohydrates = data.carbohydrates.toNutrientValue(),
                        fats = data.fats.toNutrientValue(),
                    ),
                createdAt = now,
            )
            quickCaptureRepository.markCompleted(entryIds.distinct(), nowInstant)
        }

        val _ = runCatching { updateCalorieWidgetValues() }
        return QuickCaptureCsvImportResult.Success
    }
}

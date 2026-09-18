package com.maksimowiczm.foodyou.food.domain.usecase

import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.food.domain.entity.QuickCaptureFoodName
import com.maksimowiczm.foodyou.food.domain.entity.QuickCaptureLogEntry
import com.maksimowiczm.foodyou.food.domain.entity.QuickCaptureWeightMode
import com.maksimowiczm.foodyou.food.domain.entity.canonicalQuickCaptureFoodName
import com.maksimowiczm.foodyou.food.domain.repository.FoodSnapPhotoStorage
import com.maksimowiczm.foodyou.food.domain.repository.QuickCaptureRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class ObserveQuickCaptureUseCase(private val repository: QuickCaptureRepository) {
    fun entries(): Flow<List<QuickCaptureLogEntry>> = repository.observeEntries()

    fun entry(id: Long): Flow<QuickCaptureLogEntry?> = repository.observeEntry(id)

    fun foodNames(): Flow<List<QuickCaptureFoodName>> = repository.observeFoodNames()
}

sealed interface SaveQuickCaptureEntryResult {
    data class Saved(val id: Long) : SaveQuickCaptureEntryResult
    data object InvalidName : SaveQuickCaptureEntryResult
    data object InvalidWeight : SaveQuickCaptureEntryResult
}

class SaveQuickCaptureEntryUseCase(
    private val repository: QuickCaptureRepository,
    private val dateProvider: DateProvider,
) {
    suspend fun save(
        foodName: String,
        weightMode: QuickCaptureWeightMode,
        directWeightInGrams: Double? = null,
        beforeWeightInGrams: Double? = null,
        afterWeightInGrams: Double? = null,
    ): SaveQuickCaptureEntryResult {
        val canonicalName = canonicalQuickCaptureFoodName(foodName)
        if (canonicalName.isBlank()) return SaveQuickCaptureEntryResult.InvalidName

        val valid =
            when (weightMode) {
                QuickCaptureWeightMode.Direct ->
                    directWeightInGrams?.let { it.isFinite() && it > 0.0 } == true
                QuickCaptureWeightMode.BeforeAfter -> {
                    val beforeValid =
                        beforeWeightInGrams?.let { it.isFinite() && it > 0.0 } == true
                    val afterValid =
                        afterWeightInGrams?.let {
                            it.isFinite() && it >= 0.0 &&
                                beforeWeightInGrams != null && beforeWeightInGrams > it
                        } == true
                    beforeValid && (afterValid || afterWeightInGrams == null)
                }
            }
        if (!valid) return SaveQuickCaptureEntryResult.InvalidWeight

        return SaveQuickCaptureEntryResult.Saved(
            repository.createEntry(
                foodName = canonicalName,
                weightMode = weightMode,
                directWeightInGrams = directWeightInGrams,
                beforeWeightInGrams = beforeWeightInGrams,
                afterWeightInGrams = afterWeightInGrams,
                createdAt = dateProvider.nowInstant(),
            )
        )
    }
}

class CaptureQuickCapturePhotoUseCase(
    private val repository: QuickCaptureRepository,
    private val dateProvider: DateProvider,
) {
    suspend fun capture(photoPath: String): Long =
        repository.capturePhoto(photoPath, dateProvider.nowInstant())
}

class ProcessQuickCapturePhotoUseCase(
    private val repository: QuickCaptureRepository,
    private val dateProvider: DateProvider,
) {
    suspend fun process(id: Long, foodName: String, weightInGrams: Double): Boolean {
        val name = canonicalQuickCaptureFoodName(foodName)
        if (name.isBlank() || !weightInGrams.isFinite() || weightInGrams <= 0.0) return false
        repository.processPhoto(id, name, weightInGrams, dateProvider.nowInstant())
        return true
    }
}

class CompleteQuickCaptureAfterUseCase(private val repository: QuickCaptureRepository) {
    suspend fun complete(id: Long, afterWeightInGrams: Double): Boolean {
        val entry = repository.observeEntry(id).first() ?: return false
        val before = entry.beforeWeightInGrams ?: return false
        if (!afterWeightInGrams.isFinite() || afterWeightInGrams < 0.0 || afterWeightInGrams >= before) {
            return false
        }
        repository.setAfterWeight(id, afterWeightInGrams)
        return true
    }
}

class MarkQuickCaptureCompletedUseCase(
    private val repository: QuickCaptureRepository,
    private val dateProvider: DateProvider,
) {
    suspend fun mark(ids: List<Long>) {
        if (ids.isNotEmpty()) repository.markCompleted(ids, dateProvider.nowInstant())
    }
}

class DeleteQuickCaptureEntriesUseCase(
    private val repository: QuickCaptureRepository,
    private val photoStorage: FoodSnapPhotoStorage,
) {
    suspend fun delete(entries: List<QuickCaptureLogEntry>) {
        repository.deleteEntries(entries.map { it.id })
        entries.mapNotNull { it.photoPath }.forEach(photoStorage::delete)
    }
}

class UpdateQuickCaptureLibraryUseCase(
    private val repository: QuickCaptureRepository,
    private val dateProvider: DateProvider,
) {
    suspend fun rename(id: Long, name: String): Boolean {
        val canonicalName = canonicalQuickCaptureFoodName(name)
        if (canonicalName.isBlank()) return false
        repository.renameFoodName(id, canonicalName, dateProvider.nowInstant())
        return true
    }

    suspend fun delete(id: Long) = repository.deleteFoodName(id)
}

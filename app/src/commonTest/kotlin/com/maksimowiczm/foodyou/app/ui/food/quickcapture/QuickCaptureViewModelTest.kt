package com.maksimowiczm.foodyou.app.ui.food.quickcapture

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.app.ui.food.diary.quickadd.QuickAddCsvData
import com.maksimowiczm.foodyou.app.ui.food.diary.quickadd.QuickAddCsvError
import com.maksimowiczm.foodyou.app.ui.food.diary.quickadd.QuickAddCsvParseResult
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.food.domain.entity.QuickCaptureFoodName
import com.maksimowiczm.foodyou.food.domain.entity.QuickCaptureLogEntry
import com.maksimowiczm.foodyou.food.domain.entity.QuickCaptureWeightMode
import com.maksimowiczm.foodyou.food.domain.repository.FoodSnapPhotoStorage
import com.maksimowiczm.foodyou.food.domain.repository.QuickCaptureRepository
import com.maksimowiczm.foodyou.food.domain.usecase.CaptureQuickCapturePhotoUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.CompleteQuickCaptureAfterUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.DeleteQuickCaptureEntriesUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.ObserveQuickCaptureUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.SaveQuickCaptureEntryUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.UpdateQuickCaptureLibraryUseCase
import com.maksimowiczm.foodyou.settings.domain.entity.AppLaunchInfo
import com.maksimowiczm.foodyou.settings.domain.entity.EnergyFormat
import com.maksimowiczm.foodyou.settings.domain.entity.GoalDisplayMode
import com.maksimowiczm.foodyou.settings.domain.entity.HomeCard
import com.maksimowiczm.foodyou.settings.domain.entity.NutrientsOrder
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

class QuickCaptureViewModelTest {
    @Test
    fun copiedBatchKeepsExactDistinctIdsAndIsReplacedByNextCopy() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val viewModel = viewModel()
        try {
            viewModel.rememberCopiedBatch(listOf(7, 2, 7))
            assertEquals(listOf(7L, 2L), viewModel.copiedEntryIds.value)

            viewModel.rememberCopiedBatch(listOf(11))
            assertEquals(listOf(11L), viewModel.copiedEntryIds.value)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun successfulImportUsesCopiedSnapshotOnceAndClearsIt() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val releaseImport = CompletableDeferred<Unit>()
        val importedBatches = mutableListOf<List<Long>>()
        val viewModel =
            viewModel(
                importer = { _, ids ->
                    importedBatches += ids
                    releaseImport.await()
                    QuickCaptureCsvImportResult.Success
                }
            )
        try {
            viewModel.rememberCopiedBatch(listOf(4, 9))
            viewModel.importCsv(ValidCsv)
            viewModel.importCsv(ValidCsv)

            assertEquals(QuickCaptureCsvImportState.Submitting, viewModel.csvImportState.value)
            assertEquals(listOf(listOf(4L, 9L)), importedBatches)

            releaseImport.complete(Unit)
            advanceUntilIdle()

            assertTrue(viewModel.copiedEntryIds.value.isEmpty())
            assertEquals(QuickCaptureCsvImportState.Idle, viewModel.csvImportState.value)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun invalidCsvKeepsCopiedBatchAndSkipsImport() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        var importCount = 0
        val viewModel =
            viewModel(
                parser = { QuickAddCsvParseResult.Failure(QuickAddCsvError.InvalidHeader) },
                importer = { _, _ ->
                    importCount += 1
                    QuickCaptureCsvImportResult.Success
                },
            )
        try {
            viewModel.rememberCopiedBatch(listOf(5, 6))
            viewModel.importCsv("invalid")
            advanceUntilIdle()

            assertEquals(0, importCount)
            assertEquals(listOf(5L, 6L), viewModel.copiedEntryIds.value)
            assertEquals(
                QuickCaptureCsvImportState.InvalidCsv(QuickAddCsvError.InvalidHeader),
                viewModel.csvImportState.value,
            )
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun noMealOrStorageFailureKeepsCopiedBatch() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val noMealViewModel =
            viewModel(importer = { _, _ -> QuickCaptureCsvImportResult.NoMeal })
        val failedViewModel = viewModel(importer = { _, _ -> error("storage failed") })
        try {
            noMealViewModel.rememberCopiedBatch(listOf(1))
            noMealViewModel.importCsv(ValidCsv)
            failedViewModel.rememberCopiedBatch(listOf(2))
            failedViewModel.importCsv(ValidCsv)
            advanceUntilIdle()

            assertEquals(QuickCaptureCsvImportState.NoMeal, noMealViewModel.csvImportState.value)
            assertEquals(listOf(1L), noMealViewModel.copiedEntryIds.value)
            assertEquals(
                QuickCaptureCsvImportState.SavingFailed,
                failedViewModel.csvImportState.value,
            )
            assertEquals(listOf(2L), failedViewModel.copiedEntryIds.value)
        } finally {
            noMealViewModel.viewModelScope.cancel()
            failedViewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    private fun viewModel(
        parser: suspend (String) -> QuickAddCsvParseResult = {
            QuickAddCsvParseResult.Success(CsvData)
        },
        importer: suspend (QuickAddCsvData, List<Long>) -> QuickCaptureCsvImportResult = { _, _ ->
            QuickCaptureCsvImportResult.Success
        },
    ): QuickCaptureViewModel {
        val repository = CsvImportViewModelQuickCaptureRepository()
        return QuickCaptureViewModel(
            observe = ObserveQuickCaptureUseCase(repository),
            saveEntry = SaveQuickCaptureEntryUseCase(repository, CsvImportViewModelDateProvider),
            capture = CaptureQuickCapturePhotoUseCase(repository, CsvImportViewModelDateProvider),
            completeAfter = CompleteQuickCaptureAfterUseCase(repository),
            deleteEntries =
                DeleteQuickCaptureEntriesUseCase(
                    repository,
                    object : FoodSnapPhotoStorage {
                        override fun delete(photoPath: String) = Unit
                    },
                ),
            updateLibrary =
                UpdateQuickCaptureLibraryUseCase(repository, CsvImportViewModelDateProvider),
            settingsRepository = CsvImportViewModelSettingsRepository(),
            csvParser = { parser(it) },
            csvImporter = { data, ids -> importer(data, ids) },
            savedStateHandle = SavedStateHandle(),
        )
    }

    private companion object {
        const val ValidCsv = "name,energy,proteins,carbohydrates,fats\nMeal,640,42,71,19"
        val CsvData = QuickAddCsvData("Meal", 640.0, 42.0, 71.0, 19.0)
    }
}

private class CsvImportViewModelQuickCaptureRepository : QuickCaptureRepository {
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

    override suspend fun markCompleted(ids: List<Long>, completedAt: Instant) = error("Not used")

    override suspend fun deleteEntries(ids: List<Long>) = error("Not used")

    override suspend fun renameFoodName(id: Long, name: String, usedAt: Instant) =
        error("Not used")

    override suspend fun deleteFoodName(id: Long) = error("Not used")
}

private class CsvImportViewModelSettingsRepository : UserPreferencesRepository<Settings> {
    private val settings = MutableStateFlow(defaultCsvImportSettings())

    override fun observe(): Flow<Settings> = settings

    override suspend fun update(transform: Settings.() -> Settings) {
        settings.value = settings.value.transform()
    }
}

private object CsvImportViewModelDateProvider : DateProvider {
    private val instant = Instant.parse("2026-09-19T12:00:00Z")

    override fun nowInstant(): Instant = instant

    override fun observeInstant(interval: Duration): Flow<Instant> = flowOf(instant)

    override fun observeDate(timeZone: TimeZone): Flow<LocalDate> =
        flowOf(LocalDate(2026, 9, 19))
}

private fun defaultCsvImportSettings() =
    Settings(
        lastRememberedVersion = null,
        hidePreviewDialog = false,
        showTranslationWarning = false,
        nutrientsOrder = NutrientsOrder.defaultOrder,
        secureScreen = false,
        homeCardOrder = HomeCard.defaultOrder,
        expandGoalCard = false,
        goalDisplayMode = GoalDisplayMode.Normal,
        dietEnergyDeficitKcal = null,
        onboardingFinished = true,
        energyFormat = EnergyFormat.DEFAULT,
        appLaunchInfo =
            AppLaunchInfo(
                firstLaunch = null,
                firstLaunchCurrentVersion = null,
                launchesCount = 0,
            ),
        stepsCaloriesPerStepKcal = null,
        healthConnectStepsEnabled = true,
        healthConnectStepsLastSyncedEpochSeconds = null,
    )

package com.maksimowiczm.foodyou.app.ui.food.quickcapture

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.app.ui.food.diary.quickadd.CollapsedQuickCaptureCsvExample
import com.maksimowiczm.foodyou.app.ui.food.diary.quickadd.QuickAddCsvTableParserImpl
import com.maksimowiczm.foodyou.common.infrastructure.csv.CsvParserImpl
import com.maksimowiczm.foodyou.app.ui.food.diary.quickadd.QuickAddCsvData
import com.maksimowiczm.foodyou.app.ui.food.diary.quickadd.QuickAddCsvError
import com.maksimowiczm.foodyou.app.ui.food.diary.quickadd.QuickAddCsvTableParseResult
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.food.domain.entity.QuickCaptureFoodName
import com.maksimowiczm.foodyou.food.domain.entity.QuickCaptureLogEntry
import com.maksimowiczm.foodyou.food.domain.entity.quickCaptureGroups
import com.maksimowiczm.foodyou.food.domain.entity.QuickCaptureWeightMode
import com.maksimowiczm.foodyou.food.domain.repository.FoodSnapPhotoStorage
import com.maksimowiczm.foodyou.food.domain.repository.QuickCaptureRepository
import com.maksimowiczm.foodyou.food.domain.usecase.CaptureQuickCapturePhotoUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.CompleteQuickCaptureAfterUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.DeleteQuickCaptureEntriesUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.ObserveQuickCaptureUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.SaveQuickCaptureEntryUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.SetQuickCaptureAiSuggestionRejectedUseCase
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
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

class QuickCaptureViewModelTest {
    @Test
    fun collapsedCsvImportsAllFiveRowsOnlyWhenTheBatchCountMatches() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val importedRows = mutableListOf<List<QuickAddCsvData>>()
        val importedIds = mutableListOf<List<Long>>()
        val viewModel = viewModel(importer = { rows, ids ->
            importedRows += rows
            importedIds += ids
            QuickCaptureCsvImportResult.Success
        })
        val ids = listOf(1L, 2L, 3L, 4L, 5L)
        try {
            viewModel.rememberCopiedBatch(ids, expectedRowCount = 4)
            viewModel.importCsv(CollapsedQuickCaptureCsvExample.csv)
            advanceUntilIdle()
            assertEquals(QuickCaptureCsvImportState.RowCountMismatch(4, 5), viewModel.csvImportState.value)
            assertEquals(ids, viewModel.copiedEntryIds.value)
            assertTrue(importedRows.isEmpty())

            viewModel.rememberCopiedBatch(ids, expectedRowCount = 5)
            viewModel.importCsv(CollapsedQuickCaptureCsvExample.csv)
            advanceUntilIdle()
            assertEquals(listOf(CollapsedQuickCaptureCsvExample.rows), importedRows)
            assertEquals(listOf(ids), importedIds)
            assertEquals(QuickCaptureCsvImportState.Idle, viewModel.csvImportState.value)
            assertTrue(viewModel.copiedEntryIds.value.isEmpty())
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun savedPhotoIsRegisteredAfterQuickCaptureViewModelIsClosed() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repository = CsvImportViewModelQuickCaptureRepository()
            val viewModel = viewModel(repository = repository)
            viewModel.viewModelScope.cancel()
            viewModel.capturePhoto("late.jpg")
            advanceUntilIdle()
            assertEquals(listOf("late.jpg"), repository.capturedPaths)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun copiedBatchKeepsExactDistinctIdsAndIsReplacedByNextCopy() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val saved = SavedStateHandle()
        val viewModel = viewModel(savedStateHandle = saved)
        try {
            viewModel.rememberCopiedBatch(listOf(7, 2, 7), expectedRowCount = 2)
            assertEquals(2, saved.get<Int>("quickCaptureCopiedGroupCount"))
            assertEquals(listOf(7L, 2L), viewModel.copiedEntryIds.value)

            viewModel.rememberCopiedBatch(listOf(11), expectedRowCount = 1)
            assertEquals(listOf(11L), viewModel.copiedEntryIds.value)
            assertEquals(1, saved.get<Int>("quickCaptureCopiedGroupCount"))
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
            viewModel.rememberCopiedBatch(listOf(4, 9), expectedRowCount = 1)
            viewModel.importCsv(ValidCsv)
            viewModel.rememberCopiedBatch(listOf(100), expectedRowCount = 5)
            assertEquals(listOf(4L, 9L), viewModel.copiedEntryIds.value)
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
                parser = { QuickAddCsvTableParseResult.Failure(QuickAddCsvError.InvalidHeader) },
                importer = { _, _ ->
                    importCount += 1
                    QuickCaptureCsvImportResult.Success
                },
            )
        try {
            viewModel.rememberCopiedBatch(listOf(5, 6), expectedRowCount = 1)
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
            noMealViewModel.rememberCopiedBatch(listOf(1), expectedRowCount = 1)
            noMealViewModel.importCsv(ValidCsv)
            failedViewModel.rememberCopiedBatch(listOf(2), expectedRowCount = 1)
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

    @Test
    fun rowCountMismatchKeepsBatchAndNeverImports() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        var importCount = 0
        val viewModel = viewModel(importer = { _, _ ->
            importCount += 1
            QuickCaptureCsvImportResult.Success
        })
        try {
            viewModel.rememberCopiedBatch(listOf(1, 2, 3), expectedRowCount = 2)
            for ((csv, actual) in listOf(
                "name,energy,proteins,carbohydrates,fats" to 0,
                ValidCsv to 1,
                "$TwoRowCsv\nExtra,10,1,1,1" to 3,
            )) {
                viewModel.importCsv(csv)
                advanceUntilIdle()
                assertEquals(QuickCaptureCsvImportState.RowCountMismatch(2, actual), viewModel.csvImportState.value)
                assertEquals(listOf(1L, 2L, 3L), viewModel.copiedEntryIds.value)
            }
            assertEquals(0, importCount)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun groupedCountSurvivesRecreationAndGroupingChanges() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val saved = SavedStateHandle()
        val settings = CsvImportViewModelSettingsRepository()
        val repository = CsvImportViewModelQuickCaptureRepository()
        fun readyEntry(id: Long, foodId: Long, name: String) = QuickCaptureLogEntry(
            id = id, foodNameId = foodId, foodName = name,
            weightMode = QuickCaptureWeightMode.Direct, directWeightInGrams = 100.0,
            beforeWeightInGrams = null, afterWeightInGrams = null, photoPath = null,
            createdAt = CsvImportViewModelDateProvider.nowInstant(), completedAt = null,
        )
        repository.logEntries.value = listOf(
            readyEntry(1, 1, "Meal"), readyEntry(2, 1, "Meal"), readyEntry(3, 2, "Apple"),
        )
        val original = viewModel(savedStateHandle = saved, settingsRepository = settings, repository = repository)
        original.setAggregateSameFoods(true)
        var importedRows = emptyList<QuickAddCsvData>()
        var importedIds = emptyList<Long>()
        val groups = repository.logEntries.value.quickCaptureGroups(aggregateSameFoods = true)
        original.rememberCopiedBatch(groups.flatMap { it.entries.map { entry -> entry.id } }, groups.size)
        repository.logEntries.value += readyEntry(4, 3, "Added later")
        val restoredState = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
        val restored = viewModel(
            savedStateHandle = restoredState,
            repository = repository,
            settingsRepository = settings,
            importer = { rows, ids ->
                importedRows = rows
                importedIds = ids
                QuickCaptureCsvImportResult.Success
            },
        )
        try {
            original.viewModelScope.cancel()
            restored.setAggregateSameFoods(false)
            restored.importCsv(TwoRowCsv)
            advanceUntilIdle()
            assertEquals(listOf("Meal", "Apple"), importedRows.map { it.name })
            assertEquals(listOf(1L, 2L, 3L), importedIds)
            assertTrue(restored.copiedEntryIds.value.isEmpty())
            assertNull(restoredState.get<Int>("quickCaptureCopiedGroupCount"))
        } finally {
            original.viewModelScope.cancel()
            restored.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun legacyBatchRequiresSharingAgain() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        var imports = 0
        val viewModel = viewModel(
            savedStateHandle = SavedStateHandle(mapOf("quickCaptureCopiedEntryIds" to listOf(1L, 2L))),
            importer = { _, _ ->
                imports += 1
                QuickCaptureCsvImportResult.Success
            },
        )
        try {
            viewModel.importCsv(ValidCsv)
            advanceUntilIdle()
            assertEquals(QuickCaptureCsvImportState.ShareAgain, viewModel.csvImportState.value)
            assertEquals(0, imports)
            assertEquals(listOf(1L, 2L), viewModel.copiedEntryIds.value)
            viewModel.rememberCopiedBatch(listOf(1, 2), expectedRowCount = 2)
            viewModel.importCsv(TwoRowCsv)
            advanceUntilIdle()
            assertEquals(1, imports)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    private fun TestScope.viewModel(
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
        repository: CsvImportViewModelQuickCaptureRepository = CsvImportViewModelQuickCaptureRepository(),
        settingsRepository: CsvImportViewModelSettingsRepository = CsvImportViewModelSettingsRepository(),
        parser: suspend (String) -> QuickAddCsvTableParseResult = {
            QuickAddCsvTableParserImpl(CsvParserImpl()).parse(it)
        },
        importer: suspend (List<QuickAddCsvData>, List<Long>) -> QuickCaptureCsvImportResult = { _, _ ->
            QuickCaptureCsvImportResult.Success
        },
    ): QuickCaptureViewModel {
        return QuickCaptureViewModel(
            observe = ObserveQuickCaptureUseCase(repository),
            saveEntry = SaveQuickCaptureEntryUseCase(repository, CsvImportViewModelDateProvider),
            capture = CaptureQuickCapturePhotoUseCase(repository, CsvImportViewModelDateProvider),
            setAiSuggestionRejected = SetQuickCaptureAiSuggestionRejectedUseCase(repository),
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
            settingsRepository = settingsRepository,
            csvParser = { parser(it) },
            csvImporter = { data, ids -> importer(data, ids) },
            savedStateHandle = savedStateHandle,
            photoRegistrationScope = this,
        )
    }

    private companion object {
        const val ValidCsv = "name,energy,proteins,carbohydrates,fats\nMeal,640,42,71,19"
        const val TwoRowCsv = "$ValidCsv\nApple,80,0.4,18,0.2"
    }
}

private class CsvImportViewModelQuickCaptureRepository : QuickCaptureRepository {
    val capturedPaths = mutableListOf<String>()
    val logEntries = MutableStateFlow<List<QuickCaptureLogEntry>>(emptyList())
    override fun observeFoodNames(): Flow<List<QuickCaptureFoodName>> = flowOf(emptyList())

    override fun observeEntries(): Flow<List<QuickCaptureLogEntry>> = logEntries

    override fun observeEntry(id: Long): Flow<QuickCaptureLogEntry?> = flowOf(null)

    override suspend fun createEntry(
        foodName: String,
        weightMode: QuickCaptureWeightMode,
        directWeightInGrams: Double?,
        beforeWeightInGrams: Double?,
        afterWeightInGrams: Double?,
        createdAt: Instant,
    ): Long = error("Not used")

    override suspend fun capturePhoto(photoPath: String, createdAt: Instant): Long {
        capturedPaths += photoPath
        return 42
    }

    override suspend fun processPhoto(
        id: Long,
        foodName: String,
        weightInGrams: Double,
        usedAt: Instant,
    ) = error("Not used")

    override suspend fun setAiSuggestionRejected(id: Long, rejected: Boolean) = error("Not used")

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

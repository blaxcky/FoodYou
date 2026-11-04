package com.maksimowiczm.foodyou.ai

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.*
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.maksimowiczm.foodyou.app.infrastructure.room.FoodYouDatabase
import com.maksimowiczm.foodyou.common.crypto.MasterCrypto
import com.maksimowiczm.foodyou.food.infrastructure.room.QuickCaptureLogEntryEntity
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlin.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class CloudPhotoAnalysisTest {
    private lateinit var context: Context
    private lateinit var db: FoodYouDatabase
    private lateinit var controller: AndroidAiController
    private val calls = mutableListOf<String>()
    private var response: suspend (String) -> ScaleRecognitionResult = { ScaleRecognitionResult.Recognized(42.0) }
    private val crypto = object : MasterCrypto {
        override val isSupported = flowOf(true)
        override suspend fun encrypt(data: ByteArray) = data
        override suspend fun decrypt(encryptedData: ByteArray) = encryptedData
    }

    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = ApplicationProvider.getApplicationContext()
        File(context.noBackupFilesDir, "ai").deleteRecursively()
        WorkManagerTestInitHelper.initializeTestWorkManager(context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        db = Room.inMemoryDatabaseBuilder(context, FoodYouDatabase::class.java).allowMainThreadQueries().build()
        controller = newController()
        startKoin { modules(module { single<AiController> { controller } }) }
    }

    private fun newController() = AndroidAiController(context, db, crypto) { _, _ ->
        object : ScaleWeightRecognizer {
            override val maxConcurrentPhotos = 4
            override suspend fun recognize(photoPath: String): ScaleRecognitionResult {
                calls += photoPath
                return response(photoPath)
            }
            override suspend fun close() = Unit
        }
    }

    @After fun cleanup() {
        controller.close()
        stopKoin()
        WorkManagerTestInitHelper.closeWorkDatabase()
        db.close()
        File(context.noBackupFilesDir, "ai").deleteRecursively()
        Dispatchers.resetMain()
    }

    private suspend fun photo(path: String = "photo.jpg"): QueuedScalePhoto {
        val id = db.quickCaptureDao.insertEntry(QuickCaptureLogEntryEntity(
            foodNameId = null, foodName = null, weightMode = 0, directWeightInGrams = null,
            beforeWeightInGrams = null, afterWeightInGrams = null, afterRequired = false,
            photoPath = path, createdAt = 1, completedAt = null,
        ))
        return QueuedScalePhoto(id, path, null)
    }

    private suspend fun cloud() = controller.saveSettings(AiProvider.OpenAI, "gemini", false, null)
    private suspend fun work() = withContext(Dispatchers.IO) {
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(CloudPhotoQueue.NAME).get()
    }

    @Test fun enablingIncludesBacklogAndManualStartDoesNotDuplicateWaitingWork() = runTest {
        cloud()
        photo()
        controller.reconcileBackgroundAnalysis()
        assertTrue(work().isEmpty())
        controller.saveBackgroundPhotoAnalysis(true)
        assertEquals(1, work().size)
        assertEquals(WorkInfo.State.ENQUEUED, work().single().state)
        controller.startAnalysis()
        controller.reconcileBackgroundAnalysis()
        assertEquals(1, work().size)
        assertTrue(calls.isEmpty()) // Network-constrained work has not been released by the test driver.
        val restored = newController()
        assertTrue(restored.settings.value.backgroundPhotoAnalysisEnabled)
        restored.close()
    }

    @Test fun workerExecutesAfterNetworkReturnsAndAcceptsLaterPhotos() = runTest {
        cloud()
        controller.saveBackgroundPhotoAnalysis(true)
        val first = photo("first")
        controller.schedule(first.id)
        val second = photo("second")
        controller.schedule(second.id)
        val pending = work()
        assertEquals(2, pending.size)
        val driver = requireNotNull(WorkManagerTestInitHelper.getTestDriver(context))
        pending.forEach { driver.setAllConstraintsMet(it.id) }
        withContext(Dispatchers.Default) {
            withTimeout(15_000) {
                WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(CloudPhotoQueue.NAME)
                    .first { entries -> entries.isNotEmpty() && entries.all { it.state.isFinished } }
            }
        }
        assertEquals(setOf("first", "second"), calls.toSet())
        assertEquals(2, calls.size)
        assertEquals(42.0, db.quickCaptureDao.getEntry(second.id)?.suggestedWeightInGrams)
    }

    @Test fun turningOffWaitingAutomationAllowsManualWork() = runTest {
        cloud()
        val snapshot = photo()
        controller.saveBackgroundPhotoAnalysis(true)
        controller.saveBackgroundPhotoAnalysis(false)
        CloudPhotoQueue(context).enqueue(listOf(requireNotNull(db.quickCaptureDao.getEntry(snapshot.id))),
            controller.settings.value, CloudPhotoQueue.MANUAL)
        assertEquals(2, work().size)
        assertEquals(1, work().count { "mode:${CloudPhotoQueue.MANUAL}" in it.tags })
    }

    @Test fun localAndDisabledAutomaticSchedulingNeverUpload() = runTest {
        val snapshot = photo()
        controller.schedule(snapshot.id)
        controller.saveBackgroundPhotoAnalysis(true)
        assertTrue(work().isEmpty())
        assertTrue(controller.runCloudBatch(AiProvider.Local, CloudPhotoQueue.AUTO, listOf(snapshot)))
        cloud()
        controller.saveBackgroundPhotoAnalysis(false)
        controller.runCloudBatch(AiProvider.OpenAI, CloudPhotoQueue.AUTO, listOf(snapshot))
        assertTrue(calls.isEmpty())
    }

    @Test fun manualCloudSurvivesBackgroundAndRestartSkipsStoredOutcomes() = runTest {
        cloud()
        val snapshot = photo()
        val entered = CompletableDeferred<Unit>()
        val reply = CompletableDeferred<Unit>()
        response = { entered.complete(Unit); reply.await(); ScaleRecognitionResult.Unreadable }
        val job = async { controller.runCloudBatch(AiProvider.OpenAI, CloudPhotoQueue.MANUAL, listOf(snapshot)) }
        entered.await()
        controller.onBackground()
        reply.complete(Unit)
        assertTrue(job.await())
        assertEquals("unreadable", db.quickCaptureDao.getEntry(snapshot.id)?.aiAnalysisStatus)
        controller.close()
        controller = newController()
        controller.runCloudBatch(AiProvider.OpenAI, CloudPhotoQueue.MANUAL, listOf(snapshot))
        assertEquals(listOf(snapshot.path), calls)
    }

    @Test fun deletedAssignedAndChangedPhotosAreSkippedAndNewPhotoRunsNext() = runTest {
        cloud()
        val deleted = photo("deleted")
        val assigned = photo("assigned")
        val changed = photo("changed")
        db.quickCaptureDao.deleteEntries(listOf(deleted.id))
        db.quickCaptureDao.markCompleted(listOf(assigned.id), 2)
        db.quickCaptureDao.saveAiResult(changed.id, changed.path, null, "unreadable", "OpenAI", "test", 3)
        controller.runCloudBatch(AiProvider.OpenAI, CloudPhotoQueue.REANALYZE, listOf(deleted, assigned, changed))
        assertTrue(calls.isEmpty())
        val fresh = photo("fresh")
        controller.runCloudBatch(AiProvider.OpenAI, CloudPhotoQueue.MANUAL, listOf(fresh))
        assertEquals(listOf("fresh"), calls)
    }

    @Test fun assignmentDuringRequestIsNotOverwritten() = runTest {
        cloud()
        val snapshot = photo()
        response = {
            val name = db.quickCaptureDao.resolveFoodName("Reis", "reis", 9)
            db.quickCaptureDao.processPhoto(snapshot.id, name.id, "Reis", 50.0)
            ScaleRecognitionResult.Recognized(99.0)
        }
        controller.runCloudBatch(AiProvider.OpenAI, CloudPhotoQueue.MANUAL, listOf(snapshot))
        assertNull(db.quickCaptureDao.getEntry(snapshot.id)?.suggestedWeightInGrams)
        assertEquals(50.0, db.quickCaptureDao.getEntry(snapshot.id)?.directWeightInGrams)
    }

    @Test fun cancellationAndProviderChangeKeepCompletedSuggestions() = runTest {
        cloud()
        val snapshot = photo()
        controller.runCloudBatch(AiProvider.OpenAI, CloudPhotoQueue.MANUAL, listOf(snapshot))
        controller.saveBackgroundPhotoAnalysis(true)
        val next = photo("next")
        controller.schedule(next.id)
        controller.cancelAnalysis()
        // Serialize behind cancellation before examining work.
        controller.saveSettings(AiProvider.Gemini, "gemini", false, null)
        controller.runCloudBatch(AiProvider.OpenAI, CloudPhotoQueue.MANUAL, listOf(next))
        assertEquals(listOf(snapshot.path), calls)
        assertEquals(42.0, db.quickCaptureDao.getEntry(snapshot.id)?.suggestedWeightInGrams)
        assertTrue(work().all { it.state.isFinished })
    }
}

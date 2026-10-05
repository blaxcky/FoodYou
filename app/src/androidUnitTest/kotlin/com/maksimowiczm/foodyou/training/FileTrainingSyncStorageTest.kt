package com.maksimowiczm.foodyou.training

import java.io.File
import java.security.MessageDigest
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class FileTrainingSyncStorageTest {
    private val account = TrainingAccount(uid = "A", email = null)
    private val progress = TrainingSyncProgress(TrainingCursor(1_000, 123_456_789, "session"), true, listOf("retry"))
    private fun file(root: File, owner: TrainingAccount = account): File {
        val hash = MessageDigest.getInstance("SHA-256").digest("${owner.project}\u0000${owner.uid}".toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(root, "$hash.json")
    }

    @Test fun legacyFileIgnoresDisabledSettingAndKeepsReportWithoutDetails() = runTest {
        val root = kotlin.io.path.createTempDirectory("training-storage").toFile()
        try {
            file(root).writeText("""{"enabled":false,"report":{"finishedAtMillis":123,"imported":2}}""")
            val storage = FileTrainingSyncStorage(root)
            val old = storage.load(account)
            assertEquals(TrainingSyncProgress(), old.progress)
            assertEquals(0, old.report!!.pending)
            assertTrue(old.report.successful)
            assertTrue(old.report.importedSessions.isEmpty())
            storage.saveProgress(account, progress)
            val reopened = FileTrainingSyncStorage(root).load(account)
            assertEquals(progress, reopened.progress)
            assertEquals(old.report, reopened.report)
        } finally { root.deleteRecursively() }
    }

    @Test fun manualImportWorksWithLegacyDisabledSetting() = runTest {
        val root = kotlin.io.path.createTempDirectory("training-storage").toFile()
        try {
            file(root).writeText("""{"enabled":false}""")
            val storage = FileTrainingSyncStorage(root)
            val coordinator = TrainingSyncCoordinator(FakeTrainingRemote(), storage,
                { _, _, _ -> TrainingImportResult.Imported }, backgroundScope)
            runCurrent()
            coordinator.startManualSync()
            runCurrent()
            val report = coordinator.state.first { it.report != null && !it.busy }.report!!
            assertEquals(1, report.imported)
            assertEquals(listOf(ImportedTrainingSession(TEST_SESSION, "2026-09-27", 210, 120)), report.importedSessions)
            assertEquals(report, FileTrainingSyncStorage(root).load(account).report)
        } finally { root.deleteRecursively() }
    }

    @Test fun concurrentUpdatesPreserveAllFieldsAndAccountsAreIsolated() = runTest {
        val root = kotlin.io.path.createTempDirectory("training-storage").toFile()
        try {
            val storage = FileTrainingSyncStorage(root)
            val report = TrainingSyncReport(123, imported = 1, pending = 1,
                importedSessions = listOf(ImportedTrainingSession("session", "2026-09-27", 210, 120)))
            coroutineScope {
                launch { storage.saveProgress(account, progress) }
                launch { storage.saveReport(account, report) }
            }
            val owner = storage.load(account)
            assertEquals(progress, owner.progress)
            assertEquals(report, owner.report)
            assertEquals(report, FileTrainingSyncStorage(root).load(account).report)
            assertEquals(TrainingSyncProgress(), storage.load(account.copy(uid = "B")).progress)
            assertFalse(owner.report!!.successful)
            assertTrue(owner.report.description().contains("1 weiterhin offen"))
            assertFalse(root.listFiles()!!.any { it.extension == "tmp" })
        } finally { root.deleteRecursively() }
    }

    @Test fun resetClearsProgressAndReportsForEveryAccount() = runTest {
        val root = kotlin.io.path.createTempDirectory("training-storage").toFile()
        try {
            val storage = FileTrainingSyncStorage(root)
            val other = account.copy(project = "other")
            for (owner in listOf(account, other)) {
                storage.saveProgress(owner, progress)
                storage.saveReport(owner, TrainingSyncReport(123, imported = 3))
            }
            storage.resetAllProgress()
            for (owner in listOf(account, other)) {
                val reset = FileTrainingSyncStorage(root).load(owner)
                assertEquals(TrainingSyncProgress(), reset.progress)
                assertNull(reset.report)
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun failedWriteKeepsPreviousRecordAndCorruptFilesAreNotSilentlyOverwritten() = runTest {
        val root = kotlin.io.path.createTempDirectory("training-storage").toFile()
        try {
            val storage = FileTrainingSyncStorage(root)
            storage.saveProgress(account, progress)
            val target = file(root)
            val temp = File(root, "${target.name}.tmp").apply { mkdirs() }
            assertFails { storage.saveReport(account, TrainingSyncReport(100)) }
            assertEquals(progress, storage.load(account).progress)
            temp.deleteRecursively()
            target.writeText("broken-json")
            assertFails { storage.load(account) }
            assertFails { storage.saveReport(account, TrainingSyncReport(100)) }
            assertEquals("broken-json", target.readText())
        } finally { root.deleteRecursively() }
    }
    @Test fun restoreResetRejectsAnInvalidStorageDirectory() = runTest {
        val root = kotlin.io.path.createTempDirectory("training-storage").toFile()
        try {
            val invalid = File(root, "not-a-directory").apply { writeText("invalid") }
            assertFails { FileTrainingSyncStorage(invalid).resetAllProgress() }
            FileTrainingSyncStorage(File(root, "missing")).resetAllProgress()
        } finally { root.deleteRecursively() }
    }

}

package com.maksimowiczm.foodyou.training

import java.io.File
import java.security.MessageDigest
import kotlin.test.*
import kotlinx.coroutines.*
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

    @Test fun legacyFileKeepsDisabledSettingAndStartsWithoutCursor() = runTest {
        val root = kotlin.io.path.createTempDirectory("training-storage").toFile()
        try {
            file(root).writeText("""{"enabled":false,"report":{"finishedAtMillis":123,"imported":2}}""")
            val storage = FileTrainingSyncStorage(root)
            val old = storage.load(account)
            assertFalse(old.enabled)
            assertEquals(TrainingSyncProgress(), old.progress)
            assertEquals(0, old.report!!.pending)
            assertTrue(old.report.successful)
            storage.saveProgress(account, progress)
            val reopened = FileTrainingSyncStorage(root).load(account)
            assertEquals(progress, reopened.progress)
            assertFalse(reopened.enabled)
            assertEquals(old.report, reopened.report)
        } finally { root.deleteRecursively() }
    }

    @Test fun concurrentUpdatesPreserveAllFieldsAndAccountsAreIsolated() = runTest {
        val root = kotlin.io.path.createTempDirectory("training-storage").toFile()
        try {
            val storage = FileTrainingSyncStorage(root)
            val report = TrainingSyncReport(123, pending = 1)
            coroutineScope {
                launch { storage.saveProgress(account, progress) }
                launch { storage.setEnabled(account, false) }
                launch { storage.saveReport(account, report) }
            }
            val owner = storage.load(account)
            assertFalse(owner.enabled)
            assertEquals(progress, owner.progress)
            assertEquals(report, owner.report)
            assertTrue(storage.load(account.copy(project = "other")).enabled)
            assertEquals(TrainingSyncProgress(), storage.load(account.copy(uid = "B")).progress)
            assertFalse(owner.report!!.successful)
            assertTrue(owner.report.description().contains("1 weiterhin offen"))
            assertFalse(root.listFiles()!!.any { it.extension == "tmp" })
        } finally { root.deleteRecursively() }
    }

    @Test fun resetClearsProgressAndReportsForEveryAccountButKeepsSwitches() = runTest {
        val root = kotlin.io.path.createTempDirectory("training-storage").toFile()
        try {
            val storage = FileTrainingSyncStorage(root)
            val other = account.copy(project = "other")
            for (owner in listOf(account, other)) {
                storage.saveProgress(owner, progress)
                storage.saveReport(owner, TrainingSyncReport(123, imported = 3))
            }
            storage.setEnabled(other, false)
            storage.resetAllProgress()
            for (owner in listOf(account, other)) {
                val reset = FileTrainingSyncStorage(root).load(owner)
                assertEquals(TrainingSyncProgress(), reset.progress)
                assertNull(reset.report)
            }
            assertTrue(storage.load(account).enabled)
            assertFalse(storage.load(other).enabled)
        } finally { root.deleteRecursively() }
    }

    @Test fun failedWriteKeepsPreviousRecordAndCorruptFilesAreNotSilentlyOverwritten() = runTest {
        val root = kotlin.io.path.createTempDirectory("training-storage").toFile()
        try {
            val storage = FileTrainingSyncStorage(root)
            storage.saveProgress(account, progress)
            val target = file(root)
            val temp = File(root, "${target.name}.tmp").apply { mkdirs() }
            assertFails { storage.setEnabled(account, false) }
            assertEquals(progress, storage.load(account).progress)
            assertTrue(storage.load(account).enabled)
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

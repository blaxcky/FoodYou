package com.maksimowiczm.foodyou.training

import android.content.Context
import com.maksimowiczm.foodyou.app.widget.updateCalorieWidgetValues
import com.maksimowiczm.foodyou.sync.SyncLog
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

internal fun createTrainingSync(context: Context, dao: TrainingImportDao, syncLog: SyncLog): TrainingSync = TrainingSyncCoordinator(
    FirebaseTrainingRemote.create(context),
    FileTrainingSyncStorage(File(context.noBackupFilesDir, "training-sync")),
    dao::importDocument,
    CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    onAccountChanged = { updateCalorieWidgetValues() },
    syncLog = syncLog,
    onImported = { updateCalorieWidgetValues() },
)

/** All file access is serialized and runs off the UI thread. */
internal class FileTrainingSyncStorage(private val directory: File) : TrainingSyncStorage {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private fun file(account: TrainingAccount): File {
        val hash = MessageDigest.getInstance("SHA-256").digest("${account.project}\u0000${account.uid}".toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(directory, "$hash.json")
    }
    private fun read(file: File): StoredTrainingSync =
        if (file.isFile) json.decodeFromString(file.readText()) else StoredTrainingSync()

    private fun write(target: File, value: StoredTrainingSync) {
        check(directory.isDirectory || directory.mkdirs()) { "Trainingsverzeichnis konnte nicht angelegt werden." }
        val temp = File(directory, "${target.name}.tmp")
        try {
            java.io.FileOutputStream(temp).use {
                it.write(json.encodeToString(value).encodeToByteArray())
                it.fd.sync()
            }
            java.nio.file.Files.move(temp.toPath(), target.toPath(),
                java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        } finally { temp.delete() }
    }
    override suspend fun load(account: TrainingAccount): StoredTrainingSync = withContext(Dispatchers.IO) {
        mutex.withLock { read(file(account)) }
    }
    private suspend fun update(account: TrainingAccount, transform: (StoredTrainingSync) -> StoredTrainingSync) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val target = file(account)
            write(target, transform(read(target)))
        }
    }
    override suspend fun saveReport(account: TrainingAccount, report: TrainingSyncReport) = update(account) { it.copy(report = report) }
    override suspend fun saveProgress(account: TrainingAccount, progress: TrainingSyncProgress) = update(account) { it.copy(progress = progress) }
    override suspend fun resetAllProgress() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!directory.exists()) return@withLock
            check(directory.isDirectory) { "Trainingsverzeichnis ist ungültig." }
            val files = checkNotNull(directory.listFiles()) { "Trainingsverzeichnis konnte nicht gelesen werden." }
            files.filter { it.isFile && it.extension == "json" }.forEach {
                write(it, read(it).copy(progress = TrainingSyncProgress(), report = null))
            }
            Unit
        }
    }
}

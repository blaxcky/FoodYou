package com.maksimowiczm.foodyou.training

import android.content.Context
import com.maksimowiczm.foodyou.app.widget.updateCalorieWidgetValues
import com.maksimowiczm.foodyou.sync.SyncLog
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal fun createTrainingSync(context: Context, dao: TrainingImportDao, syncLog: SyncLog): TrainingSync = TrainingSyncCoordinator(
    FirebaseTrainingRemote.create(context),
    FileTrainingSyncStorage(File(context.noBackupFilesDir, "training-sync")),
    dao::importDocument,
    CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    onAccountChanged = { updateCalorieWidgetValues() },
    syncLog = syncLog,
)

@Serializable
private data class StoredTrainingSync(val enabled: Boolean = true, val report: TrainingSyncReport? = null)

internal class FileTrainingSyncStorage(private val directory: File) : TrainingSyncStorage {
    private fun file(account: TrainingAccount): File {
        val hash = MessageDigest.getInstance("SHA-256").digest("${account.project}\u0000${account.uid}".toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(directory, "$hash.json")
    }
    private fun read(account: TrainingAccount): StoredTrainingSync = try {
        val file = file(account)
        if (file.isFile) Json.decodeFromString(file.readText()) else StoredTrainingSync()
    } catch (_: Exception) { StoredTrainingSync() }
    private fun write(account: TrainingAccount, value: StoredTrainingSync) {
        directory.mkdirs()
        val target = file(account)
        val temp = File(directory, "${target.name}.tmp")
        temp.writeText(Json.encodeToString(value))
        check(temp.renameTo(target))
    }
    override fun enabled(account: TrainingAccount) = read(account).enabled
    override fun report(account: TrainingAccount) = read(account).report
    override fun setEnabled(account: TrainingAccount, enabled: Boolean) = write(account, read(account).copy(enabled = enabled))
    override fun saveReport(account: TrainingAccount, report: TrainingSyncReport) = write(account, read(account).copy(report = report))
}

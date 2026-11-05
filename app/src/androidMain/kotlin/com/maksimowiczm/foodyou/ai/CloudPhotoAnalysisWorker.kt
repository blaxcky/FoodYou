package com.maksimowiczm.foodyou.ai

import android.content.Context
import androidx.work.*
import com.maksimowiczm.foodyou.food.infrastructure.room.QuickCaptureLogEntryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.core.context.GlobalContext

@Serializable
internal data class QueuedScalePhoto(val id: Long, val path: String, val analyzedAt: Long?) {
    fun matches(entry: QuickCaptureLogEntryEntity): Boolean =
        entry.id == id && entry.photoPath == path && entry.aiAnalyzedAt == analyzedAt && entry.isOpenPhoto()
}

internal fun QuickCaptureLogEntryEntity.isOpenPhoto() =
    photoPath != null && foodName.isNullOrBlank() && completedAt == null

/** Only small identity snapshots enter WorkManager; images and secrets stay in app storage. */
internal class CloudPhotoQueue(context: Context) {
    private val manager = WorkManager.getInstance(context)
    private val mutex = Mutex()
    val work = manager.getWorkInfosForUniqueWorkFlow(NAME)

    suspend fun enqueue(photos: List<QuickCaptureLogEntryEntity>, settings: AiSettings, mode: String) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val active = manager.getWorkInfosForUniqueWork(NAME).get().filter { !it.state.isFinished }
                val relevant = active.filter {
                    it.state == WorkInfo.State.RUNNING || settings.backgroundPhotoAnalysisEnabled || "mode:$AUTO" !in it.tags
                }
                if (mode != AUTO && relevant.isNotEmpty()) return@withLock
                val pendingIds = relevant.flatMap { it.tags }.toSet()
                val selected = photos.filter { "photo:${it.id}" !in pendingIds }
                val parallelism = when (settings.provider) {
                    AiProvider.OpenAI -> settings.openAi.photoParallelism
                    AiProvider.Claude -> settings.claude.photoParallelism
                    else -> 1
                }
                var chain: WorkContinuation? = null
                for (batch in selected.chunked(parallelism.coerceIn(1, 5))) {
                    val request = OneTimeWorkRequestBuilder<CloudPhotoAnalysisWorker>()
                        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                        .addTag("mode:$mode")
                        .setInputData(workDataOf(
                            "provider" to settings.provider.name,
                            "mode" to mode,
                            "photos" to Json.encodeToString(batch.map { QueuedScalePhoto(it.id, requireNotNull(it.photoPath), it.aiAnalyzedAt) }),
                        ))
                    batch.forEach { request.addTag("photo:${it.id}") }
                    val workRequest = request.build()
                    chain = chain?.then(workRequest)
                        ?: manager.beginUniqueWork(NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, workRequest)
                }
                chain?.enqueue()?.result?.get()
            }
        }

    suspend fun cancel() = withContext(Dispatchers.IO) {
        mutex.withLock { manager.cancelUniqueWork(NAME).result.get(); Unit }
    }

    companion object {
        const val NAME = "quick_capture_cloud_analysis"
        const val AUTO = "automatic"
        const val MANUAL = "manual"
        const val REANALYZE = "reanalyze"
    }
}

class CloudPhotoAnalysisWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val provider = inputData.getString("provider")?.let { runCatching { AiProvider.valueOf(it) }.getOrNull() }
            ?: return Result.failure()
        if (provider.localModel != null) return Result.failure()
        val photos = runCatching {
            Json.decodeFromString<List<QueuedScalePhoto>>(inputData.getString("photos") ?: "[]")
        }.getOrElse { return Result.failure() }
        val controller = GlobalContext.get().get<AiController>() as AndroidAiController
        return if (controller.runCloudBatch(provider, inputData.getString("mode") ?: CloudPhotoQueue.AUTO, photos))
            Result.success() else Result.failure()
    }
}

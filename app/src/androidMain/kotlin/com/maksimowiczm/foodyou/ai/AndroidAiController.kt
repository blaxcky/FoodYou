package com.maksimowiczm.foodyou.ai

import android.content.Context
import com.maksimowiczm.foodyou.app.infrastructure.room.FoodYouDatabase
import com.maksimowiczm.foodyou.common.crypto.MasterCrypto
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Private no-backup state keeps keys and large downloaded models out of full app backups too. */
internal class AndroidAiController(
    private val context: Context,
    database: FoodYouDatabase,
    private val crypto: MasterCrypto,
) : AiController {
    private val directory = File(context.noBackupFilesDir, "ai").apply { mkdirs() }
    private val preferences = File(directory, "settings.properties")
    private val keyFile = File(directory, "gemini.key")
    private val photoDirectory = File(context.filesDir, "food-snap-photos")
    private val cacheDirectory = File(context.cacheDir, "gemma")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val dao = database.quickCaptureDao
    private val configMutex = Mutex()
    private val store = GemmaModelStore(File(directory, "models"))
    private val diagnostics = AiDiagnostics(context)
    private val coordinator = ScaleAnalysisCoordinator()
    private val mutableSettings = MutableStateFlow(readSettings())
    override val settings: StateFlow<AiSettings> = mutableSettings
    override val download = store.state
    override val analysis = coordinator.progress
    private var analysisJob: Job? = null
    private var downloadJob: Job? = null
    private val client = HttpClient(OkHttp) {
        install(HttpTimeout) { requestTimeoutMillis = 60_000; connectTimeoutMillis = 15_000; socketTimeoutMillis = 60_000 }
        // Dedicated client deliberately has no request/body logging or shared authentication.
    }

    private fun readSettings(): AiSettings {
        val props = java.util.Properties()
        try { if (preferences.isFile) preferences.inputStream().use(props::load) } catch (_: java.io.IOException) { props.clear() }
        return AiSettings(
            provider = runCatching { AiProvider.valueOf(props.getProperty("provider", "Local")) }.getOrDefault(AiProvider.Local),
            model = props.getProperty("model", "gemini-3.8-flash"),
            hasApiKey = keyFile.isFile,
        )
    }

    override suspend fun saveSettings(provider: AiProvider, model: String, newKey: String?, deleteKey: Boolean) {
        require(Regex("[A-Za-z0-9._-]+").matches(model.trim())) { "Bitte einen gültigen Modellnamen eingeben." }
        withContext(Dispatchers.IO) {
            configMutex.withLock {
                if (deleteKey) check(!keyFile.exists() || keyFile.delete())
                else if (newKey != null) {
                    require(newKey.isNotBlank())
                    atomicWrite(keyFile, crypto.encrypt(newKey.trim().encodeToByteArray()))
                }
                val props = java.util.Properties().apply {
                    setProperty("provider", provider.name)
                    setProperty("model", model.trim())
                }
                val bytes = java.io.ByteArrayOutputStream().use { props.store(it, null); it.toByteArray() }
                atomicWrite(preferences, bytes)
                mutableSettings.value = AiSettings(provider, model.trim(), keyFile.isFile)
            }
        }
    }

    private fun atomicWrite(file: File, bytes: ByteArray) {
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeBytes(bytes)
        check(temp.renameTo(file))
    }

    private suspend fun configuration(): Pair<AiSettings, String> = withContext(Dispatchers.IO) {
        configMutex.withLock {
            val settings = mutableSettings.value
            val key = if (settings.provider == AiProvider.Gemini && keyFile.isFile)
                crypto.decrypt(keyFile.readBytes()).decodeToString() else ""
            settings to key
        }
    }

    override suspend fun testConnection(): String {
        return try {
            val (settings, key) = configuration()
            GeminiScaleWeightRecognizer(client, key, settings.model, photoDirectory).testConnection()
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) { "API-Key konnte nicht gelesen werden. Bitte erneut speichern." }
    }

    override fun startAnalysis(reanalyze: Boolean) {
        if (analysisJob?.isActive == true || downloadJob?.isActive == true) return
        analysisJob = scope.launch {
            // Snapshot both configuration and photo IDs before opening the engine.
            val photos = dao.observeEntries().first().filter {
                it.photoPath != null && it.foodName.isNullOrBlank() && it.completedAt == null &&
                    (reanalyze || it.suggestedWeightInGrams == null)
            }.sortedBy { it.createdAt }.map { AnalysisPhoto(it.id, requireNotNull(it.photoPath)) }
            var selected = settings.value
            withContext(Dispatchers.IO) {
                coordinator.run(photos,
                    open = {
                        val (config, key) = configuration()
                        selected = config
                        when (config.provider) {
                            AiProvider.Local -> LocalScaleWeightRecognizer.open(context, photoDirectory, diagnostics)
                            AiProvider.Gemini -> GeminiScaleWeightRecognizer(client, key, config.model, photoDirectory)
                        }
                    },
                    isPending = { id -> dao.getEntry(id)?.let { it.photoPath != null && it.foodName.isNullOrBlank() && it.completedAt == null } == true },
                    save = { photo, result ->
                        dao.saveAiResult(photo.id, photo.path,
                            (result as? ScaleRecognitionResult.Recognized)?.grams,
                            when (result) {
                                is ScaleRecognitionResult.Recognized -> "recognized"
                                ScaleRecognitionResult.Unreadable -> "unreadable"
                                is ScaleRecognitionResult.Error -> when (result.kind) {
                                    ScaleErrorKind.ResponseFormat -> "error_format"
                                    ScaleErrorKind.NonWholeGrams -> "error_whole_grams"
                                    ScaleErrorKind.Truncated -> "error_truncated"
                                    else -> "error"
                                }
                            }, selected.provider.name,
                            if (selected.provider == AiProvider.Local) "gemma-4-E4B-it@$GEMMA_REVISION" else selected.model,
                            System.currentTimeMillis())
                    })
            }
        }
    }

    override suspend fun diagnosticReport(): String = diagnostics.report()

    override fun cancelAnalysis() { analysisJob?.cancel() }
    override fun startDownload() {
        if (downloadJob?.isActive == true || analysisJob?.isActive == true) return
        downloadJob = scope.launch { store.download() }
    }
    override fun pauseDownload() { downloadJob?.cancel() }
    override fun deleteModel() {
        if (downloadJob?.isActive == true || analysisJob?.isActive == true) return
        store.delete()
        cacheDirectory.deleteRecursively()
    }
    override fun onBackground() { cancelAnalysis(); pauseDownload() }
}

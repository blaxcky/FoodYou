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
    private val recognizerFactory: (suspend (AiSettings, String) -> ScaleWeightRecognizer)? = null,
) : AiController, com.maksimowiczm.foodyou.food.domain.usecase.QuickCapturePhotoAnalysisScheduler {
    private val directory = File(context.noBackupFilesDir, "ai").apply { mkdirs() }
    private val preferences = File(directory, "settings.properties")
    private val claudeKeyFile = File(directory, "claude.key")
    private val openAiKeyFile = File(directory, "openai.key")
    private val keyFile = File(directory, "gemini.key")
    private val photoDirectory = File(context.filesDir, "food-snap-photos")
    private val cacheDirectory = File(context.cacheDir, "gemma")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate +
        CoroutineExceptionHandler { _, e -> android.util.Log.w("FoodYou", "AI controller task failed", e) })
    private val dao = database.quickCaptureDao
    private val configMutex = Mutex()
    private val stores = GemmaModel.entries.associateWith { GemmaModelStore(File(directory, "models"), it) }
    private val diagnostics = AiDiagnostics(context)
    private val coordinator = ScaleAnalysisCoordinator()
    private val mutableSettings = MutableStateFlow(readSettings())
    override val settings: StateFlow<AiSettings> = mutableSettings
    override val downloads: StateFlow<Map<AiProvider, ModelDownloadState>> = combine(
        requireNotNull(stores[GemmaModel.E4B]).state,
        requireNotNull(stores[GemmaModel.E2B]).state,
    ) { e4b, e2b -> mapOf(AiProvider.Local to e4b, AiProvider.LocalE2B to e2b) }
        .stateIn(scope, SharingStarted.Eagerly, mapOf(
            AiProvider.Local to requireNotNull(stores[GemmaModel.E4B]).state.value,
            AiProvider.LocalE2B to requireNotNull(stores[GemmaModel.E2B]).state.value,
        ))
    private val executionMutex = Mutex()
    private val cloudQueue by lazy { CloudPhotoQueue(context) }
    private val mutableAnalysis = MutableStateFlow(AnalysisProgress())
    override val analysis: StateFlow<AnalysisProgress> = mutableAnalysis
    @Volatile private var observingCloud = false

    @Synchronized private fun observeCloud() {
        if (observingCloud) return
        observingCloud = true
        scope.launch {
            combine(coordinator.progress, cloudQueue.work, settings) { progress, work, config ->
                val active = work.filter {
                    !it.state.isFinished && (it.state == androidx.work.WorkInfo.State.RUNNING ||
                        config.backgroundPhotoAnalysisEnabled || "mode:${CloudPhotoQueue.AUTO}" !in it.tags)
                }
                progress.copy(queued = active.isNotEmpty())
            }.collect { mutableAnalysis.value = it }
        }
    }

    init {
        scope.launch { coordinator.progress.collect { if (!observingCloud) mutableAnalysis.value = it } }
    }
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
            visionOnCpu = props.getProperty("vision_backend") == "cpu",
            backgroundPhotoAnalysisEnabled = props.getProperty("background_photo_analysis") == "true",
            nutritionProvider = runCatching { NutritionProvider.valueOf(props.getProperty("nutrition.provider", "")) }
                .getOrElse { if (claudeKeyFile.isFile && !openAiKeyFile.isFile) NutritionProvider.Claude else NutritionProvider.OpenAI },
            claude = ClaudeSettings(
                ClaudeTask(props.getProperty("claude.photo.model", "claude-sonnet-5-5"), props.getProperty("claude.photo.effort", "low").takeIf { it.isNotEmpty() }),
                ClaudeTask(props.getProperty("claude.nutrition.model", "claude-sonnet-5-5"), props.getProperty("claude.nutrition.effort", "medium").takeIf { it.isNotEmpty() }),
                claudeKeyFile.isFile,
                props.getProperty("claude.photo.parallelism")?.toIntOrNull()?.takeIf { it in 1..5 } ?: 4,
            ),
            openAi = OpenAiSettings(
                OpenAiTask(props.getProperty("openai.photo.model", "gpt-6-luna"), props.getProperty("openai.photo.effort", "low").takeIf { it.isNotEmpty() }),
                OpenAiTask(props.getProperty("openai.nutrition.model", "gpt-6.1-sol"), props.getProperty("openai.nutrition.effort", "medium").takeIf { it.isNotEmpty() }),
                openAiKeyFile.isFile,
                props.getProperty("openai.photo.parallelism")?.toIntOrNull()?.takeIf { it in 1..5 } ?: 4,
            ),
        )
    }

    override suspend fun saveSettings(
        provider: AiProvider,
        model: String,
        visionOnCpu: Boolean,
        newKey: String?,
        deleteKey: Boolean,
    ) {
        val previousProvider = settings.value.provider
        require(Regex("[A-Za-z0-9._-]+").matches(model.trim())) { "Bitte einen gültigen Modellnamen eingeben." }
        withContext(Dispatchers.IO) {
            configMutex.withLock {
                if (deleteKey) check(!keyFile.exists() || keyFile.delete())
                else if (newKey != null) {
                    require(newKey.isNotBlank())
                    atomicWrite(keyFile, crypto.encrypt(newKey.trim().encodeToByteArray()))
                }
                val props = java.util.Properties().apply {
                    if (preferences.isFile) preferences.inputStream().use(::load)
                    setProperty("provider", provider.name)
                    setProperty("model", model.trim())
                    setProperty("vision_backend", if (visionOnCpu) "cpu" else "gpu")
                }
                val bytes = java.io.ByteArrayOutputStream().use { props.store(it, null); it.toByteArray() }
                atomicWrite(preferences, bytes)
                mutableSettings.value = readSettings()
            }
        }
        if (previousProvider != provider && observingCloud) cloudQueue.cancel()
    }

    override suspend fun saveOpenAi(settings: OpenAiSettings, newKey: String?, deleteKey: Boolean) {
        require(settings.photoParallelism in 1..5)
        listOf(settings.photo, settings.nutrition).forEach {
            require(it.model.matches(Regex("[A-Za-z0-9._:/-]+")))
            require(it.effort == null || it.effort.matches(Regex("[a-z]+")))
            require(it.effort == null || supportedEfforts(it.model)?.contains(it.effort) != false)
        }
        withContext(Dispatchers.IO) { configMutex.withLock {
            if (deleteKey) check(!openAiKeyFile.exists() || openAiKeyFile.delete())
            else if (newKey != null) {
                require(newKey.isNotBlank())
                atomicWrite(openAiKeyFile, crypto.encrypt(newKey.trim().encodeToByteArray()))
            }
            val props = java.util.Properties()
            if (preferences.isFile) preferences.inputStream().use(props::load)
            props.setProperty("openai.photo.parallelism", settings.photoParallelism.toString())
            for ((name, task) in listOf("photo" to settings.photo, "nutrition" to settings.nutrition)) {
                props.setProperty("openai.$name.model", task.model)
                props.setProperty("openai.$name.effort", task.effort.orEmpty())
            }
            atomicWrite(preferences, java.io.ByteArrayOutputStream().use { props.store(it, null); it.toByteArray() })
            mutableSettings.value = readSettings()
        } }
    }

    private suspend fun openAiConfiguration(): Pair<OpenAiSettings, OpenAiClient> = withContext(Dispatchers.IO) {
        configMutex.withLock {
            val key = if (openAiKeyFile.isFile) crypto.decrypt(openAiKeyFile.readBytes()).decodeToString() else ""
            settings.value.openAi to OpenAiClient(client, key)
        }
    }

    override suspend fun testOpenAi(nutrition: Boolean): String = try {
        val (config, api) = openAiConfiguration()
        api.request(if (nutrition) config.nutrition else config.photo, "Reply OK.", web = nutrition)
        "OpenAI: OK"
    } catch (e: CancellationException) { throw e
    } catch (e: Exception) { e.message ?: "OpenAI: Fehler / error" }

    override suspend fun saveClaude(settings: ClaudeSettings, newKey: String?, deleteKey: Boolean) {
        require(settings.photoParallelism in 1..5)
        listOf(settings.photo, settings.nutrition).forEach {
            require(it.model.matches(Regex("[A-Za-z0-9._:/-]+")))
            require(it.effort == null || it.effort.matches(Regex("[a-z]+")))
            require(it.effort == null || claudeSupportedEfforts(it.model)?.contains(it.effort) != false)
        }
        withContext(Dispatchers.IO) { configMutex.withLock {
            if (deleteKey) check(!claudeKeyFile.exists() || claudeKeyFile.delete())
            else if (newKey != null) {
                require(newKey.isNotBlank())
                atomicWrite(claudeKeyFile, crypto.encrypt(newKey.trim().encodeToByteArray()))
            }
            val props = java.util.Properties()
            if (preferences.isFile) preferences.inputStream().use(props::load)
            props.setProperty("claude.photo.parallelism", settings.photoParallelism.toString())
            for ((name, task) in listOf("photo" to settings.photo, "nutrition" to settings.nutrition)) {
                props.setProperty("claude.$name.model", task.model)
                props.setProperty("claude.$name.effort", task.effort.orEmpty())
            }
            atomicWrite(preferences, java.io.ByteArrayOutputStream().use { props.store(it, null); it.toByteArray() })
            mutableSettings.value = readSettings()
        } }
    }

    private suspend fun claudeConfiguration(): Pair<ClaudeSettings, ClaudeClient> = withContext(Dispatchers.IO) {
        configMutex.withLock {
            val key = if (claudeKeyFile.isFile) crypto.decrypt(claudeKeyFile.readBytes()).decodeToString() else ""
            settings.value.claude to ClaudeClient(client, key)
        }
    }

    override suspend fun testClaude(nutrition: Boolean): String = try {
        val (config, api) = claudeConfiguration()
        api.request(if (nutrition) config.nutrition else config.photo, if (nutrition) "Search the web for nutrition values of an apple, then reply OK." else "Reply OK.", web = nutrition)
        "Claude: OK"
    } catch (e: CancellationException) { throw e
    } catch (e: Exception) { e.message ?: "Claude: Fehler / error" }

    override suspend fun saveNutritionProvider(provider: NutritionProvider) {
        withContext(Dispatchers.IO) { configMutex.withLock {
            val props = java.util.Properties()
            if (preferences.isFile) preferences.inputStream().use(props::load)
            props.setProperty("nutrition.provider", provider.name)
            atomicWrite(preferences, java.io.ByteArrayOutputStream().use { props.store(it, null); it.toByteArray() })
            mutableSettings.value = readSettings()
        } }
    }

    override suspend fun estimateNutrition(prompt: String, provider: NutritionProvider): NutritionEstimate = when (provider) {
        NutritionProvider.OpenAI -> {
            val (config, api) = openAiConfiguration()
            api.request(config.nutrition, prompt, web = true)
        }
        NutritionProvider.Claude -> {
            val (config, api) = claudeConfiguration()
            api.request(config.nutrition, prompt, web = true)
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
            val selectedKey = when (settings.provider) {
                AiProvider.Claude -> claudeKeyFile
                AiProvider.OpenAI -> openAiKeyFile
                AiProvider.Gemini -> keyFile
                else -> null
            }
            val key = if (selectedKey?.isFile == true) crypto.decrypt(selectedKey.readBytes()).decodeToString() else ""
            settings to key
        }
    }

    override suspend fun testConnection(): String {
        return try {
            val (settings, key) = configuration()
            if (settings.provider == AiProvider.Claude) testClaude(false)
            else if (settings.provider == AiProvider.OpenAI) testOpenAi(false)
            else GeminiScaleWeightRecognizer(client, key, settings.model, photoDirectory).testConnection()
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) { "API-Key konnte nicht gelesen werden. Bitte erneut speichern." }
    }

    override suspend fun saveBackgroundPhotoAnalysis(enabled: Boolean) {
        withContext(Dispatchers.IO) { configMutex.withLock {
            val props = java.util.Properties()
            if (preferences.isFile) preferences.inputStream().use(props::load)
            props.setProperty("background_photo_analysis", enabled.toString())
            atomicWrite(preferences, java.io.ByteArrayOutputStream().use { props.store(it, null); it.toByteArray() })
            mutableSettings.value = readSettings()
        } }
        reconcileBackgroundAnalysis()
    }

    override suspend fun schedule(photoId: Long) {
        val config = settings.value
        if (!config.backgroundPhotoAnalysisEnabled || config.provider.localModel != null) return
        val entry = dao.getEntry(photoId) ?: return
        if (!entry.isOpenPhoto() || entry.aiAnalysisStatus != null) return
        observeCloud()
        cloudQueue.enqueue(listOf(entry), config, CloudPhotoQueue.AUTO)
    }

    override suspend fun reconcileBackgroundAnalysis() {
        val config = settings.value
        if (config.provider.localModel != null) return
        observeCloud()
        if (!config.backgroundPhotoAnalysisEnabled) return
        cloudQueue.enqueue(dao.observeEntries().first().filter {
            it.isOpenPhoto() && it.aiAnalysisStatus == null
        }.sortedBy { it.createdAt }, config, CloudPhotoQueue.AUTO)
    }

    override fun startAnalysis(reanalyze: Boolean) {
        if (analysisJob?.isActive == true || downloadJob?.isActive == true || analysis.value.queued || analysis.value.running) return
        analysisJob = scope.launch {
            val config = settings.value
            val entries = dao.observeEntries().first().filter {
                it.isOpenPhoto() && (reanalyze || it.suggestedWeightInGrams == null)
            }.sortedBy { it.createdAt }
            if (config.provider.localModel == null) {
                observeCloud()
                cloudQueue.enqueue(entries, config, if (reanalyze) CloudPhotoQueue.REANALYZE else CloudPhotoQueue.MANUAL)
            } else executionMutex.withLock {
                runPhotos(entries.map { AnalysisPhoto(it.id, requireNotNull(it.photoPath)) })
            }
        }
    }

    internal suspend fun runCloudBatch(provider: AiProvider, mode: String, snapshots: List<QueuedScalePhoto>): Boolean =
        executionMutex.withLock {
            observeCloud()
            if (settings.value.provider != provider || provider.localModel != null ||
                (mode == CloudPhotoQueue.AUTO && !settings.value.backgroundPhotoAnalysisEnabled)) return@withLock true
            val pending = snapshots.filter { snapshot -> dao.getEntry(snapshot.id)?.let(snapshot::matches) == true }
            runPhotos(pending.map { AnalysisPhoto(it.id, it.path) }, snapshots, mode, provider)
        }

    private suspend fun runPhotos(
        photos: List<AnalysisPhoto>, snapshots: List<QueuedScalePhoto>? = null,
        mode: String? = null, expectedProvider: AiProvider? = null,
    ): Boolean {
        var selected = settings.value
        var fatal = false
        withContext(Dispatchers.IO) {
            coordinator.run(photos,
                open = {
                    val (config, key) = configuration()
                    check(expectedProvider == null || config.provider == expectedProvider)
                    selected = config
                    recognizerFactory?.invoke(config, key) ?: when (config.provider) {
                        AiProvider.Local, AiProvider.LocalE2B -> LocalScaleWeightRecognizer.open(
                            context, photoDirectory, diagnostics, requireNotNull(config.provider.localModel),
                            visionOnCpu = config.visionOnCpu,
                        )
                        AiProvider.Claude -> {
                            ClaudeScaleWeightRecognizer(ClaudeClient(client, key), config.claude) { path ->
                                android.util.Base64.encodeToString(decodeScalePhoto(photoDirectory, path), android.util.Base64.NO_WRAP)
                            }
                        }
                        AiProvider.OpenAI -> {
                            OpenAiScaleWeightRecognizer(OpenAiClient(client, key), config.openAi) { path ->
                                android.util.Base64.encodeToString(decodeScalePhoto(photoDirectory, path), android.util.Base64.NO_WRAP)
                            }
                        }
                        AiProvider.Gemini -> GeminiScaleWeightRecognizer(client, key, config.model, photoDirectory)
                    }
                },
                isPending = { id -> dao.getEntry(id)?.let { entry ->
                    entry.isOpenPhoto() && (expectedProvider == null || settings.value.provider == expectedProvider) && (snapshots == null || snapshots.any { it.id == id && it.matches(entry) }) &&
                        (mode != CloudPhotoQueue.AUTO || settings.value.backgroundPhotoAnalysisEnabled)
                } == true },
                save = { photo, result ->
                    if (result is ScaleRecognitionResult.Error && result.fatal) fatal = true
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
                        selected.provider.localModel?.artifactName ?: selected.photoModelLabel().substringBefore(" · "),
                        System.currentTimeMillis())
                })
        }
        return !fatal
    }

    internal fun close() { scope.cancel(); client.close() }

    override suspend fun diagnosticReport(): String = diagnostics.report()

    override fun cancelAnalysis() {
        analysisJob?.cancel()
        if (observingCloud) scope.launch { cloudQueue.cancel() }
    }
    override fun startDownload(provider: AiProvider) {
        if (downloadJob?.isActive == true || analysisJob?.isActive == true) return
        val store = provider.localModel?.let(stores::get) ?: return
        downloadJob = scope.launch { store.download() }
    }
    override fun pauseDownload() { downloadJob?.cancel() }
    override fun deleteModel(provider: AiProvider) {
        if (downloadJob?.isActive == true || analysisJob?.isActive == true) return
        val model = provider.localModel ?: return
        stores[model]?.delete()
        File(cacheDirectory, model.name).deleteRecursively()
    }
    override fun onBackground() {
        if (settings.value.provider.localModel != null) analysisJob?.cancel()
        pauseDownload()
    }
}

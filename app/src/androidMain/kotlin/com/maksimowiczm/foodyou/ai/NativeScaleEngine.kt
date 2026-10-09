package com.maksimowiczm.foodyou.ai

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.LogSeverity
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import java.io.File
import kotlinx.coroutines.*

/** Native calls are owned exclusively by LocalAiService's worker, except the SDK cancellation API. */
internal interface NativeScaleEngine {
    suspend fun recognize(bytes: ByteArray, imageSize: String, timing: PhotoTiming): ScaleRecognitionResult
    fun cancel()
    fun close()
}

/** Tracks the terminal callback and cancellation call separately: both must finish before deletion. */
internal class NativeGeneration(private val cancellationScope: CoroutineScope, private val cancelNative: () -> Unit) {
    private val lock = Any()
    private var terminal = false
    private var cancelled = false
    private var cancelCall: Deferred<Unit>? = null
    private val completion = CompletableDeferred<Unit>()
    private val response = StringBuilder()
    private var failed = false
    private var overflow = false

    fun chunk(text: String) = synchronized(lock) {
        if (!terminal) {
            if (response.length + text.length <= 16_384) response.append(text) else overflow = true
        }
        Unit
    }

    fun finish(error: Boolean = false) = synchronized(lock) {
        if (!terminal) {
            terminal = true
            failed = error
            completion.complete(Unit)
        }
        Unit
    }

    fun cancel() = synchronized(lock) {
        if (!terminal && !cancelled) {
            cancelled = true
            // Deletion waits for this call even if the terminal callback arrives in the meantime.
            cancelCall = cancellationScope.async { cancelNative() }
        }
    }

    suspend fun awaitResponse(): NativeModelResponse {
        completion.await()
        val pendingCancel = synchronized(lock) { cancelCall }
        pendingCancel?.await()
        return synchronized(lock) {
            when {
                cancelled -> NativeModelResponse.Failure(ScaleRecognitionResult.Error("Analyse abgebrochen.", fatal = true))
                failed -> NativeModelResponse.Failure(ScaleRecognitionResult.Error("Gemma meldet einen Verarbeitungsfehler. Bitte den KI-Diagnosebericht prüfen.", fatal = true))
                else -> NativeModelResponse.Answer(response.toString(), truncated = overflow)
            }
        }
    }

    suspend fun awaitResult(): ScaleRecognitionResult = when (val answer = awaitResponse()) {
        is NativeModelResponse.Failure -> answer.error
        is NativeModelResponse.Answer -> parseScaleReading(answer.text, answer.truncated)
    }

    fun responseText(): String = synchronized(lock) { response.toString() }
}

internal class LiteRtScaleEngine private constructor(
    private val engine: Engine,
    private val modelName: String,
    private val visionBackend: String,
    private val cancellationScope: CoroutineScope,
    private val diagnostics: AiDiagnostics,
) : NativeScaleEngine {
    @Volatile private var generation: NativeGeneration? = null
    @Volatile private var cancellationRequested = false

    override suspend fun recognize(bytes: ByteArray, imageSize: String, timing: PhotoTiming): ScaleRecognitionResult {
        val result = analyzeLocalScalePhoto(
            photo = ScaleModelImage(bytes, imageSize),
            isCancelled = { cancellationRequested },
            crop = { photo, box ->
                cropScaleDisplay(photo, box).also { cropped ->
                    diagnostics.record("display_cropped")
                    diagnostics.recordImagePreparation(photo.imageSize, cropped.imageSize, box)
                }
            },
            generate = { photo, prompt, stage -> generate(photo, prompt, stage, timing) },
        )
        val outcome = when (result) {
            is ScaleRecognitionResult.Recognized -> "recognized"
            ScaleRecognitionResult.Unreadable -> "unreadable"
            is ScaleRecognitionResult.Error -> result.kind.name
        }
        diagnostics.record("result_$outcome")
        return result
    }

    private suspend fun generate(
        photo: ScaleModelImage,
        prompt: String,
        stage: String,
        timing: PhotoTiming,
    ): NativeModelResponse {
        currentCoroutineContext().ensureActive()
        if (cancellationRequested) return NativeModelResponse.Failure(
            ScaleRecognitionResult.Error("Analyse abgebrochen.", fatal = true),
        )
        val started = System.currentTimeMillis()
        diagnostics.record("${stage}_conversation_create")
        val conversation = engine.createConversation(ConversationConfig(
            maxOutputToken = 128,
            thinkingConfig = ThinkingConfig(enableThinking = false),
            samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0),
        ))
        val current = NativeGeneration(cancellationScope) { conversation.cancelProcess() }
        try {
            if (cancellationRequested) return NativeModelResponse.Failure(
                ScaleRecognitionResult.Error("Analyse abgebrochen.", fatal = true),
            )
            diagnostics.record("${stage}_generation_start")
            try {
                timing.generationStarted()
                conversation.sendMessageAsync(
                    Contents.of(Content.ImageBytes(photo.bytes), Content.Text(prompt)),
                    object : MessageCallback {
                        override fun onMessage(message: Message) {
                            val text = message.toString()
                            timing.responseReceived(text)
                            current.chunk(text)
                        }
                        override fun onDone() { current.finish() }
                        override fun onError(throwable: Throwable) { current.finish(error = true) }
                    },
                )
            } catch (_: Exception) { current.finish(error = true) }
            // Do not call cancelProcess before sendMessageAsync has started generation.
            generation = current
            if (cancellationRequested) current.cancel()
            val response = current.awaitResponse()
            val result = when (response) {
                is NativeModelResponse.Failure -> response.error
                is NativeModelResponse.Answer -> if (stage != "locate")
                    parseScaleReading(response.text, response.truncated) else null
            }
            if (stage != "locate" && result is ScaleRecognitionResult.Error && result.kind in setOf(
                    ScaleErrorKind.ResponseFormat,
                    ScaleErrorKind.NonWholeGrams,
                    ScaleErrorKind.Truncated,
                )
            ) {
                diagnostics.recordRejectedResponse(result.kind, current.responseText())
            }
            val outcome = when (result) {
                is ScaleRecognitionResult.Recognized -> "recognized"
                ScaleRecognitionResult.Unreadable -> "unreadable"
                is ScaleRecognitionResult.Error -> result.kind.name
                null -> if (response is NativeModelResponse.Answer && !response.truncated &&
                    parseScaleDisplayBox(response.text) != null) "localized" else "full_image_fallback"
            }
            diagnostics.record("${stage}_result_$outcome")
            // Every answer is kept (bounded) so an unreadable result can be explained from one report.
            diagnostics.recordResponse(outcome, modelName, photo.imageSize, visionBackend, current.responseText(), stage)
            diagnostics.recordNativeLog(started)
            return response
        } finally {
            generation = null
            diagnostics.record("${stage}_conversation_close")
            conversation.close()
        }
    }

    override fun cancel() { cancellationRequested = true; generation?.cancel() }
    override fun close() {
        diagnostics.record("engine_close")
        engine.close()
        diagnostics.record("engine_closed")
    }

    companion object {
        fun open(
            model: File,
            modelName: String,
            cache: File,
            expectedSize: Long,
            visionOnCpu: Boolean,
            cancellationScope: CoroutineScope,
            diagnostics: AiDiagnostics,
        ): LiteRtScaleEngine {
            check(model.isFile && model.length() == expectedSize)
            cache.mkdirs()
            // INFO includes the preprocessor's "Resize image ... patches" line for the report.
            runCatching { Engine.setNativeMinLogSeverity(LogSeverity.INFO) }
            val visionBackend = if (visionOnCpu) "cpu" else "gpu"
            diagnostics.record("engine_initializing_vision_$visionBackend")
            val engine = Engine(EngineConfig(
                modelPath = model.path, backend = Backend.GPU(),
                visionBackend = if (visionOnCpu) Backend.CPU() else Backend.GPU(),
                maxNumTokens = 4096, maxNumImages = 1, cacheDir = cache.path,
            ))
            try {
                engine.initialize()
                diagnostics.record("engine_ready")
                return LiteRtScaleEngine(engine, modelName, visionBackend, cancellationScope, diagnostics)
            } catch (failure: Throwable) {
                engine.close()
                throw failure
            }
        }
    }
}

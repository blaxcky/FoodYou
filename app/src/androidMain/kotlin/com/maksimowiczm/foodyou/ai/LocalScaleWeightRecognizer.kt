package com.maksimowiczm.foodyou.ai

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*

/** Main-thread-confined IPC client; no LiteRT classes or image decoding in the UI process. */
internal class LocalScaleWeightRecognizer private constructor(
    private val context: Context,
    private val photoDirectory: File,
    private val diagnostics: AiDiagnostics,
) : ScaleWeightRecognizer {
    private val session = UUID.randomUUID().toString()
    private val pending = PendingAiRequests()
    private val connected = CompletableDeferred<Boolean>()
    private val main = Handler(Looper.getMainLooper())
    private var remote: Messenger? = null
    private var bound = false
    private var closed = false
    private var disconnected = false
    private var initialFailure: ScaleRecognitionResult.Error? = null
    private val death = IBinder.DeathRecipient { main.post { lost() } }
    private val replies = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) {
            val data = message.data
            if (message.what == LocalAiProtocol.REPLY && data.getString(LocalAiProtocol.SESSION) == session)
                pending.complete(data.getInt(LocalAiProtocol.REQUEST), data)
        }
    })
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            if (closed || disconnected) return
            try {
                binder.linkToDeath(death, 0)
                remote = Messenger(binder)
                connected.complete(true)
            } catch (_: RemoteException) { lost() }
        }
        override fun onServiceDisconnected(name: ComponentName) { lost() }
        override fun onBindingDied(name: ComponentName) { lost() }
        override fun onNullBinding(name: ComponentName) { lost() }
    }

    private fun lost() {
        if (closed || disconnected) return
        disconnected = true
        diagnostics.record("worker_connection_lost")
        initialFailure = ScaleRecognitionResult.Error(
            "Der lokale KI-Prozess wurde beendet. Ursache unbekannt; bitte den Diagnosebericht unter Einstellungen → KI prüfen.",
            fatal = true, kind = ScaleErrorKind.ProcessDied,
        )
        connected.complete(false)
        pending.failAll()
        unbind() // Prevent Android from automatically restarting a crashed engine.
    }

    private fun unbind() {
        remote?.binder?.let { runCatching { it.unlinkToDeath(death, 0) } }
        remote = null
        if (bound) { bound = false; context.unbindService(connection) }
    }

    private suspend fun request(code: Int, data: Bundle = Bundle(), timeout: Long = 120_000): Bundle {
        val (id, answer) = pending.create()
        data.putInt(LocalAiProtocol.REQUEST, id)
        data.putString(LocalAiProtocol.SESSION, session)
        try {
            val target = remote
            if (target == null) { pending.failAll() }
            else try {
                target.send(Message.obtain(null, code).apply { this.data = data; replyTo = replies })
            } catch (_: RemoteException) { lost() }
            return withTimeout(timeout) { answer.await() }
        } catch (_: TimeoutCancellationException) {
            diagnostics.record("worker_request_timeout")
            return ScaleRecognitionResult.Error("Die lokale KI antwortet nicht. Die Analyse wurde beendet.",
                fatal = true, kind = ScaleErrorKind.Timeout).toIpcBundle()
        } finally { pending.remove(id) }
    }

    override suspend fun recognize(photoPath: String): ScaleRecognitionResult = withContext(Dispatchers.Main.immediate) {
        initialFailure?.let { return@withContext it }
        val file = File(photoDirectory, photoPath).canonicalFile
        if (file.parentFile != photoDirectory.canonicalFile || !file.isFile)
            return@withContext ScaleRecognitionResult.Error("Foto ist nicht mehr vorhanden.")
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            request(LocalAiProtocol.RECOGNIZE, Bundle().apply {
                putParcelable(LocalAiProtocol.PHOTO, descriptor)
            }).readRecognitionResult()
        }
    }

    override suspend fun close() = withContext(NonCancellable + Dispatchers.Main.immediate) {
        if (closed) return@withContext
        try {
            if (remote != null) {
                val response = request(LocalAiProtocol.CLOSE, timeout = 5_500)
                if (response.getString(LocalAiProtocol.RESULT) != "closed") {
                    remote?.send(Message.obtain(null, LocalAiProtocol.TERMINATE).apply {
                        data = Bundle().apply { putString(LocalAiProtocol.SESSION, session) }
                        replyTo = replies
                    })
                }
            }
        } catch (_: RemoteException) {
            // The worker has already exited; there is no native state in this process to close.
        } finally {
            closed = true
            pending.failAll()
            unbind()
        }
    }

    companion object {
        suspend fun open(context: Context, photoDirectory: File, diagnostics: AiDiagnostics): LocalScaleWeightRecognizer =
            openOnMain(context, photoDirectory, diagnostics)

        private suspend fun openOnMain(context: Context, photoDirectory: File, diagnostics: AiDiagnostics): LocalScaleWeightRecognizer {
            var created: LocalScaleWeightRecognizer? = null
            try {
                return withContext(Dispatchers.Main.immediate) {
                    val recognizer = LocalScaleWeightRecognizer(context.applicationContext, photoDirectory, diagnostics)
                    created = recognizer
                    recognizer.bound = recognizer.context.bindService(Intent(context, LocalAiService::class.java), recognizer.connection, Context.BIND_AUTO_CREATE)
                    if (!recognizer.bound || !withTimeout(15_000) { recognizer.connected.await() }) {
                        recognizer.lost()
                    } else {
                        val result = recognizer.request(LocalAiProtocol.OPEN)
                        if (result.getString(LocalAiProtocol.RESULT) != "ready")
                            recognizer.initialFailure = result.readRecognitionResult() as? ScaleRecognitionResult.Error
                    }
                    recognizer
                }
            } catch (error: Throwable) {
                // Also closes if cancellation wins the dispatch back to the calling context.
                created?.close()
                throw error
            }
        }
    }
}

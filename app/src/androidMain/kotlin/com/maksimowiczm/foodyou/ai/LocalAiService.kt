package com.maksimowiczm.foodyou.ai

import android.app.Application
import android.app.Service
import android.content.Intent
import android.os.*
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.*

/** Bound only while a user-requested batch runs. Never starts a foreground/background job. */
class LocalAiService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val cancellation = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + worker)
    private val cancellationScope = CoroutineScope(SupervisorJob() + cancellation)
    private lateinit var diagnostics: AiDiagnostics
    @Volatile private var engine: NativeScaleEngine? = null
    private var operation: Job? = null
    private var shutdown: Job? = null
    private var session: String? = null
    private var client: Messenger? = null
    @Volatile private var stopping = false
    private var clientDeath: IBinder.DeathRecipient? = null
    private val watchdog = WorkerShutdownWatchdog(main) { terminateWorker() }
    private val messenger by lazy { Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) { handle(message) }
    }) }

    override fun onCreate() {
        super.onCreate()
        check(Application.getProcessName() == packageName + LOCAL_AI_PROCESS_SUFFIX) {
            "LocalAiService must never run in the UI process"
        }
        diagnostics = AiDiagnostics(this, worker = true)
        diagnostics.record("service_created")
    }

    override fun onBind(intent: Intent): IBinder = messenger.binder

    private fun handle(message: Message) {
        if (message.sendingUid != Process.myUid()) return
        val data = message.data
        val token = data.getString(LocalAiProtocol.SESSION) ?: return
        val id = data.getInt(LocalAiProtocol.REQUEST)
        val reply = message.replyTo ?: return
        if (session == null) {
            if (message.what != LocalAiProtocol.OPEN) return
            session = token
            client = reply
            clientDeath = IBinder.DeathRecipient { main.post { stopSession(null, 0) } }.also {
                try { reply.binder.linkToDeath(it, 0) } catch (_: RemoteException) { stopSession(null, 0); return }
            }
        }
        if (token != session || reply.binder != client?.binder) return
        when (message.what) {
            LocalAiProtocol.TERMINATE -> terminateWorker()
            LocalAiProtocol.CLOSE -> stopSession(reply, id)
            LocalAiProtocol.OPEN -> {
                if (stopping || operation?.isActive == true || engine != null) {
                    send(reply, id, failure("Die KI-Sitzung ist bereits belegt.")); return
                }
                startOperation(reply, id) {
                    try {
                        engine = LiteRtScaleEngine.open(
                            File(noBackupFilesDir, "ai/models/$GEMMA_FILE"), File(cacheDir, "gemma"), cancellationScope, diagnostics,
                        )
                        Bundle().apply { putString(LocalAiProtocol.RESULT, "ready") }
                    } catch (_: Exception) {
                        diagnostics.record("initialization_failed")
                        failure("Gemma konnte nicht geladen werden. Bitte den KI-Diagnosebericht prüfen.")
                    } catch (_: LinkageError) {
                        diagnostics.record("native_library_failed")
                        failure("Die native KI-Bibliothek ist auf diesem Gerät nicht verfügbar.")
                    }
                }
            }
            LocalAiProtocol.RECOGNIZE -> {
                @Suppress("DEPRECATION")
                val photo = data.getParcelable<ParcelFileDescriptor>(LocalAiProtocol.PHOTO)
                if (photo == null) { send(reply, id, failure("Foto fehlt.")); return }
                if (stopping || operation?.isActive == true || engine == null) {
                    photo.close()
                    send(reply, id, failure("Die KI-Sitzung ist nicht bereit.")); return
                }
                startOperation(reply, id) {
                    try {
                        val bytes = ParcelFileDescriptor.AutoCloseInputStream(photo).use {
                            diagnostics.record("photo_decode")
                            decodeScalePhoto(it) { width, height -> diagnostics.record("photo_decoded", width, height) }
                        }
                        if (stopping) failure("Analyse abgebrochen.")
                        else requireNotNull(engine).recognize(bytes).toIpcBundle()
                    } catch (_: Exception) {
                        diagnostics.record("recognition_failed")
                        failure("Lokale Analyse fehlgeschlagen. Bitte den KI-Diagnosebericht prüfen.")
                    }
                }
            }
        }
    }

    private fun startOperation(reply: Messenger, id: Int, block: suspend () -> Bundle) {
        var result: Bundle? = null
        val job = scope.launch(start = CoroutineStart.LAZY) { result = block() }
        operation = job
        job.invokeOnCompletion { main.post { result?.let { send(reply, id, it) } } }
        job.start()
    }

    /** Called on the service main thread, which never executes native code. */
    private fun stopSession(reply: Messenger?, id: Int) {
        if (stopping) return
        stopping = true
        // Arm before requesting cancellation: even cancelProcess() itself can hang in native code.
        watchdog.arm()
        diagnostics.record("shutdown_requested")
        engine?.cancel()
        var cleaned = false
        shutdown = scope.launch {
            try {
                operation?.join()
                engine?.close()
                engine = null
                diagnostics.record("session_closed")
                if (reply != null) send(reply, id, Bundle().apply { putString(LocalAiProtocol.RESULT, "closed") })
                cleaned = true
            } catch (_: Exception) {
                // Keep the watchdog armed if cleanup failed; never reuse this engine.
                diagnostics.record("shutdown_failed")
            }
        }
        shutdown?.invokeOnCompletion {
            if (cleaned) main.post {
                watchdog.disarm()
                scope.cancel()
                cancellationScope.cancel()
                worker.close()
                cancellation.close()
                stopSelf()
            }
        }
    }

    private fun send(target: Messenger, id: Int, data: Bundle) {
        data.putInt(LocalAiProtocol.REQUEST, id)
        data.putString(LocalAiProtocol.SESSION, session)
        try { target.send(Message.obtain(null, LocalAiProtocol.REPLY).apply { this.data = data }) }
        catch (_: RemoteException) { main.post { stopSession(null, 0) } }
    }

    private fun failure(message: String) = ScaleRecognitionResult.Error(message, fatal = true).toIpcBundle()

    private fun terminateWorker() {
        // Never kill a PID supplied by a caller; verify and terminate only this worker process.
        if (Application.getProcessName() != packageName + LOCAL_AI_PROCESS_SUFFIX) return
        diagnostics.record("worker_termination_requested")
        Process.killProcess(Process.myPid())
    }

    override fun onUnbind(intent: Intent): Boolean {
        stopSession(null, 0)
        return false
    }

    override fun onDestroy() {
        clientDeath?.let { runCatching { client?.binder?.unlinkToDeath(it, 0) } }
        stopSession(null, 0)
        super.onDestroy()
    }
}

package com.maksimowiczm.foodyou.ai

import android.app.Application
import android.content.ComponentName
import android.os.*
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class LocalAiClientTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val component = ComponentName(app, LocalAiService::class.java)
    private val messages = mutableListOf<Message>()
    private val looper get() = shadowOf(Looper.getMainLooper())
    private lateinit var directory: File

    @Before fun setup() {
        directory = kotlin.io.path.createTempDirectory("ipc-photos").toFile()
        File(directory, "scale.jpg").writeText("descriptor test")
        val service = Messenger(object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(message: Message) {
                val copy = Message.obtain(message)
                messages += copy
                when (message.what) {
                    LocalAiProtocol.OPEN -> reply(copy, Bundle().apply { putString("result", "ready") })
                    LocalAiProtocol.CLOSE -> reply(copy, Bundle().apply { putString("result", "closed") })
                }
            }
        })
        shadowOf(app).setComponentNameAndServiceForBindService(component, service.binder)
    }

    @After fun cleanup() { Dispatchers.resetMain(); directory.deleteRecursively() }

    private fun reply(request: Message, result: Bundle) {
        result.putInt(LocalAiProtocol.REQUEST, request.data.getInt(LocalAiProtocol.REQUEST))
        result.putString(LocalAiProtocol.SESSION, request.data.getString(LocalAiProtocol.SESSION))
        request.replyTo.send(Message.obtain(null, LocalAiProtocol.REPLY).apply { data = result })
    }

    @Test fun processLossStopsClientAndUnbindsWithoutRestart() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val opened = async { LocalScaleWeightRecognizer.open(app, directory, AiDiagnostics(app)) }
        runCurrent(); looper.idle()
        val recognizer = opened.await()
        val result = async { recognizer.recognize("scale.jpg") }
        runCurrent(); looper.idle()
        val connection = shadowOf(app).boundServiceConnections.single()
        connection.onServiceDisconnected(component)
        assertEquals(ScaleErrorKind.ProcessDied, assertIs<ScaleRecognitionResult.Error>(result.await()).kind)
        val photoRequest = messages.last { it.what == LocalAiProtocol.RECOGNIZE }
        reply(photoRequest, ScaleRecognitionResult.Recognized(999.0).toIpcBundle())
        looper.idle()
        assertEquals(ScaleErrorKind.ProcessDied, assertIs<ScaleRecognitionResult.Error>(recognizer.recognize("scale.jpg")).kind)
        recognizer.close()
        assertEquals(1, messages.count { it.what == LocalAiProtocol.OPEN })
        assertTrue(shadowOf(app).boundServiceConnections.isEmpty())
    }

    @Test fun requestTimeoutClosesService() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val opened = async { LocalScaleWeightRecognizer.open(app, directory, AiDiagnostics(app)) }
        runCurrent(); looper.idle()
        val recognizer = opened.await()
        val result = async { recognizer.recognize("scale.jpg") }
        runCurrent(); looper.idle()
        advanceTimeBy(120_001); runCurrent()
        assertEquals(ScaleErrorKind.Timeout, assertIs<ScaleRecognitionResult.Error>(result.await()).kind)
        val closing = async { recognizer.close() }
        runCurrent(); looper.idle()
        closing.await()
        assertEquals(1, messages.count { it.what == LocalAiProtocol.CLOSE })
        assertTrue(shadowOf(app).boundServiceConnections.isEmpty())
    }
    @Test fun cancellingInferenceWaitsForCloseAndDropsLateResponse() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val opened = async { LocalScaleWeightRecognizer.open(app, directory, AiDiagnostics(app)) }
        runCurrent(); looper.idle()
        val recognizer = opened.await()
        var result: ScaleRecognitionResult? = null
        val analysis = launch {
            try { result = recognizer.recognize("scale.jpg") }
            finally { recognizer.close() }
        }
        runCurrent(); looper.idle()
        val photo = messages.last { it.what == LocalAiProtocol.RECOGNIZE }
        analysis.cancel()
        runCurrent(); looper.idle()
        analysis.join()
        reply(photo, ScaleRecognitionResult.Recognized(999.0).toIpcBundle())
        looper.idle()
        assertNull(result)
        assertEquals(1, messages.count { it.what == LocalAiProtocol.CLOSE })
        assertTrue(shadowOf(app).boundServiceConnections.isEmpty())
    }

}

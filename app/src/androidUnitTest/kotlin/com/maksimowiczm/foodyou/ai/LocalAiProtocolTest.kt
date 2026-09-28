package com.maksimowiczm.foodyou.ai

import android.app.Application
import android.app.ApplicationExitInfo
import android.content.ComponentName
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.*
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class LocalAiProtocolTest {
    @Test fun processDeathFailsPendingRequestsAndLateRepliesCannotCompleteNewRequests() = runTest {
        val requests = PendingAiRequests()
        val (first, a) = requests.create()
        requests.failAll()
        assertEquals(ScaleErrorKind.ProcessDied, assertIs<ScaleRecognitionResult.Error>(a.await().readRecognitionResult()).kind)
        val (second, b) = requests.create()
        requests.complete(first, ScaleRecognitionResult.Recognized(999.0).toIpcBundle())
        assertFalse(b.isCompleted)
        requests.complete(second, ScaleRecognitionResult.Recognized(269.0).toIpcBundle())
        assertEquals(ScaleRecognitionResult.Recognized(269.0), b.await().readRecognitionResult())
    }

    @Test fun timedOutOrCancelledRequestDoesNotAcceptLateResponse() {
        val requests = PendingAiRequests()
        val (id, answer) = requests.create()
        requests.remove(id)
        requests.complete(id, Bundle())
        assertFalse(answer.isCompleted)
    }

    @Test fun serviceIsPrivateAndInSeparateProcess() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val info = context.packageManager.getServiceInfo(ComponentName(context, LocalAiService::class.java), 0)
        assertFalse(info.exported)
        assertEquals(context.packageName + ":local_ai", info.processName)
    }

    @Test fun shutdownWatchdogFiresAfterFiveSecondsAndSuccessfulShutdownDisarmsIt() {
        var killed = 0
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val watchdog = WorkerShutdownWatchdog(handler) { killed++ }
        val looper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
        watchdog.arm()
        looper.idleFor(java.time.Duration.ofMillis(4999))
        assertEquals(0, killed)
        looper.idleFor(java.time.Duration.ofMillis(1))
        assertEquals(1, killed)
        watchdog.arm()
        watchdog.disarm()
        looper.idleFor(java.time.Duration.ofSeconds(10))
        assertEquals(1, killed)
    }

    @Test fun diagnosticReportRetainsTimingInSecondsAcrossInstances() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val worker = AiDiagnostics(context, worker = true)
        worker.recordTiming(AiTimingRecord(100, null, 4200, null, AiTimingOutcome.Completed))
        worker.recordTiming(AiTimingRecord(100, 1, 20000, 14500, AiTimingOutcome.Completed))
        worker.recordTiming(AiTimingRecord(100, 2, 5000, null, AiTimingOutcome.Cancelled))
        val report = AiDiagnostics(context).report()
        assertTrue(report.contains("Modellladen=4.200 s"))
        assertTrue(report.contains("Foto 1: Gesamt=20.000 s; Erste Antwort=14.500 s"))
        assertTrue(report.contains("Foto 2: Gesamt=5.000 s; Erste Antwort=nicht empfangen; Status=Cancelled"))
    }

    @Test fun diagnosticReportRetainsExactRejectedResponseWithPrivacyWarning() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()
        AiDiagnostics(context, worker = true).recordRejectedResponse(
            ScaleErrorKind.ResponseFormat,
            "The weight is 269 g\nnot JSON",
        )

        val report = AiDiagnostics(context).report()

        assertTrue(report.contains("kind=ResponseFormat"))
        assertTrue(report.contains("Antwort (JSON-kodiert): \"The weight is 269 g\\nnot JSON\""))
        assertTrue(report.contains("können erkannten Bildtext enthalten"))
    }

    @Test fun ambiguousSignalDoesNotClaimMemoryExhaustion() {
        assertTrue(exitReasonLabel(ApplicationExitInfo.REASON_LOW_MEMORY).contains("Speichermangel"))
        assertEquals("Nativer Absturz", exitReasonLabel(ApplicationExitInfo.REASON_CRASH_NATIVE))
        assertTrue(exitReasonLabel(ApplicationExitInfo.REASON_SIGNALED).contains("unbekannt"))
        assertTrue(exitReasonLabel(-1).contains("unbekannt"))
    }
}

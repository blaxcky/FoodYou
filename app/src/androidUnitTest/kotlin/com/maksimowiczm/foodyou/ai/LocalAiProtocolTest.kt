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

    @Test fun diagnosticReportKeepsEveryAnswerWithImageSizeAndVisionBackend() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val worker = AiDiagnostics(context, worker = true)
        worker.recordResponse("unreadable", "E2B", "960x1280", "gpu", "{\"value\":null}")
        worker.recordResponse("recognized", "E4B", "960x1280", "cpu", "{\"value\":117,\"unit\":\"g\"}")
        worker.recordResponse("localized", "E4B", "542x723", "gpu",
            "{\"value\":19,\"box_2d\":[465,617,563,762]}", stage = "locate")
        worker.recordImagePreparation("542x723", "818x733", ScaleDisplayBox(465,617,563,762))
        worker.recordResponse("recognized", "E4B", "818x733", "gpu", "{\"value\":79,\"unit\":\"g\"}")

        val report = AiDiagnostics(context).report()

        assertTrue(report.contains("result=unreadable model=E2B image=960x1280 vision=gpu stage=read raw=\"{\\\"value\\\":null}\""))
        assertTrue(report.contains("result=recognized model=E4B image=960x1280 vision=cpu"))
        assertTrue(report.contains("result=localized model=E4B image=542x723 vision=gpu stage=locate"))
        assertTrue(report.contains("result=recognized model=E4B image=818x733 vision=gpu stage=read"))
        assertTrue(report.contains("original=542x723 crop=818x733 box_2d=[465,617,563,762] padding=0.35"))
        assertTrue(report.contains("Native LiteRT-Meldungen"))
    }

    @Test fun nativeLogKeepsLiteRtLinesAndWorkerWarningsOnly() {
        val lines = listOf(
            "1759525000.100  4242  4300 I litert  : stb_image_preprocessor.cc:88] Resize image from 960x1280 to 672x912 which will result in 2394 patches",
            "1759525000.200  4242  4300 D Choreographer: unrelated debug line",
            "1759525000.300  4242  4300 W System  : unrelated warning",
            "1759525000.400  4242  4301 E tflite  : OpenCL delegate failed",
            "         1759525000.500  4242  4301 W System  : right-aligned epoch timestamp",
            "--------- beginning of main",
        )

        val selected = selectNativeLogLines(lines)

        assertEquals(listOf(lines[0], lines[2], lines[3], lines[4].trim()), selected)
        assertEquals(listOf(lines[4].trim()), selectNativeLogLines(lines, limit = 1))
    }

    @Test fun ambiguousSignalDoesNotClaimMemoryExhaustion() {
        assertTrue(exitReasonLabel(ApplicationExitInfo.REASON_LOW_MEMORY).contains("Speichermangel"))
        assertEquals("Nativer Absturz", exitReasonLabel(ApplicationExitInfo.REASON_CRASH_NATIVE))
        assertTrue(exitReasonLabel(ApplicationExitInfo.REASON_SIGNALED).contains("unbekannt"))
        assertTrue(exitReasonLabel(-1).contains("unbekannt"))
    }
}

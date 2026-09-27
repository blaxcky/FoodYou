package com.maksimowiczm.foodyou.ai

import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Test

class NativeGenerationTest {
    @Test fun successfulGenerationDoesNotCancelAndIgnoresLateCallbacks() = runTest {
        val generation = NativeGeneration(backgroundScope) { error("Must not cancel completed generation") }
        generation.chunk("""{"readable":true,"value":269,"unit":"g"}""")
        generation.finish()
        generation.cancel()
        generation.chunk("late text")
        generation.finish(error = true)
        assertEquals(ScaleRecognitionResult.Recognized(269.0), generation.awaitResult())
    }

    @Test fun cancellationWaitsForTerminalCallback() = runTest {
        var calls = 0
        // A separate dispatcher is unnecessary here: this fake native call is controlled explicitly.
        val nativeScope = CoroutineScope(coroutineContext + SupervisorJob())
        val generation = NativeGeneration(nativeScope) { calls++ }
        val answer = async { generation.awaitResult() }
        generation.cancel()
        generation.cancel()
        runCurrent()
        assertEquals(1, calls)
        assertFalse(answer.isCompleted)
        advanceTimeBy(5_000)
        assertFalse(answer.isCompleted) // A timeout must kill the worker, never delete an active session.
        generation.finish()
        assertTrue(assertIs<ScaleRecognitionResult.Error>(answer.await()).fatal)
        nativeScope.cancel()
    }

    @Test fun terminalCallbackCannotCloseSessionWhileNativeCancellationIsStillRunning() = runBlocking {
        val started = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val dispatcher = java.util.concurrent.Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            val generation = NativeGeneration(scope) {
                started.countDown()
                check(release.await(5, java.util.concurrent.TimeUnit.SECONDS))
            }
            generation.cancel()
            assertTrue(started.await(5, java.util.concurrent.TimeUnit.SECONDS))
            generation.finish()
            val result = async(start = CoroutineStart.UNDISPATCHED) { generation.awaitResult() }
            assertFalse(result.isCompleted)
            release.countDown()
            assertTrue(assertIs<ScaleRecognitionResult.Error>(withTimeout(5_000) { result.await() }).fatal)
        } finally {
            release.countDown()
            scope.cancel()
            dispatcher.close()
        }
    }

    @Test fun nativeErrorIsNotReportedAsUnreadable() = runTest {
        val generation = NativeGeneration(backgroundScope) {}
        generation.finish(error = true)
        assertTrue(assertIs<ScaleRecognitionResult.Error>(generation.awaitResult()).fatal)
    }
}

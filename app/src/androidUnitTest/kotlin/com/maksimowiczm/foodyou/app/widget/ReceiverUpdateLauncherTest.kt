package com.maksimowiczm.foodyou.app.widget

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

class ReceiverUpdateLauncherTest {

    @Test
    fun finishesAfterSuccessfulUpdate() = runTest {
        var updated = false
        var finished = false
        val launcher = ReceiverUpdateLauncher(CoroutineScope(StandardTestDispatcher(testScheduler)))

        launcher.launch(finish = { finished = true }) { updated = true }
        advanceUntilIdle()

        assertTrue(updated)
        assertTrue(finished)
    }

    @Test
    fun finishesAfterFailedUpdate() = runTest {
        var finished = false
        val errors = mutableListOf<Throwable>()
        val scope =
            CoroutineScope(
                SupervisorJob() +
                    StandardTestDispatcher(testScheduler) +
                    CoroutineExceptionHandler { _, throwable -> errors += throwable }
            )
        val launcher = ReceiverUpdateLauncher(scope)

        launcher.launch(finish = { finished = true }) { error("Boom") }
        advanceUntilIdle()

        assertTrue(finished)
        assertEquals(1, errors.size)
    }
}

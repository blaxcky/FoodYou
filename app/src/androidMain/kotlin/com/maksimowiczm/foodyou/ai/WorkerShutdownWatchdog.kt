package com.maksimowiczm.foodyou.ai

import android.os.Handler

/** The handler runs outside the native executor, including while cancellation itself is stuck. */
internal class WorkerShutdownWatchdog(private val handler: Handler, terminate: () -> Unit) {
    private val timeout = Runnable(terminate)
    fun arm() { handler.removeCallbacks(timeout); handler.postDelayed(timeout, 5_000) }
    fun disarm() { handler.removeCallbacks(timeout) }
}

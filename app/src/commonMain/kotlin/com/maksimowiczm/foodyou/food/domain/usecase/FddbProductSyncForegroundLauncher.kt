package com.maksimowiczm.foodyou.food.domain.usecase

import com.maksimowiczm.foodyou.common.log.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class FddbProductSyncForegroundLauncher(
    private val applicationScope: CoroutineScope,
    private val coordinator: FddbProductSyncCoordinator,
    private val logger: Logger,
) {
    private var job: Job? = null

    fun onForeground() {
        if (job?.isActive == true) return

        job =
            applicationScope.launch {
                try {
                    coordinator.syncDueIfAllowed()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (throwable: Throwable) {
                    logger.e(
                        tag = TAG,
                        throwable = throwable,
                        message = { "Automatic FDDB product sync failed." },
                    )
                }
            }
    }

    private companion object {
        const val TAG = "FddbProductSyncForegroundLauncher"
    }
}

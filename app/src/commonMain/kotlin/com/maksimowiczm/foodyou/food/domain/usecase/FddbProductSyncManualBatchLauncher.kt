package com.maksimowiczm.foodyou.food.domain.usecase

import com.maksimowiczm.foodyou.common.log.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FddbProductSyncManualBatchLauncher(
    private val applicationScope: CoroutineScope,
    private val coordinator: FddbProductSyncCoordinator,
    private val logger: Logger,
) {
    private val mutableState = MutableStateFlow<FddbProductSyncManualBatchState>(
        FddbProductSyncManualBatchState.Idle
    )
    val state: StateFlow<FddbProductSyncManualBatchState> = mutableState.asStateFlow()

    private var job: Job? = null

    fun start(limit: Int) {
        if (limit <= 0 || job?.isActive == true) return

        job =
            applicationScope.launch {
                try {
                    var latestProgress =
                        FddbProductSyncBatchProgress(limit, 0, 0, 0, false)
                    mutableState.value =
                        FddbProductSyncManualBatchState.Running(latestProgress)
                    val result =
                        coordinator.syncNext(limit) { progress ->
                            latestProgress = progress
                            mutableState.value = FddbProductSyncManualBatchState.Running(progress)
                        }
                    val progress =
                        when (result) {
                            is ManualFddbProductSyncResult.Completed ->
                                latestProgress
                            ManualFddbProductSyncResult.NoProducts ->
                                FddbProductSyncBatchProgress(0, 0, 0, 0, false)
                            ManualFddbProductSyncResult.NotEnabled,
                            is ManualFddbProductSyncResult.Waiting,
                            -> error("A manual batch cannot be gated by sync settings.")
                        }
                    mutableState.value = FddbProductSyncManualBatchState.Completed(progress)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (throwable: Throwable) {
                    logger.e(
                        tag = TAG,
                        throwable = throwable,
                        message = { "Manual FDDB product batch failed." },
                    )
                    mutableState.value =
                        FddbProductSyncManualBatchState.Failed(
                            throwable.message ?: throwable::class.simpleName.orEmpty()
                        )
                }
            }
    }

    fun clearResult() {
        if (job?.isActive != true) mutableState.value = FddbProductSyncManualBatchState.Idle
    }

    private companion object {
        const val TAG = "FddbProductSyncManualBatchLauncher"
    }
}

sealed interface FddbProductSyncManualBatchState {
    data object Idle : FddbProductSyncManualBatchState

    data class Running(val progress: FddbProductSyncBatchProgress) :
        FddbProductSyncManualBatchState

    data class Completed(val progress: FddbProductSyncBatchProgress) :
        FddbProductSyncManualBatchState

    data class Failed(val detail: String) : FddbProductSyncManualBatchState
}

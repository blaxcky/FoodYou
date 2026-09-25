package com.maksimowiczm.foodyou.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.food.domain.entity.FddbProductSyncQueueItem
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.repository.FddbProductSyncStatusRepository
import com.maksimowiczm.foodyou.food.domain.usecase.ResyncFddbProductError
import com.maksimowiczm.foodyou.food.domain.usecase.SyncFddbProductUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.UnlinkFddbProductError
import com.maksimowiczm.foodyou.food.domain.usecase.UnlinkFddbProductUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.UpdateFddbProductLinkError
import com.maksimowiczm.foodyou.food.domain.usecase.UpdateFddbProductLinkUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.toStatusMessage
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class FddbProductSyncQueueViewModel(
    statusRepository: FddbProductSyncStatusRepository,
    settingsRepository: UserPreferencesRepository<Settings>,
    private val syncFddbProductUseCase: SyncFddbProductUseCase,
    private val updateFddbProductLinkUseCase: UpdateFddbProductLinkUseCase,
    private val unlinkFddbProductUseCase: UnlinkFddbProductUseCase,
) : ViewModel() {
    private val mutableActionState = MutableStateFlow(FddbProductSyncActionState())
    val actionState: StateFlow<FddbProductSyncActionState> = mutableActionState.asStateFlow()

    val model: StateFlow<FddbProductSyncQueueModel> =
        combine(statusRepository.observeQueue(), settingsRepository.observe()) { queue, settings ->
                FddbProductSyncQueueModel(
                    progress = settings.fddbProductSyncManualCount.coerceIn(0, 2),
                    queue = queue,
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(2_000),
                initialValue = FddbProductSyncQueueModel(progress = 0, queue = emptyList()),
            )

    fun retry(productId: FoodId.Product) {
        launchAction(productId) {
            syncFddbProductUseCase.sync(productId).syncErrorOrNull()
        }
    }

    fun updateLink(productId: FoodId.Product, input: String) {
        launchAction(productId) {
            when (val update = updateFddbProductLinkUseCase.update(productId, input)) {
                is Result.Error -> update.error.toUiError()
                is Result.Success ->
                    syncFddbProductUseCase.sync(productId).syncErrorOrNull()
            }
        }
    }

    fun unlink(productId: FoodId.Product) {
        launchAction(productId) {
            when (val result = unlinkFddbProductUseCase.unlink(productId)) {
                is Result.Success -> {
                    null
                }
                is Result.Error -> result.error.toUiError()
            }
        }
    }

    fun clearActionError() {
        mutableActionState.value = mutableActionState.value.copy(error = null)
    }

    private fun launchAction(
        productId: FoodId.Product,
        action: suspend () -> FddbProductSyncActionError?,
    ) {
        if (mutableActionState.value.inProgress) return
        viewModelScope.launch {
            mutableActionState.value =
                FddbProductSyncActionState(productId = productId, inProgress = true)
            val error = action()
            mutableActionState.value =
                FddbProductSyncActionState(productId = productId, error = error)
        }
    }
}

internal data class FddbProductSyncQueueModel(
    val progress: Int,
    val queue: List<FddbProductSyncQueueItem>,
)

internal data class FddbProductSyncActionState(
    val productId: FoodId.Product? = null,
    val inProgress: Boolean = false,
    val error: FddbProductSyncActionError? = null,
)

internal sealed interface FddbProductSyncActionError {
    data object InvalidUrl : FddbProductSyncActionError

    data object AlreadyLinked : FddbProductSyncActionError

    data object ProductUnavailable : FddbProductSyncActionError

    data class SyncFailed(val detail: String) : FddbProductSyncActionError
}

private fun Result<Unit, ResyncFddbProductError>.syncErrorOrNull(): FddbProductSyncActionError? =
    when (this) {
        is Result.Success -> null
        is Result.Error -> FddbProductSyncActionError.SyncFailed(error.toStatusMessage())
    }

private fun UpdateFddbProductLinkError.toUiError(): FddbProductSyncActionError =
    when (this) {
        UpdateFddbProductLinkError.InvalidUrl -> FddbProductSyncActionError.InvalidUrl
        is UpdateFddbProductLinkError.AlreadyLinked -> FddbProductSyncActionError.AlreadyLinked
        is UpdateFddbProductLinkError.ProductNotFound,
        UpdateFddbProductLinkError.NotFddbProduct ->
            FddbProductSyncActionError.ProductUnavailable
    }

private fun UnlinkFddbProductError.toUiError(): FddbProductSyncActionError =
    FddbProductSyncActionError.ProductUnavailable

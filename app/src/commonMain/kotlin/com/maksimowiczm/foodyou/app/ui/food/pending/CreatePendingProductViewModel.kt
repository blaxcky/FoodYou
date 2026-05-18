package com.maksimowiczm.foodyou.app.ui.food.pending

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.food.domain.usecase.CreatePendingProductResult
import com.maksimowiczm.foodyou.food.domain.usecase.CreatePendingProductUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

internal sealed interface CreatePendingProductEvent {
    data class PendingProductReady(val id: Long) : CreatePendingProductEvent

    data class ProductExists(val id: Long) : CreatePendingProductEvent
}

internal class CreatePendingProductViewModel(
    private val createPendingProductUseCase: CreatePendingProductUseCase
) : ViewModel() {
    private val eventBus = Channel<CreatePendingProductEvent>()
    val events = eventBus.receiveAsFlow()

    fun create(barcode: String?, photoPath: String) {
        viewModelScope.launch {
            when (val result = createPendingProductUseCase.create(barcode, photoPath)) {
                is CreatePendingProductResult.Created ->
                    eventBus.send(CreatePendingProductEvent.PendingProductReady(result.pendingProductId))

                is CreatePendingProductResult.ExistingPendingProduct ->
                    eventBus.send(CreatePendingProductEvent.PendingProductReady(result.pendingProductId))

                is CreatePendingProductResult.ExistingProduct ->
                    eventBus.send(CreatePendingProductEvent.ProductExists(result.productId.id))
            }
        }
    }
}

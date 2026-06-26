package com.maksimowiczm.foodyou.app.ui.food.product.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.app.ui.food.product.ProductFormState
import com.maksimowiczm.foodyou.app.ui.food.product.nutritionFacts
import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.result.onError
import com.maksimowiczm.foodyou.common.result.onSuccess
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.Product
import com.maksimowiczm.foodyou.food.domain.entity.ProductPortion
import com.maksimowiczm.foodyou.food.domain.usecase.ObserveFoodUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.ResyncFddbProductError
import com.maksimowiczm.foodyou.food.domain.usecase.ResyncFddbProductUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.SetProductFavoriteUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.UpdateProductUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class UpdateProductViewModel(
    observeFoodUseCase: ObserveFoodUseCase,
    private val updateProductUseCase: UpdateProductUseCase,
    private val resyncFddbProductUseCase: ResyncFddbProductUseCase,
    private val setProductFavoriteUseCase: SetProductFavoriteUseCase,
    private val productId: FoodId.Product,
) : ViewModel() {

    val product =
        observeFoodUseCase
            .observe(productId)
            .mapNotNull { it as? Product }
            .stateIn(scope = viewModelScope, initialValue = null, started = WhileSubscribed(2_000))

    private val eventBus = Channel<UpdateProductEvent>()
    val events = eventBus.receiveAsFlow()

    private val _isResyncing = MutableStateFlow(false)
    val isResyncing: StateFlow<Boolean> = _isResyncing.asStateFlow()

    fun updateProduct(form: ProductFormState) {
        if (!form.isValid) {
            return
        }

        val multiplier =
            when (form.measurement) {
                is Measurement.ImmutableMeasurement -> 1f
                is Measurement.Package -> form.packageWeight.value?.let { 1 / it * 100 }
                is Measurement.Serving -> form.servingWeight.value?.let { 1 / it * 100 }
            }

        if (multiplier == null) {
            return
        }

        viewModelScope.launch {
            updateProductUseCase
                .update(
                    id = productId,
                    name = form.name.value,
                    brand = form.brand.value,
                    barcode = form.barcode.value,
                    nutritionFacts = form.nutritionFacts(multiplier),
                    packageWeight = form.packageWeight.value?.toDouble(),
                    servingWeight = form.servingWeight.value?.toDouble(),
                    note = form.note.value,
                    source = FoodSource(type = form.sourceType, url = form.sourceUrl.value),
                    isLiquid = form.isLiquid,
                    portions =
                        form.portions.map {
                            it.copy(
                                unit =
                                    if (form.isLiquid) {
                                        ProductPortion.Unit.Milliliter
                                    } else {
                                        ProductPortion.Unit.Gram
                                    }
                            )
                        },
                )
                .onSuccess { eventBus.send(UpdateProductEvent.Updated) }
                .onError {
                    // Explode
                    error("Failed to update product: $it")
                }
        }
    }

    fun resyncFddbProduct() {
        if (_isResyncing.value) {
            return
        }

        viewModelScope.launch {
            _isResyncing.value = true
            try {
                resyncFddbProductUseCase
                    .resync(productId)
                    .onSuccess { eventBus.send(UpdateProductEvent.Resynced) }
                    .onError { eventBus.send(UpdateProductEvent.ResyncFailed(it.toUiError())) }
            } finally {
                _isResyncing.value = false
            }
        }
    }

    fun setFavorite(isFavorite: Boolean) {
        viewModelScope.launch { setProductFavoriteUseCase.setFavorite(productId, isFavorite) }
    }
}

private fun ResyncFddbProductError.toUiError(): ResyncFddbProductUiError =
    when (this) {
        is ResyncFddbProductError.ProductNotFound -> ResyncFddbProductUiError.ProductNotFound
        ResyncFddbProductError.NotFddbProduct -> ResyncFddbProductUiError.NotFddbProduct
        ResyncFddbProductError.MissingSourceUrl -> ResyncFddbProductUiError.MissingSourceUrl
        ResyncFddbProductError.Blocked -> ResyncFddbProductUiError.Blocked
        ResyncFddbProductError.NetworkOrParseFailed ->
            ResyncFddbProductUiError.NetworkOrParseFailed
    }
